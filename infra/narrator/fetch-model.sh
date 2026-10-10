#!/bin/sh
# Fetch the newsroom's tiny model (idempotent): the smallest usable instruct
# model in the families from exploration — Qwen2.5-0.5B-Instruct, Q8 (~640 MB).
# Runs on the reference i5/8GB box at well above the 10-25s/article budget.
# Usage: infra/narrator/fetch-model.sh  (or let the entrypoint call it)
set -eu

MODEL_URL="${MODEL_URL:-https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf}"
MODEL_FILE="${MODEL_FILE:-qwen2.5-0.5b-instruct-q8_0.gguf}"
MODEL_DIR="${MODEL_DIR:-$(dirname "$0")/../narrator-models}"

mkdir -p "$MODEL_DIR"
if [ -s "$MODEL_DIR/$MODEL_FILE" ]; then
    echo "model present: $MODEL_DIR/$MODEL_FILE"
    exit 0
fi
echo "downloading $MODEL_FILE (~640 MB) ..."
curl -sfL --retry 3 -o "$MODEL_DIR/$MODEL_FILE.tmp" "$MODEL_URL"
mv "$MODEL_DIR/$MODEL_FILE.tmp" "$MODEL_DIR/$MODEL_FILE"
echo "saved: $MODEL_DIR/$MODEL_FILE"
