import { api, ApiError } from './api.js';
import { CameraError, startCamera, stopCamera } from './camera.js';
import { createRecognizer, RecognizerError } from './recognizer.js';
import { assessEnrollmentCapture, canChooseAvatar, canSaveEnrollment } from './enrollment-policy.js';
import { animateMarkedText, typeText } from './typewriter.js';

const GUIDANCE = [
  'Regardez droit vers la caméra, visage détendu.',
  'Tournez légèrement la tête vers la gauche.',
  'Tournez légèrement la tête vers la droite.',
  'Inclinez légèrement le menton vers le bas.',
  'Relevez légèrement le menton.',
  'Placez-vous dans une lumière différente.',
  'Regardez légèrement à gauche de l’objectif.',
  'Regardez légèrement à droite de l’objectif.',
];
const ERROR_MESSAGES = {
  none: 'Aucun visage détecté. Placez-vous devant la caméra.',
  tooSmall: 'Visage trop éloigné. Rapprochez-vous de la caméra.',
  duplicate: 'Ce visage correspond à un profil existant. Réessayez seul devant la caméra.',
  ambiguous: 'Ressemblance ambiguë avec un profil existant. Réessayez avec un meilleur éclairage.',
};

const el = Object.fromEntries([
  'enroll-title', 'enroll-status', 'asset-loading', 'asset-loading-label', 'asset-progress', 'asset-percent',
  'enrollment-workflow', 'name-field', 'profile-name', 'recapture-options', 'enroll-camera', 'capture-guide',
  'capture-button', 'capture-count', 'capture-message', 'capture-gallery', 'choose-avatar', 'avatar-step',
  'avatar-gallery', 'back-to-captures', 'save-profile',
].map((id) => [id, document.getElementById(id)]));

const recaptureMode = new URLSearchParams(window.location.search).get('mode') === 'recapture';
let config;
let recognizer;
let stream;
let faces = [];
let currentProfileId = null;
let captures = [];
let avatarIndex = -1;
let busy = false;

function setStatus(message) {
  typeText(el['enroll-status'], message, typingOptions());
}

function typingOptions() {
  return { enabled: config?.typingEnabled !== false, speedMs: config?.typingSpeedMs ?? 25 };
}

function setProgress({ percent, label }) {
  const value = Math.max(0, Math.min(100, Math.round(percent)));
  el['asset-progress'].value = value;
  el['asset-percent'].value = `${value}%`;
  typeText(el['asset-loading-label'], label, typingOptions());
}

function avatarFromVideo(video, box) {
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

function removeCapture(index) {
  captures.splice(index, 1);
  if (avatarIndex === index) {
    avatarIndex = -1;
  } else if (avatarIndex > index) {
    avatarIndex -= 1;
  }
  renderCaptures();
}

function renderCaptures() {
  const count = captures.length;
  const minimum = config.enrollmentMinPhotos;
  const maximum = config.enrollmentMaxPhotos;
  typeText(el['capture-count'], `${count} / ${minimum} captures minimum (${maximum} maximum)`, typingOptions());
  typeText(el['capture-guide'], GUIDANCE[count % GUIDANCE.length], typingOptions());
  el['capture-button'].disabled = busy || count >= maximum;
  el['choose-avatar'].disabled = busy || !canChooseAvatar(count, minimum);
  el['capture-gallery'].replaceChildren();

  captures.forEach((capture, index) => {
    const figure = document.createElement('figure');
    figure.className = 'capture-card';
    const image = document.createElement('img');
    image.src = capture.avatar;
    image.alt = `Capture ${index + 1}`;
    const caption = document.createElement('figcaption');
    typeText(caption, `Capture ${index + 1}`, typingOptions());
    const remove = document.createElement('button');
    remove.type = 'button';
    typeText(remove, 'Retirer', typingOptions());
    remove.setAttribute('aria-label', `Retirer la capture ${index + 1}`);
    remove.disabled = busy;
    remove.addEventListener('click', () => removeCapture(index));
    figure.append(image, caption, remove);
    el['capture-gallery'].append(figure);
  });
}

function renderAvatarChoices() {
  el['avatar-gallery'].replaceChildren();
  captures.forEach((capture, index) => {
    const label = document.createElement('label');
    label.className = 'avatar-choice';
    const radio = document.createElement('input');
    radio.type = 'radio';
    radio.name = 'profile-avatar';
    radio.value = String(index);
    radio.checked = avatarIndex === index;
    radio.addEventListener('change', () => {
      avatarIndex = index;
      el['save-profile'].disabled = !canSaveEnrollment(
        captures.length, config.enrollmentMinPhotos, config.enrollmentMaxPhotos, avatarIndex);
    });
    const image = document.createElement('img');
    image.src = capture.avatar;
    image.alt = `Choisir la capture ${index + 1} comme photo de profil`;
    label.append(radio, image);
    el['avatar-gallery'].append(label);
  });
  if (avatarIndex < 0 && captures.length > 0) {
    avatarIndex = 0;
    el['avatar-gallery'].querySelector('input[type="radio"]').checked = true;
  }
  el['save-profile'].disabled = !canSaveEnrollment(
    captures.length, config.enrollmentMinPhotos, config.enrollmentMaxPhotos, avatarIndex);
}

async function captureFace() {
  if (busy || captures.length >= config.enrollmentMaxPhotos) {
    return;
  }
  busy = true;
  typeText(el['capture-message'], 'Analyse de la capture…', typingOptions());
  renderCaptures();
  try {
    const detection = await recognizer.detect(el['enroll-camera']);
    const decision = assessEnrollmentCapture(detection, faces, currentProfileId, config);
    if (decision.status !== 'accepted') {
      typeText(el['capture-message'], ERROR_MESSAGES[decision.status] ?? 'Capture refusée. Réessayez.', typingOptions());
      return;
    }
    captures.push({ descriptor: decision.descriptor, avatar: avatarFromVideo(el['enroll-camera'], detection.box) });
    typeText(el['capture-message'], `Capture ${captures.length} enregistrée temporairement.`, typingOptions());
  } catch (error) {
    typeText(el['capture-message'], error.message || 'Impossible de traiter cette capture.', typingOptions());
  } finally {
    busy = false;
    renderCaptures();
  }
}

function showAvatarStep() {
  if (!canChooseAvatar(captures.length, config.enrollmentMinPhotos)) {
    return;
  }
  avatarIndex = -1;
  renderAvatarChoices();
  el['enrollment-workflow'].hidden = true;
  el['avatar-step'].hidden = false;
  animateMarkedText(el['avatar-step'], typingOptions());
  typeText(el['save-profile'], recaptureMode ? 'Enregistrer mes captures' : 'Créer mon profil', typingOptions());
  setStatus('Choisissez une capture comme photo de profil.');
}

function returnToCaptures() {
  el['avatar-step'].hidden = true;
  el['enrollment-workflow'].hidden = false;
  animateMarkedText(el['enrollment-workflow'], typingOptions());
  setStatus('Vous pouvez retirer ou refaire une capture avant de continuer.');
}

async function saveEnrollment() {
  if (!canSaveEnrollment(captures.length, config.enrollmentMinPhotos,
    config.enrollmentMaxPhotos, avatarIndex) || busy) {
    return;
  }
  const name = el['profile-name'].value.trim();
  if (!recaptureMode && !name) {
    el['profile-name'].focus();
    setStatus('Saisissez un nom pour le profil.');
    return;
  }
  busy = true;
  el['save-profile'].disabled = true;
  const embeddings = captures.map((capture) => capture.descriptor);
  const avatar = captures[avatarIndex].avatar;
  try {
    if (recaptureMode) {
      const replace = document.querySelector('input[name="embedding-mode"]:checked').value === 'replace';
      await api.updateEmbeddings(currentProfileId, embeddings, replace, avatar);
      setStatus('Captures mises à jour. Retour à l’accueil…');
    } else {
      const profile = await api.createProfile(name, embeddings, avatar);
      try {
        await api.login(profile.id);
      } catch (error) {
        setStatus('Profil créé. Retour à l’accueil pour terminer la reconnaissance.');
        stopCamera(stream);
        window.location.assign('/');
        return;
      }
      setStatus('Profil créé. Connexion en cours…');
    }
    stopCamera(stream);
    window.location.assign('/');
  } catch (error) {
    setStatus(error instanceof ApiError ? error.message : 'Enregistrement impossible. Réessayez.');
    busy = false;
    el['save-profile'].disabled = false;
  }
}

async function initialize() {
  try {
    setStatus('Préparation de la caméra…');
    config = await api.config();
    typeText(el['enroll-title'], el['enroll-title'].textContent, typingOptions());
    if (recaptureMode) {
      let session;
      try {
        session = await api.session();
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) {
          throw new ApiError(401, 'Une session de profil est nécessaire pour refaire ses captures.');
        }
        throw error;
      }
      currentProfileId = session.profileId;
      const currentProfile = (await api.faces()).find((face) => face.id === currentProfileId);
      if (!currentProfile) {
        throw new ApiError(404, 'Profil introuvable. Retournez à l’accueil.');
      }
      typeText(el['enroll-title'], `Refaire les captures de ${currentProfile.name}`, typingOptions());
      el['name-field'].hidden = true;
      el['recapture-options'].hidden = false;
    }

    stream = await startCamera(el['enroll-camera'], config.cameraWidth);
    el['asset-loading'].hidden = false;
    setStatus(config.faceAssetsMode === 'cdn'
      ? 'Chargement des modèles depuis le CDN…'
      : 'Chargement des modèles embarqués…');
    recognizer = await createRecognizer(config, setProgress);
    faces = await api.faces();
    el['asset-loading'].hidden = true;
    el['enrollment-workflow'].hidden = false;
    animateMarkedText(el['enrollment-workflow'], typingOptions());
    renderCaptures();
    setStatus('Suivez les consignes, puis capturez entre 5 et 8 images.');
  } catch (error) {
    setStatus(error instanceof CameraError || error instanceof RecognizerError || error instanceof ApiError
      ? error.message
      : 'Impossible de démarrer le parcours de création.');
    console.error(error);
  }
}

el['capture-button'].addEventListener('click', captureFace);
el['choose-avatar'].addEventListener('click', showAvatarStep);
el['back-to-captures'].addEventListener('click', returnToCaptures);
el['save-profile'].addEventListener('click', saveEnrollment);
window.addEventListener('pagehide', () => stopCamera(stream), { once: true });

initialize();
