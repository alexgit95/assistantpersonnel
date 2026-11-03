// Logique pure de la capture a distance et du renouvellement des QR (sans DOM, testable sous Node).
export const ACTIVE_STATES = new Set(['COUNTDOWN', 'CAPTURING']);
export const TERMINAL_STATES = new Set(['DONE', 'CANCELLED', 'EXPIRED']);

const RETRY_AFTER_FAILED_SHOT_MS = 1500;
const QR_RENEWAL_LEAD_MS = 15_000;

/** Consignes de pose, une par capture (la serie recommence si le nombre de photos depasse la liste). */
export const GUIDANCE = [
  'Regardez droit vers la caméra, visage détendu.',
  'Tournez légèrement la tête vers la gauche.',
  'Tournez légèrement la tête vers la droite.',
  'Inclinez légèrement le menton vers le bas.',
  'Relevez légèrement le menton.',
  'Gardez la tête droite et regardez un peu à gauche de l’objectif.',
  'Gardez la tête droite et regardez un peu à droite de l’objectif.',
  'Regardez droit vers la caméra, expression naturelle.',
];

const REJECTION_MESSAGES = {
  none: 'Aucun visage détecté : restez face à la caméra.',
  tooSmall: 'Rapprochez-vous de la caméra.',
  duplicate: 'Ce visage correspond déjà à un profil existant.',
  ambiguous: 'Ressemblance ambiguë avec un profil existant : changez d’éclairage.',
  multiple: 'Plusieurs visages détectés : restez seul devant la caméra.',
  abandoned: 'Capture abandonnée : relancez-la depuis votre téléphone.',
  other: 'Capture refusée, nouvel essai.',
};

const ASSESSMENT_TO_REASON = { none: 'none', tooSmall: 'tooSmall', duplicate: 'duplicate', ambiguous: 'ambiguous' };

export function guidanceFor(index) {
  const safe = Number.isInteger(index) && index >= 0 ? index : 0;
  return GUIDANCE[safe % GUIDANCE.length];
}

export function rejectionMessage(reason) {
  return REJECTION_MESSAGES[reason] ?? REJECTION_MESSAGES.other;
}

/** Motif envoye au serveur pour un resultat de `assessEnrollmentCapture`. */
export function assessmentToReason(status) {
  return ASSESSMENT_TO_REASON[status] ?? 'other';
}

/** Ecart entre l'horloge du serveur et celle du navigateur du Pi (serveur - client). */
export function clockOffset(serverNowMs, clientNowMs) {
  return serverNowMs - clientNowMs;
}

/** Millisecondes avant la prise, en temps serveur reconstitue a partir de l'horloge locale. */
export function msUntilShot(capture, offsetMs, clientNowMs) {
  return capture.nextShotAtMs - (clientNowMs + offsetMs);
}

export function secondsLeft(ms) {
  return Math.max(0, Math.ceil(ms / 1000));
}

/** 'shoot' quand la prise est due, 'wait' pendant le compte a rebours ou un envoi, 'none' hors capture. */
export function nextAction(capture, offsetMs, clientNowMs, { inFlight = false, lastShotAt = 0 } = {}) {
  if (!capture || !ACTIVE_STATES.has(capture.state)) {
    return 'none';
  }
  if (inFlight || msUntilShot(capture, offsetMs, clientNowMs) > 0) {
    return 'wait';
  }
  return clientNowMs - lastShotAt < RETRY_AFTER_FAILED_SHOT_MS ? 'wait' : 'shoot';
}

/** Un QR est renouvele des qu'il est consomme ou expire, et 15 s avant son expiration. */
export function qrNeedsRenewal(entry, serverStatus, clientNowMs, leadMs = QR_RENEWAL_LEAD_MS) {
  if (!entry) {
    return true;
  }
  if (serverStatus === 'CONSUMED' || serverStatus === 'EXPIRED') {
    return true;
  }
  return clientNowMs >= entry.expiresAtClientMs - leadMs;
}

/** Une capture ne se joue sur le Pi que si elle le concerne : creation en invite, recapture du profil connecte. */
export function isActionable(capture, connected, connectedProfileId) {
  if (!capture || !ACTIVE_STATES.has(capture.state)) {
    return false;
  }
  return capture.mode === 'RECAPTURE'
    ? connected && connectedProfileId != null && capture.profileId === connectedProfileId
    : !connected;
}
