// Logique pure de reconnaissance (sans DOM ni face-api) : testable avec `node --test`.

export function distance(a, b) {
  if (a.length !== b.length) {
    return Number.POSITIVE_INFINITY;
  }
  let sum = 0;
  for (let i = 0; i < a.length; i++) {
    const d = a[i] - b[i];
    sum += d * d;
  }
  return Math.sqrt(sum);
}

export function minDistance(descriptor, embeddings) {
  let best = Number.POSITIVE_INFINITY;
  for (const embedding of embeddings) {
    const d = distance(descriptor, embedding);
    if (d < best) {
      best = d;
    }
  }
  return best;
}

/**
 * Compare un descripteur aux profils connus.
 * faces : [{ id, name, embeddings: number[][] }]
 * Retourne { status: 'matched' | 'unknown' | 'ambiguous', profileId?, distance, secondDistance, margin }
 */
export function classify(descriptor, faces, { matchThreshold, matchMargin }) {
  const ranked = faces
    .map((face) => ({ id: face.id, distance: minDistance(descriptor, face.embeddings) }))
    .filter((entry) => Number.isFinite(entry.distance))
    .sort((a, b) => a.distance - b.distance);

  if (ranked.length === 0) {
    return { status: 'unknown', distance: null, secondDistance: null, margin: null };
  }
  const best = ranked[0];
  const second = ranked.length > 1 ? ranked[1] : null;
  const secondDistance = second ? second.distance : null;
  const margin = second ? second.distance - best.distance : null;

  if (best.distance > matchThreshold) {
    return { status: 'unknown', distance: best.distance, secondDistance, margin };
  }
  if (matchMargin > 0 && margin !== null && margin < matchMargin) {
    return { status: 'ambiguous', distance: best.distance, secondDistance, margin };
  }
  return { status: 'matched', profileId: best.id, distance: best.distance, secondDistance, margin };
}

/**
 * Retient le plus grand visage (par surface).
 * detections : [{ box: { width, height }, ... }]
 * Retourne { status: 'none' | 'tooSmall' | 'ok', detection? }
 */
export function pickLargestFace(detections, minFaceSizePx) {
  if (!detections || detections.length === 0) {
    return { status: 'none' };
  }
  let largest = detections[0];
  for (const detection of detections) {
    if (detection.box.width * detection.box.height > largest.box.width * largest.box.height) {
      largest = detection;
    }
  }
  if (Math.min(largest.box.width, largest.box.height) < minFaceSizePx) {
    return { status: 'tooSmall' };
  }
  return { status: 'ok', detection: largest };
}
