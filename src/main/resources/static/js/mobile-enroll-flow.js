// Etapes et messages de la page d'enrolement du smartphone (sans DOM, testable sous Node).
import { guidanceFor, msUntilShot, rejectionMessage, secondsLeft } from './remote-capture.js';

export const POLL_ACTIVE_MS = 1000;
export const POLL_IDLE_MS = 2500;
export const SESSION_LOST_MESSAGE = 'La session de capture a expiré. Scannez à nouveau le QR sur l’écran.';
export const CANCELLED_MESSAGE = 'La capture a été annulée.';
export const TAKEN_MESSAGE = 'Un autre téléphone a déjà lancé la capture.';
export const RECAPTURE_BUSY_MESSAGE = 'Une capture est déjà en cours sur l’écran.';

export function enrollTitle(mode) {
  return mode === 'RECAPTURE' ? 'Refaire mes captures' : 'Créer mon profil';
}

/** Etape d'ecran pour un etat serveur : start | capture | review | done | ended. */
export function enrollStep(state) {
  switch (state) {
    case 'WAITING': return 'start';
    case 'COUNTDOWN':
    case 'CAPTURING': return 'capture';
    case 'REVIEW': return 'review';
    case 'DONE': return 'done';
    default: return 'ended';
  }
}

/** Delai avant le prochain appel de suivi : court pendant la capture, plus lent sinon. */
export function pollDelay(state) {
  return enrollStep(state) === 'capture' ? POLL_ACTIVE_MS : POLL_IDLE_MS;
}

export function progressLabel(done, total) {
  return `${done} / ${total} photos`;
}

/** Texte du compte a rebours ou de la consigne de pose pendant la capture. */
export function captureStatus(snapshot, offsetMs, clientNowMs) {
  const seconds = secondsLeft(msUntilShot(snapshot, offsetMs, clientNowMs));
  const countdown = snapshot.state === 'COUNTDOWN' && snapshot.done === 0
    ? `La première photo est prise dans ${seconds} s. Placez-vous face à l’écran.`
    : (seconds > 0 ? `Prochaine photo dans ${seconds} s.` : 'Photo en cours…');
  return { countdown, guidance: guidanceFor(snapshot.guidanceIndex) };
}

/** Message d'un rejet ou d'un abandon a afficher, ou chaine vide. */
export function rejectionText(snapshot) {
  return snapshot.lastRejection ? rejectionMessage(snapshot.lastRejection) : '';
}

/** Message de fin pour DONE, CANCELLED et EXPIRED. */
export function endMessage(state) {
  if (state === 'DONE') {
    return 'C’est terminé.';
  }
  return state === 'CANCELLED' ? CANCELLED_MESSAGE : SESSION_LOST_MESSAGE;
}
