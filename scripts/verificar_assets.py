from pathlib import Path
import json
root = Path(__file__).resolve().parent.parent / 'app/src/main/assets'
files = [
  'vosk-model-small-tr-0.3/am/final.mdl',
  'vosk-model-small-tr-0.3/conf/model.conf',
  'marian-tr-es/encoder_model.onnx',
  'marian-tr-es/decoder_model.onnx',
  'marian-tr-es/config.json',
  'marian-tr-es/vocab.json',
  'marian-tr-es/source.spm',
  'marian-tr-es/target.spm'
]
missing = [x for x in files if not (root / x).is_file() or not (root / x).stat().st_size]
if missing: raise SystemExit('FALTAN ASSETS: '+', '.join(missing))
cfg = json.loads((root/'marian-tr-es/config.json').read_text())
import onnx
for name, expected in [('encoder_model.onnx', {'input_ids','attention_mask'}),
                       ('decoder_model.onnx', {'input_ids','encoder_hidden_states','encoder_attention_mask'})]:
    path = root/'marian-tr-es'/name
    graph = onnx.load(str(path), load_external_data=False).graph
    inputs = {t.name for t in graph.input}
    if not expected.issubset(inputs):
        raise SystemExit(f'{name}: entradas incompatibles {sorted(inputs)}; esperadas {sorted(expected)}')
    outputs = [t.name for t in graph.output]
    print(f'{name}: inputs={sorted(inputs)} outputs={outputs}, size={path.stat().st_size/1048576:.1f} MiB')
print('Modelos de reconocimiento/traducción presentes y estructura ONNX verificada.')
print('AVISO: verificar precisión en teléfono. Esta comprobación NO ejecuta traducción.')


