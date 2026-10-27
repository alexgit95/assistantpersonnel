import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { prepareFaceAssets } from '../../main/resources/static/js/face-assets.js';

const originalFetch = globalThis.fetch;
const originalWindow = globalThis.window;

afterEach(() => {
  globalThis.fetch = originalFetch;
  if (originalWindow === undefined) {
    delete globalThis.window;
  } else {
    globalThis.window = originalWindow;
  }
});

function setupResponseMocks({ failAt } = {}) {
  const urls = [];
  const cacheOptions = [];
  const manifests = {
    tiny_face_detector_model: ['tiny_face_detector_model.bin'],
    face_landmark_68_tiny_model: ['face_landmark_68_tiny_model.bin'],
    face_recognition_model: ['face_recognition_model-shard1'],
  };
  globalThis.window = { location: { href: 'http://localhost:8080/' } };
  globalThis.fetch = async (input, options) => {
    const url = String(input);
    urls.push(url);
    cacheOptions.push(options?.cache);
    if (url === failAt) {
      return new Response('missing', { status: 404 });
    }
    const manifestName = Object.keys(manifests).find((name) => url.endsWith(`${name}-weights_manifest.json`));
    if (manifestName) {
      const body = JSON.stringify([{ paths: manifests[manifestName] }]);
      return new Response(body, { status: 200, headers: { 'content-type': 'application/json' } });
    }
    return new Response(new Uint8Array([1, 2, 3, 4]), { status: 200 });
  };
  return { urls, cacheOptions };
}

const localConfig = {
  faceAssetsMode: 'local',
  faceApiUrl: '/vendor/face-api/face-api.js',
  faceModelsUrl: '/vendor/face-api/model',
  wasmScriptUrl: '/vendor/face-api/wasm/tf-backend-wasm.js',
  wasmUrl: '/vendor/face-api/wasm/',
};

test('preloads the library, model manifests, shards and WASM with browser HTTP cache', async () => {
  const { urls, cacheOptions } = setupResponseMocks();
  const progress = [];

  const assets = await prepareFaceAssets(localConfig, (event) => progress.push(event));

  assert.equal(assets.faceApiUrl, 'http://localhost:8080/vendor/face-api/face-api.js');
  assert.equal(urls.length, 11);
  assert.ok(urls.includes('http://localhost:8080/vendor/face-api/model/face_recognition_model-shard1'));
  assert.ok(urls.includes('http://localhost:8080/vendor/face-api/wasm/tfjs-backend-wasm-threaded-simd.wasm'));
  assert.ok(cacheOptions.every((value) => value === 'force-cache'));
  assert.equal(progress.at(-1).percent, 100);
  assert.equal(progress.at(-1).label, 'Modèles prêts, cache navigateur activé');
  assert.ok(progress.every((event, index) => index === 0 || event.percent >= progress[index - 1].percent));
});

test('supports absolute CDN URLs and retains pinned package versions', async () => {
  const { urls } = setupResponseMocks();
  const config = {
    faceAssetsMode: 'cdn',
    faceApiUrl: 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.15/dist/face-api.js',
    faceModelsUrl: 'https://cdn.jsdelivr.net/npm/@vladmandic/face-api@1.7.15/model',
    wasmScriptUrl: 'https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/tf-backend-wasm.js',
    wasmUrl: 'https://cdn.jsdelivr.net/npm/@tensorflow/tfjs-backend-wasm@4.22.0/dist/',
  };

  await prepareFaceAssets(config);

  assert.ok(urls.includes(config.faceApiUrl));
  assert.ok(urls.includes(`${config.faceModelsUrl}/tiny_face_detector_model-weights_manifest.json`));
  assert.ok(urls.includes(`${config.wasmUrl}tfjs-backend-wasm.wasm`));
});

test('fails with the broken CDN or local asset URL instead of continuing silently', async () => {
  const brokenUrl = 'http://localhost:8080/vendor/face-api/face-api.js';
  setupResponseMocks({ failAt: brokenUrl });

  await assert.rejects(prepareFaceAssets(localConfig), /Chargement impossible \(404\)/);
});
