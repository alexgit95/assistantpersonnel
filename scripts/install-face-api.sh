#!/usr/bin/env bash
# Telecharge la bibliotheque et ses modeles dans les ressources statiques pour un build local sans CDN.
# Usage : scripts/install-face-api.sh [version face-api] [version tfjs-backend-wasm]
set -euo pipefail

FACE_API_VERSION="${1:-1.7.15}"
WASM_VERSION="${2:-4.22.0}"
DEST="$(cd "$(dirname "$0")/.." && pwd)/src/main/resources/static/vendor/face-api"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

cd "$TMP"
npm pack "@vladmandic/face-api@${FACE_API_VERSION}" --silent
mkdir face-api && tar -xzf vladmandic-face-api-*.tgz -C face-api
npm pack "@tensorflow/tfjs-backend-wasm@${WASM_VERSION}" --silent
mkdir wasm && tar -xzf tensorflow-tfjs-backend-wasm-*.tgz -C wasm

mkdir -p "$DEST/model" "$DEST/wasm"
cp face-api/package/dist/face-api.js "$DEST/face-api.js"
for model in tiny_face_detector_model face_landmark_68_tiny_model face_recognition_model; do
  cp face-api/package/model/${model}* "$DEST/model/"
done
cp wasm/package/dist/*.wasm "$DEST/wasm/"
cp wasm/package/dist/tf-backend-wasm.js "$DEST/wasm/"
cp face-api/package/LICENSE "$DEST/LICENSE-face-api.txt"
cp wasm/package/LICENSE* "$DEST/LICENSE-tfjs-backend-wasm.txt" 2>/dev/null \
  || echo "Apache-2.0 - https://github.com/tensorflow/tfjs" > "$DEST/LICENSE-tfjs-backend-wasm.txt"

echo "face-api ${FACE_API_VERSION} installe dans ${DEST}"
