import { api, ApiError } from './api.js';
import {
  capturePairing, clearEnrollment, clearPairing, readEnrollment, saveEnrollment, SCAN_PROMPT,
} from './mobile-menu.js';
import {
  captureStatus, CANCELLED_MESSAGE, enrollStep, enrollTitle, endMessage, pollDelay, progressLabel, rejectionText,
  SESSION_LOST_MESSAGE, TAKEN_MESSAGE,
} from './mobile-enroll-flow.js';
import { clockOffset } from './remote-capture.js';

const storage = window.sessionStorage;
const byId = (id) => document.getElementById(id);
const message = byId('enroll-message');
const steps = { start: byId('step-start'), capture: byId('step-capture'), review: byId('step-review') };
const endLink = byId('enroll-end-link');

let sessionId = null;
let tokens = {};
let snapshot = null;
let offsetMs = 0;
let avatarsLoaded = false;
let selectedAvatar = 0;
let timer = null;
let countdownTimer = null;

function show(step, text = '') {
  for (const [name, section] of Object.entries(steps)) {
    section.hidden = name !== step;
  }
  message.textContent = text;
  message.classList.remove('mobile-error');
  endLink.hidden = step !== null && step !== undefined;
}

function showError(text) {
  message.textContent = text;
  message.classList.add('mobile-error');
}

function stopTimers() {
  clearTimeout(timer);
  clearInterval(countdownTimer);
  timer = null;
  countdownTimer = null;
}

function finish(text) {
  stopTimers();
  clearEnrollment(storage);
  show(null, text);
}

function renderCountdown() {
  if (!snapshot || enrollStep(snapshot.state) !== 'capture') {
    return;
  }
  const status = captureStatus(snapshot, offsetMs, Date.now());
  byId('capture-countdown').textContent = status.countdown;
  byId('capture-guidance').textContent = status.guidance;
}

function renderSnapshot() {
  if (snapshot.claimed && !tokens.claim) {
    finish(TAKEN_MESSAGE);
    return;
  }
  const recapture = snapshot.mode === 'RECAPTURE';
  byId('enroll-title').textContent = enrollTitle(snapshot.mode);
  byId('name-field').hidden = recapture;
  byId('recapture-options').hidden = !recapture;
  const step = enrollStep(snapshot.state);
  if (step === 'start') {
    show('start', rejectionText(snapshot));
    if (snapshot.lastRejection) {
      message.classList.add('mobile-error');
    }
    if (snapshot.name && !byId('profile-name').value) {
      byId('profile-name').value = snapshot.name;
    }
  } else if (step === 'capture') {
    show('capture', rejectionText(snapshot));
    if (snapshot.lastRejection) {
      message.classList.add('mobile-error');
    }
    byId('capture-progress').textContent = progressLabel(snapshot.done, snapshot.total);
    byId('capture-bar').max = snapshot.total;
    byId('capture-bar').value = snapshot.done;
    renderCountdown();
  } else if (step === 'review') {
    show('review');
    loadAvatars();
  } else {
    finish(endMessage(snapshot.state));
  }
}

async function loadAvatars() {
  if (avatarsLoaded) {
    return;
  }
  avatarsLoaded = true;
  try {
    const { avatars } = await api.enrollmentAvatars(sessionId, tokens);
    selectedAvatar = 0;
    const gallery = byId('avatar-gallery');
    gallery.replaceChildren(...avatars.map((src, index) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.setAttribute('role', 'radio');
      button.setAttribute('aria-checked', String(index === 0));
      button.setAttribute('aria-label', `Photo ${index + 1}`);
      const image = document.createElement('img');
      image.alt = '';
      image.src = src;
      button.append(image);
      button.addEventListener('click', () => {
        selectedAvatar = index;
        for (const other of gallery.children) {
          other.setAttribute('aria-checked', String(other === button));
        }
      });
      return button;
    }));
  } catch (error) {
    avatarsLoaded = false;
    handleFailure(error);
  }
}

function handleFailure(error) {
  if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
    clearPairing(storage);
    finish(SESSION_LOST_MESSAGE);
    return;
  }
  showError(error instanceof ApiError ? error.message : 'Le serveur est injoignable. Réessayez.');
}

async function poll() {
  try {
    const received = await api.enrollmentState(sessionId, tokens);
    offsetMs = clockOffset(received.serverNowMs, Date.now());
    if (snapshot && snapshot.state !== 'REVIEW' && received.state === 'REVIEW') {
      avatarsLoaded = false;
    }
    snapshot = received;
    renderSnapshot();
    if (['DONE', 'CANCELLED', 'EXPIRED'].includes(snapshot.state) || (snapshot.claimed && !tokens.claim)) {
      return;
    }
  } catch (error) {
    handleFailure(error);
    if (error instanceof ApiError && [401, 403, 404].includes(error.status)) {
      return;
    }
  }
  timer = setTimeout(poll, snapshot ? pollDelay(snapshot.state) : 2500);
}

function beginPolling() {
  stopTimers();
  countdownTimer = setInterval(renderCountdown, 250);
  poll();
}

async function run(action) {
  try {
    await action();
  } catch (error) {
    handleFailure(error);
  }
}

byId('start-button').addEventListener('click', () => run(async () => {
  const recapture = snapshot?.mode === 'RECAPTURE';
  const name = recapture ? undefined : byId('profile-name').value.trim();
  if (!recapture && !name) {
    showError('Saisissez un nom pour votre profil.');
    return;
  }
  try {
    const result = await api.enrollmentStart(sessionId, tokens, name);
    if (result.claimToken) {
      tokens = { claim: result.claimToken };
      saveEnrollment(storage, sessionId, result.claimToken);
      clearPairing(storage);
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 409) {
      showError(TAKEN_MESSAGE);
      return;
    }
    if (error instanceof ApiError && error.status === 400) {
      showError(error.message);
      return;
    }
    throw error;
  }
  beginPolling();
}));

async function cancel() {
  await api.enrollmentCancel(sessionId, tokens).catch(() => {});
  finish(CANCELLED_MESSAGE);
}

byId('capture-cancel').addEventListener('click', () => run(cancel));
byId('review-cancel').addEventListener('click', () => run(cancel));

byId('review-restart').addEventListener('click', () => run(async () => {
  await api.enrollmentRestart(sessionId, tokens);
  avatarsLoaded = false;
  beginPolling();
}));

byId('review-confirm').addEventListener('click', () => run(async () => {
  byId('review-confirm').disabled = true;
  try {
    const replace = snapshot?.mode === 'RECAPTURE'
      && document.querySelector('input[name="embedding-mode"]:checked')?.value === 'replace';
    await api.enrollmentConfirm(sessionId, tokens, selectedAvatar, replace);
  } catch (error) {
    byId('review-confirm').disabled = false;
    if (error instanceof ApiError && [400, 409].includes(error.status)) {
      showError(error.message);
      return;
    }
    throw error;
  }
  stopTimers();
  clearEnrollment(storage);
  clearPairing(storage);
  window.location.replace('/mobile');
}));

async function start() {
  const pairing = capturePairing(storage, window.location, window.history);
  const saved = readEnrollment(storage);
  try {
    if (saved) {
      snapshot = await api.enrollmentState(saved.id, { claim: saved.claim }).catch((error) => {
        if (error instanceof ApiError) {
          clearEnrollment(storage);
          return null;
        }
        throw error;
      });
      if (snapshot) {
        sessionId = saved.id;
        tokens = { claim: saved.claim };
      }
    }
    if (!snapshot && pairing) {
      snapshot = await api.enrollmentByPairing(pairing);
      sessionId = snapshot.id;
      tokens = { pairing };
    }
  } catch (error) {
    if (error instanceof ApiError && error.status === 404) {
      clearPairing(storage);
    } else if (!(error instanceof ApiError)) {
      showError('Le serveur est injoignable. Réessayez.');
      return;
    }
    snapshot = null;
  }
  if (!snapshot) {
    show(null, pairing ? SESSION_LOST_MESSAGE : SCAN_PROMPT);
    return;
  }
  offsetMs = clockOffset(snapshot.serverNowMs, Date.now());
  renderSnapshot();
  if (snapshot.claimed && !tokens.claim) {
    return;
  }
  if (enrollStep(snapshot.state) !== 'start' || tokens.claim) {
    beginPolling();
  } else {
    countdownTimer = null;
    timer = setTimeout(poll, pollDelay(snapshot.state));
  }
}

start();
