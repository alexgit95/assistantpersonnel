// Miniature d'avatar (128 x 128, JPEG) recadree autour du visage detecte dans l'image video.
export function avatarFromVideo(video, box) {
  const size = 128;
  if (!video.videoWidth || !video.videoHeight || !box) {
    throw new Error('La caméra n’est pas encore prête.');
  }
  const cropSize = Math.min(Math.max(box.width, box.height) * 1.45, video.videoWidth, video.videoHeight);
  const centerX = box.x + box.width / 2;
  const centerY = box.y + box.height / 2;
  const left = Math.max(0, Math.min(video.videoWidth - cropSize, centerX - cropSize / 2));
  const top = Math.max(0, Math.min(video.videoHeight - cropSize, centerY - cropSize / 2));
  const canvas = document.createElement('canvas');
  canvas.width = size;
  canvas.height = size;
  const context = canvas.getContext('2d', { alpha: false });
  context.drawImage(video, left, top, cropSize, cropSize, 0, 0, size, size);
  return canvas.toDataURL('image/jpeg', 0.82);
}
