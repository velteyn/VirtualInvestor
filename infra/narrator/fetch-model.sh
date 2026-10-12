#!/bin/sh
# Fetch the newsroom model (idempotent): Qwen2.5-3B-Instruct, Q4_K_M (~1.9 GB).
# Runs on the reference i5/8GB box inside the 60s/article budget.
# Usage: infra/narrator/fetch-model.sh  (or let the entrypoint call it)
set -eu

MODEL_URL="${MODEL_URL:-https://huggingface.co/Qwen/Qwen2.5-3B-Instruct-GGUF/resolve/main/qwen2.5-3b-instruct-q4_k_m.gguf}"
MODEL_FILE="${MODEL_FILE:-qwen2.5-3b-instruct-q4_k_m.gguf}"
MODEL_DIR="${MODEL_DIR:-$(dirname "$0")/../narrator-models}"

mkdir -p "$MODEL_DIR"
if [ -s "$MODEL_DIR/$MODEL_FILE" ]; then
    echo "model present: $MODEL_DIR/$MODEL_FILE"
    exit 0
fi
echo "downloading $MODEL_FILE (~1.9 GB) ..."
curl -sfL --retry 3 -o "$MODEL_DIR/$MODEL_FILE.tmp" "$MODEL_URL"
mv "$MODEL_DIR/$MODEL_FILE.tmp" "$MODEL_DIR/$MODEL_FILE"
echo "saved: $MODEL_DIR/$MODEL_FILE"
