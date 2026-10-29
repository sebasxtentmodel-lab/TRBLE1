package com.tuapp.traductor;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.inputmethodservice.InputMethodService;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import org.json.JSONObject;
import org.vosk.Model;
import org.vosk.Recognizer;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Teclado de zona única, sin letras/botones; funciona sin red tras la compilación con modelos. */
public class TurcoTranslatorKeyboard extends InputMethodService {
    private static final int HZ = 16000;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final StringBuilder historial = new StringBuilder();
    private final ExecutorService translationThread = Executors.newSingleThreadExecutor();
    private volatile boolean listening;
    private volatile boolean alive = true;
    private volatile int epoch;
    private volatile AudioRecord recorder;
    private Thread microphoneThread;
    private OfflineMarianTranslator localTranslator;
    private ScrollView scroll;
    private TextView visor;
    private String status = "Abre la aplicación una vez para permitir el micrófono.";

    @Override public View onCreateInputView() {
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(true);
        scroll.setBackgroundColor(Color.BLACK);
        scroll.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, px(260)));
        visor = new TextView(this);
        visor.setTextColor(Color.WHITE);
        visor.setBackgroundColor(Color.BLACK);
        visor.setTextSize(21);
        visor.setGravity(Gravity.TOP | Gravity.START);
        visor.setPadding(px(18),px(16),px(18),px(16));
        visor.setMinHeight(px(260));
        visor.setTextIsSelectable(false);
        scroll.addView(visor, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        View.OnLongClickListener picker = v -> {
            InputMethodManager manager = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (manager != null) manager.showInputMethodPicker();
            return true;
        };
        visor.setOnLongClickListener(picker);
        scroll.setOnLongClickListener(picker);
        visor.setLongClickable(true);
        scroll.setLongClickable(true);
        updateView();
        return scroll;
    }

    @Override public void onStartInputView(EditorInfo info, boolean restarting) {
        super.onStartInputView(info,restarting);
        if (!alive) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            announce("Permiso de micrófono pendiente. Abre la app desde el escritorio.");
            return;
        }
        if (isPasswordField(info)) {
            stopCapture();
            announce("Micrófono desactivado en campos de contraseña.");
            return;
        }
        startCapture();
    }

    private boolean isPasswordField(EditorInfo info) {
        if (info == null) return false;
        int type = info.inputType;
        int clazz = type & InputType.TYPE_MASK_CLASS;
        int variation = type & InputType.TYPE_MASK_VARIATION;
        if (clazz == InputType.TYPE_CLASS_TEXT) return variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
        return clazz == InputType.TYPE_CLASS_NUMBER && variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD;
    }

    private synchronized void startCapture() {
        if (listening || !alive) return;
        // No abrir un segundo AudioRecord antes de liberar el anterior.
        if (microphoneThread != null && microphoneThread.isAlive()) {
            ui.postDelayed(() -> { if (!listening && alive) startCapture(); }, 350L);
            return;
        }
        final int session = ++epoch;
        listening = true;
        announce("Preparando modelos locales…");
        microphoneThread = new Thread(() -> processMicrophone(session), "microfono-vosk-16000");
        microphoneThread.start();
        translationThread.execute(() -> {
            try {
                if (localTranslator == null) localTranslator = new OfflineMarianTranslator(getApplicationContext());
                announce("Traducción turco → español lista. Escuchando…");
            } catch (Exception ex) {
                announce("Faltan modelos de traducción en APK: " + ex.getMessage());
            }
        });
    }

    private boolean active(int session) { return alive && listening && epoch == session; }

    private void processMicrophone(int session) {
        AudioRecord local = null;
        try {
            File modelDir = AssetUtils.extractDirectory(this,"vosk-model-small-tr-0.3");
            if (!active(session)) return;
            try (Model model = new Model(modelDir.getAbsolutePath());
                 Recognizer recognizer = new Recognizer(model,(float)HZ)) {
                int size = AudioRecord.getMinBufferSize(HZ, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (size <= 0) throw new IllegalStateException("PCM16 mono 16kHz no disponible");
                local = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, HZ,
                        AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,Math.max(size,6400));
                if (local.getState() != AudioRecord.STATE_INITIALIZED) throw new IllegalStateException("Micrófono ocupado");
                recorder = local;
                if (!active(session)) return;
                local.startRecording();
                short[] frames = new short[1600];
                while (active(session)) {
                    int n = local.read(frames,0,frames.length,AudioRecord.READ_BLOCKING);
                    if (n < 0) throw new IllegalStateException("AudioRecord.read=" + n);
                    if (n == 0) continue;
                    if (recognizer.acceptWaveForm(frames,n)) {
                        String result = new JSONObject(recognizer.getResult()).optString("text","").trim();
                        if (!result.isEmpty()) scheduleTranslation(result,session);
                    }
                }
                // No confirmar audio residual tras cambiar de teclado o de campo.
            }
        } catch (Exception ex) {
            if (active(session)) announce("Error de captura/reconocimiento: " + ex.getMessage());
        } finally {
            if (local != null) {
                try { if (local.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) local.stop(); }
                catch (IllegalStateException ignored) {}
                local.release();
            }
            if (recorder == local) recorder = null;
            if (epoch == session) listening = false;
        }
    }

    private void scheduleTranslation(String text, int session) {
        try {
            translationThread.execute(() -> {
                if (!active(session)) return;
                if (localTranslator == null) {
                    announce("El traductor no está disponible. Verifica los modelos integrados.");
                    return;
                }
                try {
                    String translated = localTranslator.translate(text).trim();
                    if (translated.isEmpty()) return;
                    ui.post(() -> {
                        if (!active(session)) return;
                        String fragment = translated + " ";
                        InputConnection conn = getCurrentInputConnection();
                        // Sin InputConnection no se cambia el historial: no fingimos haber escrito.
                        if (conn != null && conn.commitText(fragment,1)) {
                            historial.append(fragment);
                            updateView();
                        }
                    });
                } catch (Exception ex) { announce("Traducción local falló: " + ex.getMessage()); }
            });
        } catch (RejectedExecutionException ignored) { /* Servicio cerrado. */ }
    }

    private void announce(String message) {
        ui.post(() -> {
            if (!alive) return;
            status = message;
            updateView();
        });
    }

    private void updateView() {
        if (visor == null) return;
        visor.setText(historial.length() == 0 ? status : historial.toString());
        if (scroll != null) scroll.post(() -> scroll.fullScroll(View.FOCUS_DOWN));
    }

    private synchronized void stopCapture() {
        listening = false;
        epoch++;
        AudioRecord a = recorder;
        if (a != null) try { a.stop(); } catch (IllegalStateException ignored) {}
        // No finalizamos forcibly el thread: libera AudioRecord en finally.
    }

    @Override public void onFinishInputView(boolean finishingInput) {
        stopCapture();
        super.onFinishInputView(finishingInput);
    }
    @Override public boolean onEvaluateFullscreenMode() { return false; }
    @Override public void onDestroy() {
        stopCapture();
        alive = false;
        translationThread.execute(() -> { if (localTranslator != null) localTranslator.close(); });
        translationThread.shutdown();
        super.onDestroy();
    }
    private int px(int dp) { return (int)(dp * getResources().getDisplayMetrics().density + 0.5f); }

    public static final class PreparacionActivity extends Activity {
        private static final int MIC_REQUEST = 19;
        private TextView label;
        @Override protected void onCreate(Bundle state) {
            super.onCreate(state);
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(30,50,30,30);
            label = new TextView(this);
            label.setTextSize(18);
            label.setText("Este teclado usa micrófono y modelos offline integrados. Autoriza el micrófono y habilita el teclado en Ajustes.");
            root.addView(label);
            Button settings = new Button(this);
            settings.setText("Abrir ajustes de teclados");
            settings.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
            root.addView(settings);
            setContentView(root);
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},MIC_REQUEST);
        }
        @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] grants) {
            super.onRequestPermissionsResult(code,permissions,grants);
            if (code == MIC_REQUEST && (grants.length == 0 || grants[0] != PackageManager.PERMISSION_GRANTED))
                label.setText("Debes permitir el micrófono para reconocer la voz. Puedes concederlo desde Ajustes de aplicación.");
        }
    }
}


