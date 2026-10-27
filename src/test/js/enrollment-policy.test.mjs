import assert from 'node:assert/strict';
import { test } from 'node:test';
import { assessEnrollmentCapture, canChooseAvatar, canSaveEnrollment } from '../../main/resources/static/js/enrollment-policy.js';

const vector = (base) => Array.from({ length: 128 }, (_, i) => base + i * 0.0001);
const config = { matchThreshold: 0.55, matchMargin: 0.05 };
const faces = [
  { id: 1, name: 'Alice', embeddings: [vector(0)] },
  { id: 2, name: 'Bob', embeddings: [vector(3)] },
];

test('enrollment accepts a face not already registered', () => {
  const result = assessEnrollmentCapture({ status: 'ok', descriptor: vector(8) }, faces, null, config);
  assert.equal(result.status, 'accepted');
  assert.equal(result.descriptor.length, 128);
});

test('enrollment rejects an existing face', () => {
  const result = assessEnrollmentCapture({ status: 'ok', descriptor: vector(0) }, faces, null, config);
  assert.equal(result.status, 'duplicate');
  assert.equal(result.profileId, 1);
});

test('recapture excludes the current profile but still rejects another profile', () => {
  assert.equal(assessEnrollmentCapture({ status: 'ok', descriptor: vector(0) }, faces, 1, config).status, 'accepted');
  const result = assessEnrollmentCapture({ status: 'ok', descriptor: vector(3) }, faces, 1, config);
  assert.equal(result.status, 'duplicate');
  assert.equal(result.profileId, 2);
});

test('enrollment reports no-face and too-small results without a descriptor', () => {
  assert.equal(assessEnrollmentCapture({ status: 'none' }, faces, null, config).status, 'none');
  assert.equal(assessEnrollmentCapture({ status: 'tooSmall' }, faces, null, config).status, 'tooSmall');
});

test('enrollment blocks an ambiguous match', () => {
  const similar = [
    { id: 1, embeddings: [vector(0)] },
    { id: 2, embeddings: [vector(0.01)] },
  ];
  assert.equal(assessEnrollmentCapture({ status: 'ok', descriptor: vector(0.005) }, similar, null, config).status,
    'ambiguous');
});

test('avatar selection and save require configured capture count and valid selection', () => {
  assert.equal(canChooseAvatar(4, 5), false);
  assert.equal(canChooseAvatar(5, 5), true);
  assert.equal(canSaveEnrollment(4, 5, 8, 0), false);
  assert.equal(canSaveEnrollment(5, 5, 8, 4), true);
  assert.equal(canSaveEnrollment(9, 5, 8, 0), false);
  assert.equal(canSaveEnrollment(5, 5, 8, 5), false);
});
