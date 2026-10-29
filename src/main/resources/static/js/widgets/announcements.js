import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';
import { nextRetypeDelay } from '../retype.js';

export { nextRetypeDelay };

const PARIS = 'Europe/Paris';
const RELOAD_MS = 60000;
const MS_PER_SECOND = 1000;

const parisFormat = new Intl.DateTimeFormat('en-GB', {
  timeZone: PARIS, hourCycle: 'h23', year: 'numeric', month: 'numeric', day: 'numeric',
  hour: 'numeric', minute: 'numeric', second: 'numeric',
});

const endDateFormat = new Intl.DateTimeFormat('fr-FR', {
  timeZone: PARIS, day: '2-digit', month: '2-digit', year: 'numeric',
});
const endTimeFormat = new Intl.DateTimeFormat('fr-FR', { timeZone: PARIS, hour: '2-digit', minute: '2-digit' });

/** Texte ecrit : titre seul avec compte a rebours, sinon titre suivi de la date de fin (heure de Paris). */
export function announcementLabel(data) {
  if (data.countdown) {
    return data.title;
  }
  const end = new Date(data.endsAt);
  return `${data.title} \u00b7 ${endDateFormat.format(end)} \u00e0 ${endTimeFormat.format(end)}`;
}

/** Avancement entre debut et fin, borne a [0, 1]. */
export function countdownProgress(nowMs, startMs, endMs) {
  if (endMs <= startMs) {
    return 1;
  }
  return Math.min(1, Math.max(0, (nowMs - startMs) / (endMs - startMs)));
}

function parisFields(instantMs) {
  const fields = {};
  for (const { type, value } of parisFormat.formatToParts(new Date(instantMs))) {
    fields[type] = Number(value);
  }
  return { year: fields.year, month: fields.month, day: fields.day, hour: fields.hour,
    minute: fields.minute, second: fields.second };
}

function fieldsAsUtc(fields) {
  return Date.UTC(fields.year, fields.month - 1, fields.day, fields.hour, fields.minute, fields.second);
}

function parisOffsetMs(instantMs) {
  return fieldsAsUtc(parisFields(instantMs)) - Math.floor(instantMs / MS_PER_SECOND) * MS_PER_SECOND;
}

function parisToInstant(fields) {
  const guess = fieldsAsUtc(fields);
  return guess - parisOffsetMs(guess - parisOffsetMs(guess));
}

function addMonths(fields, months) {
  const index = fields.year * 12 + (fields.month - 1) + months;
  const year = Math.floor(index / 12);
  const month = (index % 12) + 1;
  const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
  return { ...fields, year, month, day: Math.min(fields.day, lastDay) };
}

/** Temps restant calendaire (mois civils en heure de Paris) ou null si la fin est atteinte. */
export function countdownParts(nowMs, endMs) {
  if (endMs <= nowMs) {
    return null;
  }
  const start = parisFields(nowMs);
  const end = parisFields(endMs);
  let months = (end.year - start.year) * 12 + (end.month - start.month);
  while (months > 0 && parisToInstant(addMonths(start, months)) > endMs) {
    months -= 1;
  }
  const base = months > 0 ? parisToInstant(addMonths(start, months)) : nowMs;
  let seconds = Math.floor((endMs - base) / MS_PER_SECOND);
  const days = Math.floor(seconds / 86400);
  seconds -= days * 86400;
  const hours = Math.floor(seconds / 3600);
  seconds -= hours * 3600;
  const minutes = Math.floor(seconds / 60);
  return { months, days, hours, minutes, seconds: seconds - minutes * 60 };
}

/** Masque les unites de tete nulles ; les suivantes restent affichees. */
export function formatCountdown(parts) {
  const units = [[parts.months, 'mois'], [parts.days, 'j'], [parts.hours, 'h'], [parts.minutes, 'min'],
    [parts.seconds, 's']];
  const first = units.findIndex(([value]) => value > 0);
  return units.slice(first === -1 ? units.length - 1 : first).map(([value, label]) => `${value} ${label}`).join(' ');
}

export async function initialize(container, _configJson, typingOptions = {}) {
  const root = container.querySelector('[data-announcements-widget]');
  if (!root) return;
  const list = root.querySelector('[data-announcements-list]');
  const status = root.querySelector('[data-announcements-status]');
  const empty = root.querySelector('[data-announcements-empty]');
  const items = new Map();
  let offsetMs = 0;
  let tickTimer = null;
  let reloadTimer = null;
  const retypeEnabled = typingOptions.enabled !== false;

  const dispose = () => {
    window.clearInterval(tickTimer);
    window.clearInterval(reloadTimer);
    items.forEach((item) => window.clearTimeout(item.retypeTimer));
    items.clear();
  };

  const scheduleRetype = (item) => {
    if (!retypeEnabled) return;
    item.retypeTimer = window.setTimeout(() => {
      typeText(item.title, announcementLabel(item.data), { ...typingOptions, restart: true });
      scheduleRetype(item);
    }, nextRetypeDelay());
  };

  const removeItem = (id) => {
    const item = items.get(id);
    if (!item) return;
    window.clearTimeout(item.retypeTimer);
    item.element.remove();
    items.delete(id);
  };

  const refreshEmpty = () => {
    const wasHidden = empty.hidden;
    empty.hidden = items.size > 0;
    if (wasHidden && !empty.hidden) typeText(empty, 'Aucune annonce.', typingOptions);
  };

  const createItem = (data) => {
    const element = document.createElement('li');
    element.className = 'announcement-entry';
    const title = document.createElement('p');
    title.className = 'announcement-title announcement-idle-caret';
    element.append(title);
    const item = { data, element, title, countdown: null, retypeTimer: null };
    if (data.countdown) {
      item.countdown = document.createElement('p');
      item.countdown.className = 'announcement-countdown';
      item.countdown.setAttribute('role', 'timer');
      item.progress = document.createElement('div');
      item.progress.className = 'announcement-progress';
      item.progress.setAttribute('role', 'progressbar');
      item.progress.setAttribute('aria-label', 'Progression jusqu\u2019à l\u2019échéance');
      item.progress.setAttribute('aria-valuemin', '0');
      item.progress.setAttribute('aria-valuemax', '100');
      item.progressFill = document.createElement('div');
      item.progressFill.className = 'announcement-progress-fill';
      item.progress.append(item.progressFill);
      element.append(item.countdown, item.progress);
    }
    items.set(data.id, item);
    typeText(title, announcementLabel(data), typingOptions);
    scheduleRetype(item);
    return item;
  };

  const tick = () => {
    if (!root.isConnected) {
      dispose();
      return;
    }
    const now = Date.now() + offsetMs;
    for (const [id, item] of [...items]) {
      const parts = countdownParts(now, item.data.endsAt);
      if (!parts) {
        removeItem(id);
      } else if (item.countdown) {
        item.countdown.textContent = formatCountdown(parts);
        const percent = Math.round(countdownProgress(now, item.data.startsAt, item.data.endsAt) * 1000) / 10;
        item.progressFill.style.width = `${percent}%`;
        item.progress.setAttribute('aria-valuenow', String(Math.round(percent)));
      }
    }
    refreshEmpty();
  };

  const load = async () => {
    try {
      const payload = await api.announcements();
      offsetMs = payload.serverNow - Date.now();
      status.hidden = true;
      const incoming = new Set(payload.announcements.map((entry) => entry.id));
      [...items.keys()].filter((id) => !incoming.has(id)).forEach(removeItem);
      for (const entry of payload.announcements) {
        const item = items.get(entry.id) ?? createItem(entry);
        const previous = announcementLabel(item.data);
        item.data = entry;
        if (previous !== announcementLabel(entry)) typeText(item.title, announcementLabel(entry), typingOptions);
        list.append(item.element);
      }
      tick();
    } catch (error) {
      status.hidden = false;
      typeText(status, error instanceof ApiError ? error.message : 'Les annonces sont momentanément indisponibles.',
        typingOptions);
    }
  };

  await load();
  tickTimer = window.setInterval(tick, MS_PER_SECOND);
  reloadTimer = window.setInterval(load, RELOAD_MS);
}
