import assert from 'node:assert/strict';
import { test } from 'node:test';
import { classify, distance, minDistance, pickLargestFace } from '../../main/resources/static/js/matcher.js';

const vec = (base) => Array.from({ length: 4 }, (_, i) => base + i * 0.01);
const opts = { matchThreshold: 0.55, matchMargin: 0.05 };
const faces = [
  { id: 1, name: 'Alice', embeddings: [vec(0), vec(0.05)] },
  { id: 2, name: 'Bob', embeddings: [vec(5)] },
];

test('distance euclidienne et vecteurs de tailles differentes', () => {
  assert.equal(distance([0, 0], [3, 4]), 5);
  assert.equal(distance([0], [0, 1]), Number.POSITIVE_INFINITY);
});

test('minDistance retient le plus proche embedding', () => {
  assert.ok(minDistance(vec(0.05), faces[0].embeddings) < 1e-9);
});

test('visage connu reconnu', () => {
  const result = classify(vec(0.01), faces, opts);
  assert.equal(result.status, 'matched');
  assert.equal(result.profileId, 1);
});

test('visage trop eloigne = inconnu', () => {
  assert.equal(classify(vec(20), faces, opts).status, 'unknown');
});

test('aucun profil = inconnu', () => {
  assert.equal(classify(vec(0), [], opts).status, 'unknown');
});

test('deux profils trop proches = ambigu', () => {
  const close = [
    { id: 1, embeddings: [[0, 0]] },
    { id: 2, embeddings: [[0.02, 0]] },
  ];
  assert.equal(classify([0.01, 0], close, { matchThreshold: 0.55, matchMargin: 0.05 }).status, 'ambiguous');
});

test('marge a 0 desactive la detection d\'ambiguite', () => {
  const close = [
    { id: 1, embeddings: [[0, 0]] },
    { id: 2, embeddings: [[0.02, 0]] },
  ];
  const result = classify([0.001, 0], close, { matchThreshold: 0.55, matchMargin: 0 });
  assert.equal(result.status, 'matched');
  assert.equal(result.profileId, 1);
});

test('un seul profil : pas de marge a verifier', () => {
  assert.equal(classify(vec(0), [faces[0]], opts).status, 'matched');
});

test('le seuil est inclusif', () => {
  assert.equal(classify([0.55], [{ id: 1, embeddings: [[0]] }], opts).status, 'matched');
  assert.equal(classify([0.56], [{ id: 1, embeddings: [[0]] }], opts).status, 'unknown');
});

test('pickLargestFace : aucun visage', () => {
  assert.equal(pickLargestFace([], 80).status, 'none');
  assert.equal(pickLargestFace(undefined, 80).status, 'none');
});

test('pickLargestFace : retient la plus grande surface', () => {
  const small = { box: { width: 100, height: 100 }, id: 'small' };
  const large = { box: { width: 200, height: 180 }, id: 'large' };
  const result = pickLargestFace([small, large], 80);
  assert.equal(result.status, 'ok');
  assert.equal(result.detection.id, 'large');
});

test('pickLargestFace : visage trop petit', () => {
  assert.equal(pickLargestFace([{ box: { width: 60, height: 90 } }], 80).status, 'tooSmall');
});
