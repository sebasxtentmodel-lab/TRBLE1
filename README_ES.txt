VISOR TURCO ESPAÑOL - PROYECTO FUENTE SIN INTERNET EN EJECUCIÓN
Versión experimental: SIN COMPILAR NI PROBAR EN UN ANDROID REAL.

ESTADO ACTUAL
- No es una APK terminada. Es código fuente y una construcción automatizada.
- El entorno que generó este ZIP NO tiene Android SDK ni acceso de descarga a modelos,
  por lo que no están incorporados los modelos grandes.
- El flujo de GitHub Actions intenta descargar Vosk y OPUS-MT durante la CONSTRUCCIÓN
  y empaquetarlos dentro del APK. EL TELEFONO NO DESCARGA NADA AL USAR LA APP.
- Durante la instalación el usuario tendrá que conceder permiso micrófono y activar
  expresamente el método de entrada en Ajustes > Idiomas y entrada.
- No es posible garantizar uso infinito si Android cierra el teclado, cambia de app,
  interrumpe la captura por llamada o revoca permisos.

COMPILAR
1. Crea un repositorio en GitHub, carga TODO el contenido de esta carpeta a la
   RAÍZ de la rama main, incluyendo .github y scripts (no cargues el ZIP sin descomprimir).
2. GitHub > Actions > Compilar APK autonoma turco-espanol > Run workflow.
3. Si la compilación termina correctamente, descarga el artefacto APK.
4. Instálalo, abre una vez la app para pedir permiso, activa IME desde Ajustes y selecciónalo.
5. La pantalla del teclado solamente es negra con visor; mantén presionado para cambiar IME.

MODELOS PUBLICOS
- Reconocimiento (Vosk): https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip
- Traducción directa neural: https://huggingface.co/Helsinki-NLP/opus-mt-tr-es
- JNI SentencePiece: https://github.com/google/sentencepiece
- ONNX Runtime Android: https://onnxruntime.ai/docs/get-started/with-java.html

NOTAS Y LIMITACIONES
- La exportación ONNX a un grafo compatible no está validada aquí. scripts/verificar_assets.py
  evita fabricar APK con archivos ausentes o firmas de entradas distintas, pero no verifica
  semántica ni velocidad. El modelo de traductor puede requerir más ajustes en hardware real.
- Con pesos FP32 el APK y RAM requeridos pueden ser MUY GRANDES (centenares de MB).
- La traducción se confirma por frases; los buffers de AudioRecord siguen entrando durante
  los silencios, salvo interrupciones del sistema. `commitText()` solo se usa para frases nuevas.
- No se necesitan Google Play Services, Google ML Kit ni acceso INTERNET en ejecución.
- Sin probar la descarga, la exportación y el ensamblado no se debe afirmar que
  exista una APK final ni que funcione desde el primer arranque.


