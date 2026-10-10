#!/bin/sh
# Container entrypoint: ensure the weights exist (install phase, idempotent),
# then serve them. MODEL_URL/MODEL_FILE/MODEL_DIR mirror fetch-model.sh.
set -eu

MODEL_FILE="${MODEL_FILE:-qwen2.5-0.5b-instruct-q8_0.gguf}"
MODEL_DIR="${MODEL_DIR:-/models}"
export MODEL_DIR MODEL_FILE
export MODEL_URL="${MODEL_URL:-https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf}"

if [ ! -s "$MODEL_DIR/$MODEL_FILE" ]; then
    echo "weights missing, fetching (one-time, ~640 MB) ..." >&2
    /fetch-model.sh
fi
# Container port 8081, not 8080: some hosts filter outbound traffic to
# container port 8080 (this dev box answers it with 404s), so the sidecar
# listens on 8081 and compose maps the usual 18080 to it.
exec /app/llama-server \
    --model "$MODEL_DIR/$MODEL_FILE" \
    --host 0.0.0.0 \
    --port 8081 \
    --ctx-size 2048 \
    --threads "$(nproc)"
