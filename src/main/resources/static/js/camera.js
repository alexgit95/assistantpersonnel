// Acces a la camera et miniature video.

export class CameraError extends Error {}

export async function startCamera(video, width) {
  if (!window.isSecureContext || !navigator.mediaDevices?.getUserMedia) {
    throw new CameraError(
      "La caméra exige HTTPS ou localhost. Ouvrez l'application via l'adresse HTTPS du serveur.");
  }
  let stream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'user', width: { ideal: width } },
      audio: false,
    });
  } catch (error) {
    throw new CameraError(messageFor(error));
  }
  video.srcObject = stream;
  await video.play();
  return stream;
}

export function stopCamera(stream) {
  stream?.getTracks().forEach((track) => track.stop());
}

function messageFor(error) {
  switch (error?.name) {
    case 'NotAllowedError':
      return "Accès à la caméra refusé. Autorisez la caméra pour ce site dans le navigateur.";
    case 'NotFoundError':
      return 'Aucune caméra détectée sur cet appareil.';
    case 'NotReadableError':
      return 'La caméra est utilisée par une autre application.';
    default:
      return "Impossible de démarrer la caméra.";
  }
}
