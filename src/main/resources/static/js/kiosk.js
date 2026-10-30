// Pilotage du Pi : QR menu toujours affiche, capture a distance commandee depuis le smartphone.
import { api, ApiError } from './api.js';
import { avatarFromVideo } from './avatar.js';
import { assessEnrollmentCapture } from './enrollment-policy.js';
import { loadQrLibrary, buildQr, qrSvg } from './qr-code.js';
import {
  assessmentToReason, clockOffset, guidanceFor, isActionable, msUntilShot, nextAction, qrNeedsRenewal,
  rejectionMessage, secondsLeft, TERMINAL_STATES,
} from './remote-capture.js';

const QR_RETRY_AFTER_UNAVAILABLE_MS = 60_000;
const OVERLAY_REFRESH_MS = 200;

/**
 * deps : getConfig, isConnected, connectedProfileId, detect (detection sur la camera), getVideo, getFaces,
 * refreshFaces, setPaused et elements : panel, link, image (QR) puis overlay, count, title, hint,
 * progress, message (panneau de guidage plein ecran).
 */
export function createKiosk(deps) {
  const { elements: el } = deps;
  let timer = null;
  let overlayTimer = null;
  let guest = null;
  let connectedQr = null;
  let shownUrl = null;
  let capture = null;
  let offset = 0;
  let inFlight = false;
  let lastShotAt = 0;
  let capturing = false;
  let qrRetryAt = 0;

  const config = () => deps.getConfig();

  function schedule(delayMs) {
    clearTimeout(timer);
    timer = setTimeout(tick, delayMs ?? (capturing ? config().kioskPollActiveMs : config().kioskPollIdleMs));
  }

  async function tick() {
    try {
      if (!document.hidden) {
        const connected = deps.isConnected();
        const state = await api.kioskState(connected && connectedQr ? [connectedQr.qrId] : []);
        offset = clockOffset(state.serverNowMs, Date.now());
        capture = state.capture;
        await syncQr(connected, state);
        syncCapture(connected);
      }
    } catch (error) {
      console.warn('État du kiosque indisponible', error);
    }
    schedule();
  }

  async function syncQr(connected, state) {
    if (connected) {
      if (guest) {
        await leaveGuest();
      }
      const status = state.qr.find((entry) => entry.qrId === connectedQr?.qrId)?.state;
      if (qrNeedsRenewal(connectedQr, status, Date.now())) {
        await issueConnectedQr();
      }
      return;
    }
    connectedQr = null;
    const current = state.capture;
    if (!guest || !current || current.id !== guest.id || TERMINAL_STATES.has(current.state)) {
      await openGuestSession();
    }
  }

  async function openGuestSession() {
    try {
      const opened = await api.openEnrollmentSession();
      guest = { id: opened.id, mode: opened.mode };
      await showQr(opened.qrUrl ?? null);
    } catch (error) {
      console.warn('Session de capture indisponible', error);
    }
  }

  /** Un profil connu vient d'etre reconnu : la session d'enrolement en attente n'a plus lieu d'etre. */
  async function leaveGuest() {
    const id = guest.id;
    guest = null;
    if (capture?.id === id && capture.state === 'WAITING') {
      try {
        await api.cancelEnrollmentSession(id);
      } catch (error) {
        console.warn('Annulation de la session de capture impossible', error);
      }
    }
    await showQr(null);
  }

  async function issueConnectedQr() {
    if (Date.now() < qrRetryAt) {
      return;
    }
    try {
      const issued = await api.issueHandoff('FULL');
      connectedQr = { qrId: issued.qrId, url: issued.url, expiresAtClientMs: Date.now() + issued.expiresInSeconds * 1000 };
      await showQr(issued.url);
    } catch (error) {
      connectedQr = null;
      if (error instanceof ApiError && error.status === 503) {
        qrRetryAt = Date.now() + QR_RETRY_AFTER_UNAVAILABLE_MS;
        await showQr(null);
      } else if (!(error instanceof ApiError && error.status === 401)) {
        console.warn('QR de transfert indisponible', error);
      }
    }
  }

  async function showQr(url) {
    if (url !== shownUrl) {
      shownUrl = url;
      if (url) {
        const library = await loadQrLibrary();
        el.image.innerHTML = qrSvg(buildQr(library, url), { label: 'QR code d’accès depuis un téléphone' });
        if (config().qrDevMode) {
          el.link.href = url;
          el.link.target = '_blank';
          el.link.rel = 'noopener';
          el.link.title = `Simuler le scan : ${url}`;
        }
      } else {
        el.image.replaceChildren();
        el.link.removeAttribute('href');
        el.link.removeAttribute('title');
      }
    }
    el.panel.hidden = !url || capturing;
  }

  function syncCapture(connected) {
    const active = isActionable(capture, connected, deps.connectedProfileId());
    if (active && !capturing) {
      enterCapture();
    } else if (!active && capturing) {
      exitCapture();
    }
  }

  function enterCapture() {
    capturing = true;
    lastShotAt = 0;
    deps.setPaused(true);
    el.panel.hidden = true;
    el.overlay.hidden = false;
    deps.refreshFaces(0);
    updateOverlay();
    overlayTimer = setInterval(step, OVERLAY_REFRESH_MS);
    schedule();
  }

  function exitCapture() {
    capturing = false;
    clearInterval(overlayTimer);
    overlayTimer = null;
    el.overlay.hidden = true;
    el.panel.hidden = !shownUrl;
    deps.setPaused(false);
    deps.refreshFaces(0);
    schedule(0);
  }

  function updateOverlay() {
    if (!capture) {
      return;
    }
    const ms = msUntilShot(capture, offset, Date.now());
    el.count.textContent = ms > 0 ? String(secondsLeft(ms)) : '…';
    el.title.textContent = capture.state === 'COUNTDOWN' ? 'Préparez-vous' : 'Restez face à la caméra';
    el.hint.textContent = guidanceFor(capture.guidanceIndex);
    el.progress.textContent = `${capture.done} / ${capture.total}`;
    el.message.textContent = capture.lastRejection ? rejectionMessage(capture.lastRejection) : '';
  }

  function step() {
    updateOverlay();
    if (nextAction(capture, offset, Date.now(), { inFlight, lastShotAt }) === 'shoot') {
      shoot();
    }
  }

  async function shoot() {
    inFlight = true;
    lastShotAt = Date.now();
    const current = capture;
    try {
      const snapshot = await takeShot(current);
      offset = clockOffset(snapshot.serverNowMs, Date.now());
      capture = snapshot;
      syncCapture(deps.isConnected());
    } catch (error) {
      console.warn('Prise de vue en échec', error);
      if (error instanceof ApiError && (error.status === 404 || error.status === 409 || error.status === 403)) {
        schedule(0);
      }
    } finally {
      inFlight = false;
    }
  }

  async function takeShot(current) {
    let detection;
    try {
      detection = await deps.detect();
    } catch (error) {
      return api.submitCapture(current.id, { status: 'REJECTED', reason: 'other' });
    }
    const profileId = current.mode === 'RECAPTURE' ? deps.connectedProfileId() : null;
    const decision = assessEnrollmentCapture(detection, deps.getFaces(), profileId, config());
    if (decision.status !== 'accepted') {
      return api.submitCapture(current.id, { status: 'REJECTED', reason: assessmentToReason(decision.status) });
    }
    return api.submitCapture(current.id, {
      status: 'ACCEPTED',
      embedding: Array.from(decision.descriptor),
      avatar: avatarFromVideo(deps.getVideo(), detection.box),
    });
  }

  return {
    start() {
      schedule(0);
    },
    /** Reveille le poll apres un changement d'etat local (connexion, deconnexion). */
    poke() {
      schedule(0);
    },
    get capturing() {
      return capturing;
    },
  };
}
