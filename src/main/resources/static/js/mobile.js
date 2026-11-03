import { api, ApiError } from './api.js';
import {
  capturePairing, clearPairing, CONNECTED_STORAGE_KEY, menuFor, saveEnrollment,
} from './mobile-menu.js';
import { RECAPTURE_BUSY_MESSAGE } from './mobile-enroll-flow.js';

const note = document.getElementById('mobile-note');
const menu = document.getElementById('mobile-menu');

async function loadSession() {
  try {
    return await api.session();
  } catch (error) {
    if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
      return null;
    }
    throw error;
  }
}

async function pairingIsValid(token) {
  if (!token) {
    return false;
  }
  try {
    await api.enrollmentByPairing(token);
    return true;
  } catch (error) {
    if (error instanceof ApiError) {
      clearPairing(window.sessionStorage);
      return false;
    }
    throw error;
  }
}

const ACTIONS = {
  async logout() {
    await api.logout().catch(() => {});
    window.sessionStorage.removeItem(CONNECTED_STORAGE_KEY);
    window.location.reload();
  },
  async recapture() {
    try {
      const created = await api.recapture();
      saveEnrollment(window.sessionStorage, created.id, created.claimToken);
      window.location.assign('/mobile/enroll');
    } catch (error) {
      if (error instanceof ApiError && error.status === 409) {
        note.textContent = RECAPTURE_BUSY_MESSAGE;
      } else if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
        window.location.reload();
      } else {
        note.textContent = 'Le serveur est injoignable. Réessayez.';
      }
    }
  },
};

function render(model) {
  note.textContent = model.note;
  menu.replaceChildren(...model.items.map((item) => {
    if (item.href) {
      const link = document.createElement('a');
      link.href = item.href;
      link.textContent = item.label;
      return link;
    }
    const button = document.createElement('button');
    button.type = 'button';
    button.textContent = item.label;
    button.addEventListener('click', async () => {
      button.disabled = true;
      await ACTIONS[item.action]();
      button.disabled = false;
    });
    return button;
  }));
}

async function start() {
  const pairing = capturePairing(window.sessionStorage, window.location, window.history);
  try {
    const [pairingValid, session] = await Promise.all([pairingIsValid(pairing), loadSession()]);
    const wasConnected = window.sessionStorage.getItem(CONNECTED_STORAGE_KEY) === '1';
    if (session) {
      window.sessionStorage.setItem(CONNECTED_STORAGE_KEY, '1');
    } else {
      window.sessionStorage.removeItem(CONNECTED_STORAGE_KEY);
    }
    render(menuFor({ pairingValid, session, wasConnected }));
  } catch {
    note.textContent = 'Le serveur est injoignable. Réessayez dans un instant.';
    menu.replaceChildren();
  }
}

start();
