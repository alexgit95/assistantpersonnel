import { classify } from './matcher.js';

export function assessEnrollmentCapture(detection, faces, currentProfileId, config) {
  if (!detection || detection.status !== 'ok') {
    return { status: detection?.status ?? 'none' };
  }
  const existingFaces = currentProfileId == null
    ? faces
    : faces.filter((face) => face.id !== currentProfileId);
  const match = classify(detection.descriptor, existingFaces, config);
  if (match.status === 'matched') {
    return { status: 'duplicate', profileId: match.profileId, distance: match.distance };
  }
  if (match.status === 'ambiguous') {
    return { status: 'ambiguous', distance: match.distance, margin: match.margin };
  }
  return { status: 'accepted', descriptor: detection.descriptor };
}

export function canChooseAvatar(captureCount, photos) {
  return captureCount === photos;
}

export function canSaveEnrollment(captureCount, photos, avatarIndex) {
  return captureCount === photos
    && Number.isInteger(avatarIndex)
    && avatarIndex >= 0
    && avatarIndex < captureCount;
}
