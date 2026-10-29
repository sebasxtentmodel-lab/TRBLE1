#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
ASSETS="$(pwd)/app/src/main/assets"
mkdir -p "$ASSETS"
if [[ ! -f "$ASSETS/vosk-model-small-tr-0.3/am/final.mdl" ]]; then
  echo 'Descargando Vosk turco solo durante la COMPILACION (nunca durante instalación)' >&2
  curl --fail --location --retry 5 --output /tmp/vosk-model-small-tr-0.3.zip \
    https://alphacephei.com/vosk/models/vosk-model-small-tr-0.3.zip
  unzip -tq /tmp/vosk-model-small-tr-0.3.zip >/dev/null
  unzip -q /tmp/vosk-model-small-tr-0.3.zip -d "$ASSETS"
fi

MODEL="Helsinki-NLP/opus-mt-tr-es"
EXPORT="$(pwd)/.build-onnx-mar"
DEST="$ASSETS/marian-tr-es"
if [[ ! -f "$DEST/encoder_model.onnx" || ! -f "$DEST/decoder_model.onnx" ]]; then
  echo 'Exportando el traductor OPUS-MT TR->ES a ONNX (solo en máquina de compilación)...'
  optimum-cli export onnx --model "$MODEL" --task text2text-generation "$EXPORT"
  mkdir -p "$DEST"
  # Reducir los pesos FP32 a int8 sin retirar los modelos: más ligero para Android.
  python - "$EXPORT" "$DEST" <<'PY'
import sys
from pathlib import Path
from onnxruntime.quantization import quantize_dynamic, QuantType
src, out = map(Path, sys.argv[1:])
for name in ("encoder_model.onnx", "decoder_model.onnx"):
    print(f"Cuantizando {name} a int8...", flush=True)
    quantize_dynamic(str(src/name), str(out/name), weight_type=QuantType.QInt8, per_channel=False)
    print(f"{name}: {(out/name).stat().st_size/1048576:.1f} MiB", flush=True)
PY
  # Los modelos pueden exportar tensores de pesos externos; conservar todos.
  find "$EXPORT" -maxdepth 1 -type f \( -name '*.onnx_data' -o -name '*.onnx.data' \) -exec cp {} "$DEST/" \;
  cp "$EXPORT/config.json" "$DEST/config.json"
  cp "$EXPORT/vocab.json" "$DEST/vocab.json"
  cp "$EXPORT/source.spm" "$DEST/source.spm"
  cp "$EXPORT/target.spm" "$DEST/target.spm"
fi
python scripts/verificar_assets.py


