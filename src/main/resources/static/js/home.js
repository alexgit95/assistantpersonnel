// Ecran d'accueil : camera, reconnaissance locale, session glissante, bascule et deconnexion.
import { api, ApiError } from './api.js';
import { CameraError, startCamera, stopCamera } from './camera.js';
import { classify } from './matcher.js';
import { createRecognizer, RecognizerError } from './recognizer.js';
import { SessionMachine, State } from './session-machine.js';
import { animateMarkedText, typeText } from './typewriter.js';
import { createProfileMenu } from './profile-menu.js';
import { captureKioskKey } from './kiosk-key.js';
import { createKiosk } from './kiosk.js';

captureKioskKey(window.sessionStorage, window.location, window.history);

const FACES_REFRESH_MS = 60_000;
const UNKNOWN_FACES_REFRESH_MS = 10_000;
const MESSAGES = {
  none: 'Aucun visage détecté',
  tooSmall: 'Rapprochez-vous de la caméra',
  unknown: 'Visage non reconnu',
  ambiguous: 'Reconnaissance ambiguë, rapprochez-vous ou changez de lumière',
  matched: 'Visage reconnu',
};

const el = Object.fromEntries(
  ['camera', 'clock', 'status', 'greeting', 'avatar', 'timer', 'logout', 'debug', 'asset-loading',
    'asset-loading-label', 'asset-progress', 'asset-percent', 'unrecognized-help',
    'menu-configuration', 'apps'].map((id) => [id, document.getElementById(id)]),
);
const profileMenu = createProfileMenu();
const dashboardSlots = new Map([...document.querySelectorAll('#apps [data-slot-index]')]
  .map((slot) => [Number(slot.dataset.slotIndex), slot]));

let config;
let machine;
let recognizer;
let cameraStream;
let faces = [];
let facesLoadedAt = 0;
let lastObservation = { status: 'none' };
let appPreferences = [];
let wake = () => {};
let kiosk = null;
let capturePaused = false;

function setStatus(text) {
  typeText(el.status, text, typingOptions());
}

function typingOptions() {
  return { enabled: config?.typingEnabled !== false, speedMs: config?.typingSpeedMs ?? 25 };
}

function updateAssetProgress({ percent, label }) {
  const value = Math.max(0, Math.min(100, Math.round(percent)));
  el['asset-progress'].value = value;
  el['asset-percent'].value = `${value}%`;
  typeText(el['asset-loading-label'], label, typingOptions());
}

function formatClock(totalSeconds) {
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = String(totalSeconds % 60).padStart(2, '0');
  return `${minutes}:${seconds}`;
}

function updateClock() {
  if (el.clock) {
    el.clock.textContent = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' }).format(new Date());
  }
}

function profileFace(id) {
  return faces.find((face) => face.id === id);
}

function render() {
  const now = Date.now();
  const connected = machine.state === State.CONNECTED;
  const face = connected ? profileFace(machine.profileId) : null;

  typeText(el.greeting, connected ? `Bonjour ${face?.name ?? ''}`.trim() : '', typingOptions());
  profileMenu.setProfile(connected && face ? { name: face.name, avatar: face.avatar } : null);
  el.timer.hidden = !connected;
  el.timer.textContent = connected ? `Session : ${formatClock(machine.remainingSeconds(now))}` : '';
  el.logout.hidden = !connected;
  el['unrecognized-help'].hidden = connected;
  animateMarkedText(document, typingOptions());

  if (machine.state === State.PAUSED) {
    setStatus(`Reconnaissance suspendue (${machine.pauseRemainingSeconds(now)} s)`);
  } else if (connected) {
    setStatus('Session ouverte');
  } else {
    setStatus(MESSAGES[lastObservation.status] ?? '');
  }
  renderDebug();
}

function renderDebug() {
  if (!config.recognitionDebug) {
    return;
  }
  const fmt = (value) => (value === null || value === undefined ? '-' : Number(value).toFixed(3));
  el.debug.hidden = false;
  el.debug.textContent = [
    `état : ${machine.state}`,
    `moteur : ${recognizer.backend}`,
    `résultat : ${lastObservation.status}`,
    `distance : ${fmt(lastObservation.distance)} (seuil ${config.matchThreshold})`,
    `écart 2e profil : ${fmt(lastObservation.margin)} (marge ${config.matchMargin})`,
    `traitement : ${lastObservation.timeMs ?? '-'} ms`,
  ].join('\n');
}

async function refreshFaces(maxAgeMs = FACES_REFRESH_MS) {
  if (Date.now() - facesLoadedAt < maxAgeMs) {
    return;
  }
  try {
    faces = await api.faces();
    facesLoadedAt = Date.now();
  } catch (error) {
    console.warn('Liste des visages indisponible', error);
  }
}

async function renderAppWidgets() {
  dashboardSlots.forEach((slot) => slot.replaceChildren());
  const active = appPreferences.filter((app) => app.enabled && dashboardSlots.has(app.position))
    .sort((left, right) => left.position - right.position || left.key.localeCompare(right.key));
  for (const app of active) {
    const section = document.createElement('section');
    section.className = 'app-widget';
    section.dataset.appKey = app.key;
    const heading = document.createElement('h2');
    typeText(heading, app.title, typingOptions());
    const content = document.createElement('div');
    content.className = 'app-widget-content';
    section.append(heading, content);
    dashboardSlots.get(app.position).append(section);
    try {
      content.innerHTML = await api.appFragment(app.key);
      animateMarkedText(content, typingOptions());
      if (app.widgetModule) {
        const widget = await import(app.widgetModule);
        await widget.initialize?.(content, app.configJson, typingOptions(), { slotIndex: app.position });
      }
    } catch (error) {
      typeText(content, error instanceof ApiError ? error.message : 'Application indisponible.', typingOptions());
    }
  }
}

async function loadProfileApps() {
  try {
    appPreferences = await api.apps();
    await renderAppWidgets();
  } catch (error) {
    appPreferences = [];
    await renderAppWidgets();
    console.warn('Préférences des applications indisponibles', error);
  }
}

document.addEventListener('assistant:app-config-updated', (event) => {
  const { key, configJson } = event.detail;
  const app = appPreferences.find((preference) => preference.key === key);
  if (app) {
    app.configJson = configJson;
  }
});

async function observe() {
  const result = await recognizer.detect(el.camera);
  if (result.status !== 'ok') {
    return { status: result.status, timeMs: result.timeMs };
  }
  return { ...classify(result.descriptor, faces, config), timeMs: result.timeMs };
}

async function execute(actions) {
  for (const action of actions) {
    try {
      if (action.type === 'login') {
        await api.login(action.profileId);
        await loadProfileApps();
      } else if (action.type === 'heartbeat') {
        await api.heartbeat();
      } else if (action.type === 'logout' || action.type === 'expired') {
        await api.logout();
        await loadProfileApps();
      }
    } catch (error) {
      handleApiFailure(action, error);
    }
  }
  if (actions.some((action) => ['login', 'logout', 'expired'].includes(action.type))) {
    kiosk?.poke();
  }
}

function handleApiFailure(action, error) {
  if (action.type === 'login') {
    machine.loginFailed();
    refreshFaces(0);
    setStatus(error instanceof ApiError ? error.message : 'Connexion impossible.');
  } else if (action.type === 'heartbeat' && error instanceof ApiError && error.status === 401) {
    machine.sessionLost();
  } else {
    console.warn('Appel API en échec', action, error);
  }
}

function sleep(ms) {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    wake = () => {
      clearTimeout(timer);
      resolve();
    };
  });
}

async function loop() {
  for (;;) {
    await execute(machine.tick(Date.now()));
    if (machine.state !== State.PAUSED && !document.hidden && !capturePaused) {
      await refreshFaces();
      lastObservation = await observe();
      await execute(machine.observe(lastObservation, Date.now()));
      if (lastObservation.status === 'unknown') {
        await refreshFaces(UNKNOWN_FACES_REFRESH_MS);
      }
    }
    render();
    await sleep(machine.intervalMs(Date.now()));
  }
}

async function restoreSession() {
  try {
    const session = await api.session();
    machine.restore(session.profileId, session.remainingSeconds, Date.now());
    await loadProfileApps();
  } catch (error) {
    if (!(error instanceof ApiError && error.status === 401)) {
      console.warn('Session non restaurée', error);
    }
    // Sans session, le serveur répond avec les widgets de l'invité.
    await loadProfileApps();
  }
}

async function start() {
  try {
    updateClock();
    window.setInterval(updateClock, 30_000);
    setStatus('Chargement…');
    config = await api.config();
    document.documentElement.style.setProperty('--qr-size', `${config.qrSizeRem}rem`);
    machine = new SessionMachine(config);
    cameraStream = await startCamera(el.camera, config.cameraWidth);
    el['asset-loading'].hidden = false;
    setStatus(config.faceAssetsMode === 'cdn'
      ? 'Chargement des modèles depuis le CDN…'
      : 'Chargement des modèles embarqués…');
    recognizer = await createRecognizer(config, updateAssetProgress);
    window.setTimeout(() => { el['asset-loading'].hidden = true; }, 1200);
    await refreshFaces(0);
    await restoreSession();
    render();
    kiosk = createKiosk({
      getConfig: () => config,
      isConnected: () => machine.state === State.CONNECTED,
      connectedProfileId: () => machine.profileId,
      detect: () => recognizer.detect(el.camera),
      getVideo: () => el.camera,
      getFaces: () => faces,
      refreshFaces,
      setPaused: (paused) => {
        capturePaused = paused;
        if (!paused) {
          wake();
        }
      },
      elements: {
        panel: document.getElementById('qr-panel'),
        link: document.getElementById('qr-link'),
        image: document.getElementById('qr-image'),
        overlay: document.getElementById('capture-overlay'),
        title: document.getElementById('capture-title'),
        count: document.getElementById('capture-count'),
        hint: document.getElementById('capture-hint'),
        progress: document.getElementById('capture-progress'),
        message: document.getElementById('capture-message'),
      },
    });
    kiosk.start();
  } catch (error) {
    stopCamera(cameraStream);
    cameraStream = null;
    setStatus(error instanceof CameraError || error instanceof RecognizerError || error instanceof ApiError
      ? error.message
      : 'Démarrage impossible.');
    if (error instanceof RecognizerError) {
      el['asset-loading-label'].textContent = error.message;
    }
    console.error(error);
    return;
  }

  el.logout.addEventListener('click', async () => {
    await execute(machine.logout(Date.now()));
    render();
    wake();
  });
  document.addEventListener('visibilitychange', () => wake());
  window.addEventListener('pagehide', () => stopCamera(cameraStream), { once: true });
  setInterval(render, 1000);
  loop();
}

start();
