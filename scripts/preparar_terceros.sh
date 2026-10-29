#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
TARGET='app/src/main/cpp/third_party/sentencepiece'
if [[ ! -f "$TARGET/CMakeLists.txt" ]]; then
  mkdir -p "$(dirname "$TARGET")"
  git clone --depth 1 --branch v0.2.1 https://github.com/google/sentencepiece.git "$TARGET"
fi


