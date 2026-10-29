package com.tuapp.traductor;

import android.content.Context;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Iterator;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;

/**
 * Translator local y sin red: OPUS-MT tr->es (Marian), ONNX Runtime CPU.
 * Los modelos y los dos SentencePiece se incluyen en assets antes de generar la APK.
 * Decodificación greedy sin KV-cache: posible lentitud en móviles modestos.
 */
final class OfflineMarianTranslator implements AutoCloseable {
    private OrtEnvironment env;
    private OrtSession encoder;
    private OrtSession decoder;
    private long sourceSp;
    private long targetSp;
    private JSONObject vocab;
    private String[] idToPiece;
    private int eosId = 0;
    private int padId = 65000;
    private int startId = 65000;
    private int unkId = 1;
    private boolean closed;

    OfflineMarianTranslator(Context ctx) throws Exception {
        File path = AssetUtils.extractDirectory(ctx, "marian-tr-es");
        File enc = new File(path, "encoder_model.onnx");
        File dec = new File(path, "decoder_model.onnx");
        if (!enc.isFile() || !dec.isFile()) throw new IllegalStateException("Faltan gráficos ONNX encoder/decoder");
        try {
            JSONObject conf = new JSONObject(read(new File(path,"config.json")));
            padId = conf.optInt("pad_token_id", padId);
            startId = conf.optInt("decoder_start_token_id", padId);
            eosId = conf.optInt("eos_token_id", eosId);
            unkId = conf.optInt("unk_token_id", unkId);
            vocab = new JSONObject(read(new File(path,"vocab.json")));
            int high = -1;
            for (Iterator<String> it = vocab.keys(); it.hasNext();) high = Math.max(high, vocab.optInt(it.next(), 0));
            idToPiece = new String[Math.max(high + 1, conf.optInt("vocab_size",0))];
            for (Iterator<String> it = vocab.keys(); it.hasNext();) {
                String piece = it.next(); int id = vocab.optInt(piece,-1);
                if (id >= 0 && id < idToPiece.length) idToPiece[id] = piece;
            }
            sourceSp = SentencePieceBridge.load(new File(path,"source.spm").getAbsolutePath());
            targetSp = SentencePieceBridge.load(new File(path,"target.spm").getAbsolutePath());
            env = OrtEnvironment.getEnvironment();
            OrtSession.SessionOptions o = new OrtSession.SessionOptions();
            o.setIntraOpNumThreads(2);
            o.setInterOpNumThreads(1);
            encoder = env.createSession(enc.getAbsolutePath(), o);
            decoder = env.createSession(dec.getAbsolutePath(), o);
            o.close();
        } catch (Exception ex) {
            close();
            throw ex;
        }
    }

    private static String read(File f) throws Exception {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }

    synchronized String translate(String turkish) throws Exception {
        if (closed || encoder == null || decoder == null) throw new IllegalStateException("Modelo no cargado");
        String text = turkish.trim();
        if (text.isEmpty()) return "";
        String[] pieces = SentencePieceBridge.encode(sourceSp,text);
        int sourceLen = Math.min(pieces.length, 126);
        long[][] in = new long[1][sourceLen + 1];
        long[][] mask = new long[1][sourceLen + 1];
        for (int i=0;i<sourceLen;i++) {
            in[0][i] = vocab.optInt(pieces[i], unkId);
            mask[0][i] = 1L;
        }
        in[0][sourceLen] = eosId;
        mask[0][sourceLen] = 1L;
        float[][][] hidden;
        try (OnnxTensor token = OnnxTensor.createTensor(env,in);
             OnnxTensor attention = OnnxTensor.createTensor(env,mask)) {
            Map<String,OnnxTensor> args = new HashMap<>();
            args.put("input_ids",token); args.put("attention_mask",attention);
            try (OrtSession.Result r = encoder.run(args)) {
                hidden = (float[][][]) r.get(0).getValue();
            }
        }
        ArrayList<Long> output = new ArrayList<>();
        output.add((long)startId);
        // Decodificador sin KV-cache: se repite con el prefijo generado.
        try (OnnxTensor memory = OnnxTensor.createTensor(env,hidden);
             OnnxTensor attention = OnnxTensor.createTensor(env,mask)) {
            for (int step=0;step<72;step++) {
                long[][] ids = new long[1][output.size()];
                for (int i=0;i<output.size();i++) ids[0][i] = output.get(i);
                try (OnnxTensor prev = OnnxTensor.createTensor(env, ids)) {
                    Map<String,OnnxTensor> args = new HashMap<>();
                    args.put("input_ids",prev);
                    args.put("encoder_hidden_states",memory);
                    args.put("encoder_attention_mask",attention);
                    try (OrtSession.Result r = decoder.run(args)) {
                        float[][][] logits = (float[][][])r.get(0).getValue();
                        float[] last = logits[0][logits[0].length-1];
                        int next = 0;
                        for (int i=1;i<last.length;i++) if (last[i] > last[next]) next = i;
                        if (next == eosId || next == padId) break;
                        output.add((long)next);
                    }
                }
            }
        }
        ArrayList<String> translatedPieces = new ArrayList<>();
        for (int i=1;i<output.size();i++) {
            int idx = output.get(i).intValue();
            if (idx >= 0 && idx < idToPiece.length && idToPiece[idx] != null)
                translatedPieces.add(idToPiece[idx]);
        }
        return SentencePieceBridge.decode(targetSp,translatedPieces.toArray(new String[0])).trim();
    }

    @Override public synchronized void close() {
        closed = true;
        if (encoder != null) {
            try { encoder.close(); } catch (Exception ignored) { }
            encoder = null;
        }
        if (decoder != null) {
            try { decoder.close(); } catch (Exception ignored) { }
            decoder = null;
        }
        if (sourceSp != 0) { SentencePieceBridge.release(sourceSp); sourceSp = 0; }
        if (targetSp != 0) { SentencePieceBridge.release(targetSp); targetSp = 0; }
    }
}


