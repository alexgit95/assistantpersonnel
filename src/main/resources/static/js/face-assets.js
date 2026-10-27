const MODEL_MANIFESTS = [
  'tiny_face_detector_model-weights_manifest.json',
  'face_landmark_68_tiny_model-weights_manifest.json',
  'face_recognition_model-weights_manifest.json',
];
const WASM_FILES = [
  'tfjs-backend-wasm.wasm',
  'tfjs-backend-wasm-simd.wasm',
  'tfjs-backend-wasm-threaded-simd.wasm',
];

function absoluteUrl(url) {
  return new URL(url, window.location.href).href;
}

function joinUrl(base, file) {
  return new URL(file, `${absoluteUrl(base).replace(/\/$/, '')}/`).href;
}

function assertResponse(response, url) {
  if (!response.ok) {
    throw new Error(`Chargement impossible (${response.status}) : ${url}`);
  }
  return response;
}

async function consume(response, onBytes, collect = false) {
  const total = Number(response.headers.get('content-length')) || 0;
  if (!response.body?.getReader) {
    const data = await response.arrayBuffer();
    onBytes(data.byteLength, total || data.byteLength);
    return collect ? new Uint8Array(data) : null;
  }

  const reader = response.body.getReader();
  const chunks = [];
  let received = 0;
  for (;;) {
    const { done, value } = await reader.read();
    if (done) {
      break;
    }
    if (collect) {
      chunks.push(value);
    }
    received += value.byteLength;
    onBytes(received, total);
  }
  if (!collect) {
    onBytes(received, total || received);
    return null;
  }
  const result = new Uint8Array(received);
  let offset = 0;
  for (const chunk of chunks) {
    result.set(chunk, offset);
    offset += chunk.byteLength;
  }
  onBytes(received, total || received);
  return result;
}

async function loadAsset(url, label, start, end, onProgress, parseJson = false) {
  const response = assertResponse(await fetch(url, { cache: 'force-cache', credentials: 'omit' }), url);
  const bytes = await consume(response, (received, total) => {
    const fraction = total > 0 ? Math.min(received / total, 1) : 0;
    onProgress({ percent: start + (end - start) * fraction, label });
  }, parseJson);
  return parseJson ? JSON.parse(new TextDecoder().decode(bytes)) : null;
}

function modelShardUrls(manifest, manifestUrl) {
  if (!Array.isArray(manifest)) {
    throw new Error('Manifeste de modèle invalide.');
  }
  return manifest.flatMap((group) => group.paths ?? []).map((path) => new URL(path, manifestUrl).href);
}

/** Précharge la bibliothèque, les manifestes, les poids et WASM avec le cache HTTP du navigateur. */
export async function prepareFaceAssets(config, onProgress = () => {}) {
  const faceApiUrl = absoluteUrl(config.faceApiUrl);
  const modelsBase = absoluteUrl(config.faceModelsUrl).replace(/\/$/, '');
  const wasmScriptUrl = absoluteUrl(config.wasmScriptUrl);
  const wasmBase = absoluteUrl(config.wasmUrl).replace(/\/$/, '');

  const libraryAssets = [
    { url: faceApiUrl, label: 'Bibliothèque de reconnaissance' },
    { url: wasmScriptUrl, label: 'Moteur WebAssembly' },
  ];
  for (let index = 0; index < libraryAssets.length; index++) {
    const item = libraryAssets[index];
    await loadAsset(item.url, item.label, index * 10, (index + 1) * 10, onProgress);
  }

  const manifests = [];
  for (let index = 0; index < MODEL_MANIFESTS.length; index++) {
    const file = MODEL_MANIFESTS[index];
    const url = joinUrl(modelsBase, file);
    const manifest = await loadAsset(url, `Préparation des modèles (${index + 1}/${MODEL_MANIFESTS.length})`,
      20 + (index / MODEL_MANIFESTS.length) * 10,
      20 + ((index + 1) / MODEL_MANIFESTS.length) * 10, onProgress, true);
    manifests.push(...modelShardUrls(manifest, url));
  }

  const wasmFiles = WASM_FILES.map((file) => joinUrl(wasmBase, file));
  const weightAssets = [...new Set([...manifests, ...wasmFiles])];
  for (let index = 0; index < weightAssets.length; index++) {
    await loadAsset(weightAssets[index], `Téléchargement des modèles (${index + 1}/${weightAssets.length})`,
      30 + (index / weightAssets.length) * 70,
      30 + ((index + 1) / weightAssets.length) * 70, onProgress);
  }

  onProgress({ percent: 100, label: 'Modèles prêts, cache navigateur activé' });
  return { faceApiUrl, modelsUrl: modelsBase, wasmScriptUrl, wasmUrl: `${wasmBase}/` };
}
