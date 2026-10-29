import { api, ApiError } from '../api.js';
import { startRetypeLoop } from '../retype.js';
import { typeText } from '../typewriter.js';

export const REFRESH_MS = 60 * 1000;

export function formatTransitUpdatedAt(value) {
  if (!value) return '';
  const instant = new Date(value);
  if (Number.isNaN(instant.getTime())) return '';
  return `Mis à jour le ${new Intl.DateTimeFormat('fr-FR', {
    timeZone: 'Europe/Paris', day: '2-digit', month: '2-digit', year: 'numeric',
    hour: '2-digit', minute: '2-digit',
  }).format(instant)}`;
}

export function retypeTransitText(element, text, typingOptions, loopOptions = {}) {
  if (typingOptions.enabled === false) {
    element.classList.remove('announcement-idle-caret');
  } else {
    element.classList.add('announcement-idle-caret');
  }
  typeText(element, text, typingOptions);
  if (typingOptions.enabled === false) return null;
  return startRetypeLoop({
    ...loopOptions,
    run: () => typeText(element, text, { ...typingOptions, restart: true }),
  });
}

export function providerMessageText(markup, parser = new DOMParser()) {
  if (!markup) return '';
  const parsed = parser.parseFromString(String(markup), 'text/html');
  parsed.body.querySelectorAll('script, style, iframe, object, embed, svg, math, template')
    .forEach((node) => node.remove());
  parsed.body.querySelectorAll('br').forEach((node) => node.replaceWith(parsed.createTextNode('\n')));
  parsed.body.querySelectorAll('p, li').forEach((node) => node.append(parsed.createTextNode('\n')));
  return parsed.body.textContent
    .replace(/[\t\f\v ]+\n/g, '\n')
    .replace(/\n[\t\f\v ]+/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim();
}

function stopLoops(loops) {
  loops.forEach((loop) => loop.stop());
  loops.clear();
}

function createLineEntry(line, alerts, hasData, typingOptions, loops, root) {
  const item = document.createElement('li');
  item.className = 'transit-line';
  item.dataset.disrupted = String(alerts.length > 0);
  item.dataset.severity = alerts.some((alert) => alert.severity === 'BLOQUANTE')
    ? 'BLOQUANTE' : alerts.length > 0 ? 'PERTURBEE' : 'NORMAL';
  const heading = document.createElement('h3');
  heading.className = 'transit-line-heading';
  heading.textContent = line.label;
  if (/^[0-9a-f]{6}$/i.test(line.lineColor || '')) {
    heading.style.setProperty('--line-color', `#${line.lineColor}`);
  }
  item.append(heading);
  const textEntries = [];
  if (alerts.length === 0) {
    const text = hasData ? 'Trafic normal' : 'Données indisponibles';
    const message = document.createElement('p');
    message.className = 'transit-line-message';
    item.append(message);
    textEntries.push({ element: message, text });
  } else {
    for (const alert of alerts) {
      const card = document.createElement('div');
      card.className = 'transit-alert';
      card.dataset.severity = alert.severity;
      const title = document.createElement('p');
      title.className = 'transit-alert-title';
      title.setAttribute('aria-live', 'off');
      card.append(title);
      textEntries.push({ element: title, text: alert.title });
      if (alert.shortMessage && alert.shortMessage !== alert.title) {
        const summary = document.createElement('p');
        summary.className = 'transit-alert-summary';
        summary.setAttribute('aria-live', 'off');
        card.append(summary);
        textEntries.push({ element: summary, text: alert.shortMessage });
      }
      const detailText = providerMessageText(alert.message);
      if (detailText) {
        const details = document.createElement('details');
        details.className = 'transit-alert-details';
        const summary = document.createElement('summary');
        summary.textContent = 'Détails';
        const body = document.createElement('p');
        body.className = 'transit-alert-message';
        body.setAttribute('aria-live', 'off');
        details.append(summary, body);
        card.append(details);
        textEntries.push({ element: body, text: detailText });
      }
      item.append(card);
    }
  }
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  if (typingOptions.enabled !== false && !reducedMotion) {
    textEntries.forEach(({ element }) => element.classList.add('announcement-idle-caret'));
  }
  textEntries.forEach(({ element, text }) => typeText(element, text, typingOptions));
  if (typingOptions.enabled !== false && !reducedMotion) {
    loops.set(line.id, startRetypeLoop({
      run: () => Promise.all(textEntries.map(({ element, text }) => {
        element.classList.add('announcement-idle-caret');
        return typeText(element, text, { ...typingOptions, restart: true });
      })),
      isActive: () => root.isConnected,
    }));
  }
  return item;
}

export async function initialize(container, _configJson, typingOptions = {}, dependencies = {}) {
  const root = container.querySelector('[data-transit-widget]');
  if (!root) return;
  const status = root.querySelector('[data-transit-status]');
  const updated = root.querySelector('[data-transit-updated]');
  const lines = root.querySelector('[data-transit-lines]');
  const testBadge = root.querySelector('[data-external-test-mode]');
  if (testBadge) testBadge.hidden = root.dataset.testMode !== 'true';
  let loops = new Map();
  let refreshTimer = null;
  let lastSignature = null;
  const setPoll = dependencies.setInterval ?? ((callback, delay) => window.setInterval(callback, delay));

  const dispose = () => {
    window.clearInterval(refreshTimer);
    stopLoops(loops);
  };

  const render = (overview) => {
    if (testBadge) testBadge.hidden = !overview.testMode;
    stopLoops(loops);
    loops = new Map();
    lines.replaceChildren();
    for (const entry of overview.lines) {
      lines.append(createLineEntry(entry.line, entry.alerts, Boolean(overview.fetchedAt),
        typingOptions, loops, root));
    }
    updated.textContent = formatTransitUpdatedAt(overview.fetchedAt);
    status.hidden = !overview.notice;
    typeText(status, overview.notice || '', typingOptions);
  };

  const load = async () => {
    if (!root.isConnected) {
      dispose();
      return;
    }
    try {
      const overview = await (dependencies.api ?? api).transitAlerts();
      const signature = JSON.stringify([overview.fetchedAt, overview.notice, overview.paused]);
      if (signature === lastSignature) return;
      lastSignature = signature;
      render(overview);
    } catch (error) {
      if (lastSignature !== null) return;
      status.hidden = false;
      typeText(status, error instanceof ApiError ? error.message : 'Les alertes trafic sont momentanément indisponibles.',
        typingOptions);
    }
  };

  await load();
  refreshTimer = setPoll(load, REFRESH_MS);
}
