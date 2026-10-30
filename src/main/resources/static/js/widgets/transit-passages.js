import { api, ApiError } from '../api.js';

// Un appel par minute tant que le widget est affiche (donc session faciale ouverte).
export const REFRESH_MS = 60 * 1000;

export function formatMinutes(minutes) {
  return minutes <= 0 ? "À l'approche" : `dans ${minutes} min`;
}

export function formatPassagesUpdatedAt(value) {
  if (!value) return '';
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) return '';
  return `Mis à jour le ${new Intl.DateTimeFormat('fr-FR', {
    timeZone: 'Europe/Paris', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(instant)}`;
}

function createStopEntry(entry) {
  const item = document.createElement('li');
  item.className = 'passages-stop';
  item.dataset.state = entry.state;
  const heading = document.createElement('h3');
  heading.className = 'passages-stop-heading';
  heading.textContent = [entry.stop.name, entry.stop.town].filter(Boolean).join(' · ');
  item.append(heading);
  if (entry.message) {
    const message = document.createElement('p');
    message.className = 'passages-stop-message';
    message.textContent = entry.message;
    item.append(message);
  }
  if (entry.passages.length > 0) {
    const list = document.createElement('ul');
    list.className = 'passages-list';
    for (const passage of entry.passages) {
      const row = document.createElement('li');
      row.className = 'passage';
      const line = document.createElement('span');
      line.className = 'passage-line';
      line.textContent = passage.line;
      if (/^[0-9a-f]{6}$/i.test(passage.lineColor || '')) {
        line.style.setProperty('--line-color', `#${passage.lineColor}`);
      }
      const destination = document.createElement('span');
      destination.className = 'passage-destination';
      destination.textContent = passage.destination;
      const minutes = document.createElement('strong');
      minutes.className = 'passage-minutes';
      minutes.textContent = formatMinutes(passage.minutes);
      row.append(line, destination, minutes);
      list.append(row);
    }
    item.append(list);
  }
  if (entry.updatedAt) {
    const updated = document.createElement('p');
    updated.className = 'passages-updated';
    updated.textContent = formatPassagesUpdatedAt(entry.updatedAt);
    item.append(updated);
  }
  return item;
}

export async function initialize(container, _configJson, _typingOptions = {}, dependencies = {}) {
  const root = container.querySelector('[data-passages-widget]');
  if (!root) return;
  const status = root.querySelector('[data-passages-status]');
  const stops = root.querySelector('[data-passages-stops]');
  const testBadge = root.querySelector('[data-external-test-mode]');
  if (testBadge) testBadge.hidden = root.dataset.testMode !== 'true';
  const setPoll = dependencies.setInterval ?? ((callback, delay) => window.setInterval(callback, delay));
  const clearPoll = dependencies.clearInterval ?? ((timer) => window.clearInterval(timer));
  let refreshTimer = null;
  let disposed = false;
  let rendered = false;

  const dispose = () => {
    disposed = true;
    if (refreshTimer !== null) clearPoll(refreshTimer);
    refreshTimer = null;
  };

  const render = (overview) => {
    if (testBadge) testBadge.hidden = !overview.testMode;
    stops.replaceChildren(...overview.stops.map(createStopEntry));
    const notice = overview.notice
      || (overview.stops.length === 0 ? 'Aucun arrêt sélectionné : choisissez-en dans la configuration.' : '');
    status.hidden = !notice;
    status.textContent = notice;
    rendered = true;
  };

  const load = async () => {
    if (!root.isConnected) {
      dispose();
      return;
    }
    try {
      render(await (dependencies.api ?? api).transitPassages());
    } catch (error) {
      if (error instanceof ApiError && error.status === 401) dispose();
      if (rendered) return;
      status.hidden = false;
      status.textContent = error instanceof ApiError
        ? error.message : 'Les prochains passages sont momentanément indisponibles.';
    }
  };

  await load();
  if (!disposed) refreshTimer = setPoll(load, REFRESH_MS);
}
