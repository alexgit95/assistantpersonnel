// Detection et calcul d'empreinte dans le navigateur avec @vladmandic/face-api (variable globale `faceapi`).
import { pickLargestFace } from './matcher.js';
import { prepareFaceAssets } from './face-assets.js';

const BACKEND_ORDER = { auto: ['webgl', 'wasm', 'cpu'], webgl: ['webgl'], wasm: ['wasm'], cpu: ['cpu'] };

export class RecognizerError extends Error {}

function loadScript(url, name) {
  return new Promise((resolve, reject) => {
    const existing = document.querySelector(`script[data-face-asset="${name}"]`);
    if (existing?.dataset.loaded === 'true') {
      resolve();
      return;
    }
    const script = existing ?? document.createElement('script');
    script.src = url;
    script.crossOrigin = 'anonymous';
    script.dataset.faceAsset = name;
    script.onload = () => {
      script.dataset.loaded = 'true';
      resolve();
    };
    script.onerror = () => reject(new RecognizerError(`Impossible de charger ${name} depuis ${url}.`));
    if (!existing) {
      document.head.append(script);
    }
  });
}

async function selectBackend(requested, wasmUrl) {
  const tf = faceapi.tf;
  if (tf.wasm?.setWasmPaths) {
    tf.wasm.setWasmPaths(wasmUrl);
  }
  for (const name of BACKEND_ORDER[requested] ?? BACKEND_ORDER.auto) {
    try {
      if (await tf.setBackend(name)) {
        await tf.ready();
        return name;
      }
    } catch (error) {
      console.warn(`Moteur ${name} indisponible`, error);
    }
  }
  throw new RecognizerError("Aucun moteur de calcul n'est disponible pour la reconnaissance faciale.");
}

export async function createRecognizer(config, onProgress = () => {}) {
  let assets;
  try {
    assets = await prepareFaceAssets(config, onProgress);
    await loadScript(assets.faceApiUrl, 'face-api');
    if (typeof faceapi === 'undefined') {
      throw new RecognizerError('La bibliothèque de reconnaissance ne fournit pas son API.');
    }

    window.tf = faceapi.tf;
    await loadScript(assets.wasmScriptUrl, 'backend WebAssembly');
    delete window.tf;

    const backend = await selectBackend(config.recognitionBackend, assets.wasmUrl);
    await Promise.all([
      faceapi.nets.tinyFaceDetector.loadFromUri(assets.modelsUrl),
      faceapi.nets.faceLandmark68TinyNet.loadFromUri(assets.modelsUrl),
      faceapi.nets.faceRecognitionNet.loadFromUri(assets.modelsUrl),
    ]);
    const options = new faceapi.TinyFaceDetectorOptions({
      inputSize: config.detectorInputSize,
      scoreThreshold: config.detectorMinConfidence,
    });

    return {
      backend,
      /** Retourne { status, descriptor?, timeMs } pour le plus grand visage de l'image video. */
      async detect(video) {
        const start = performance.now();
        const results = await faceapi.detectAllFaces(video, options).withFaceLandmarks(true).withFaceDescriptors();
        const faces = results.map((r) => ({ box: r.detection.box, descriptor: r.descriptor }));
        const picked = pickLargestFace(faces, config.minFaceSizePx);
        const timeMs = Math.round(performance.now() - start);
        if (picked.status !== 'ok') {
          return { status: picked.status, timeMs };
        }
        return {
          status: 'ok',
          descriptor: Array.from(picked.detection.descriptor),
          box: {
            x: picked.detection.box.x,
            y: picked.detection.box.y,
            width: picked.detection.box.width,
            height: picked.detection.box.height,
          },
          timeMs,
        };
      },
    };
  } catch (error) {
    delete window.tf;
    if (error instanceof RecognizerError) {
      throw error;
    }
    const source = config.faceAssetsMode === 'cdn' ? 'le CDN (vérifiez l’accès Internet)' : 'les assets locaux';
    throw new RecognizerError(`Impossible de charger la reconnaissance depuis ${source} : ${error.message}`);
  }
}
