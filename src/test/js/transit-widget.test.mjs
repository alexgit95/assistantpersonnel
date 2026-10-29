import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import {
  formatTransitUpdatedAt, initialize, REFRESH_MS, retypeTransitText,
} from '../../main/resources/static/js/widgets/transit.js';

class FakeElement {
  constructor() {
    this.children = [];
    this.dataset = {};
    this.style = { setProperty() {} };
    this.attributes = new Map();
    this.hidden = false;
    this.isConnected = true;
    this.elements = new Map();
    const classes = new Set();
    this.classList = {
      add: (value) => classes.add(value),
      remove: (value) => classes.delete(value),
      contains: (value) => classes.has(value),
    };
  }

  replaceChildren(...children) {
    this.children = children;
  }

  append(...children) {
    this.children.push(...children);
  }

  setAttribute(name, value) {
    this.attributes.set(name, value);
  }

  querySelector(selector) {
    return this.elements.get(selector) ?? null;
  }

  get textContent() {
    return this.children.map((child) => child.textContent ?? '').join('');
  }

  set textContent(value) {
    this.children = [{ textContent: String(value) }];
  }
}

class FakeSpan {
  textContent = '';
  className = '';
  classList = { add() {}, remove() {} };
  setAttribute() {}
}

const originalDocument = globalThis.document;
const originalWindow = globalThis.window;
const originalDOMParser = globalThis.DOMParser;

afterEach(() => {
  if (originalDocument === undefined) delete globalThis.document;
  else globalThis.document = originalDocument;
  if (originalWindow === undefined) delete globalThis.window;
  else globalThis.window = originalWindow;
  if (originalDOMParser === undefined) delete globalThis.DOMParser;
  else globalThis.DOMParser = originalDOMParser;
});

function fakeScheduler() {
  const queue = [];
  return {
    queue,
    schedule: (fn, ms) => { const task = { fn, ms }; queue.push(task); return task; },
    cancel: (task) => { task.cancelled = true; },
    async fire() {
      const task = queue.find((item) => !item.done && !item.cancelled);
      task.done = true;
      await task.fn();
    },
  };
}

function setupBrowser(reducedMotion = true) {
  const timers = [];
  globalThis.document = { createElement: (tag) => tag === 'span' ? new FakeSpan() : new FakeElement() };
  globalThis.document.createTextNode = (text) => ({ textContent: text });
  globalThis.DOMParser = class {
    parseFromString(markup) {
      const textContent = markup.replace(/<script\b[^>]*>[\s\S]*?<\/script>/gi, '')
        .replace(/<style\b[^>]*>[\s\S]*?<\/style>/gi, '')
        .replace(/<br\s*\/?>/gi, '\n').replace(/<\/p>/gi, '\n')
        .replace(/<[^>]+>/g, '').replace(/&nbsp;/gi, ' ').replace(/&amp;/gi, '&');
      return {
        body: { querySelectorAll: () => [], textContent },
        createTextNode: (text) => ({ textContent: text }),
      };
    }
  };
  globalThis.window = {
    matchMedia: () => ({ matches: reducedMotion }),
    setTimeout: (callback, delay) => { timers.push({ callback, delay }); return timers.length; },
    clearTimeout() {},
    setInterval: () => 2,
    clearInterval() {},
  };
  return timers;
}

test('formats the last successful refresh in French Paris time', () => {
  assert.equal(formatTransitUpdatedAt('2026-10-28T11:00:00Z'), 'Mis à jour le 28/10/2026 12:00');
  assert.equal(formatTransitUpdatedAt(null), '');
  assert.equal(formatTransitUpdatedAt('invalid'), '');
});

test('reuses the shared random loop and keeps the announcements caret after replay', async () => {
  setupBrowser();
  const scheduler = fakeScheduler();
  const element = new FakeElement();
  const loop = retypeTransitText(element, 'RER A · Trafic normal', { enabled: true }, {
    ...scheduler, random: () => 0,
  });
  const firstVisual = element.children[1];

  assert.equal(element.classList.contains('announcement-idle-caret'), true);
  assert.equal(scheduler.queue[0].ms, 15000);
  await scheduler.fire();
  assert.notEqual(element.children[1], firstVisual);
  assert.equal(element.classList.contains('announcement-idle-caret'), true);
  assert.equal(scheduler.queue[1].ms, 15000);
  loop.stop();
});

test('renders a line alert and the TEST status without throwing while starting its retype loop', async () => {
  const timers = setupBrowser(false);
  const status = new FakeElement();
  const updated = new FakeElement();
  const lines = new FakeElement();
  const badge = new FakeElement();
  const root = new FakeElement();
  root.elements.set('[data-transit-status]', status);
  root.elements.set('[data-transit-updated]', updated);
  root.elements.set('[data-transit-lines]', lines);
  root.elements.set('[data-external-test-mode]', badge);
  const container = new FakeElement();
  container.elements.set('[data-transit-widget]', root);
  const fetchedAt = '2026-10-28T14:00:00Z';

  await initialize(container, '{}', { enabled: true }, { api: { transitAlerts: async () => ({
    testMode: true,
    paused: false,
    notice: 'MODE TEST · données simulées',
    fetchedAt,
    lines: [{
      line: { id: 'C01739', label: 'Transilien J', lineColor: 'cec73d' },
      alerts: [{ id: 'alert-j', title: 'Ligne J : Ralentissements',
        shortMessage: 'Ralentissements',
        message: '<p>Le trafic est ralenti.<br><script>alert(1)</script><strong>Reprise estimée ce soir.</strong></p>',
        severity: 'PERTURBEE' }],
    }],
  }) } });

  assert.equal(badge.hidden, false, status.textContent);
  assert.match(status.textContent, /MODE TEST/);
  assert.equal(updated.textContent, formatTransitUpdatedAt(fetchedAt));
  assert.equal(lines.children.length, 1);
  assert.match(lines.children[0].textContent, /Ligne J : Ralentissements/);
  assert.match(lines.children[0].textContent, /Le trafic est ralenti/);
  assert.match(lines.children[0].textContent, /Reprise estimée ce soir/);
  assert.doesNotMatch(lines.children[0].textContent, /alert\(1\)/);
  assert.ok(timers.some(({ delay }) => delay >= 15000 && delay <= 60000));
  const alertCard = lines.children[0].children[1];
  const detailsBody = alertCard.children[2].children[1];
  assert.equal(alertCard.children[0].classList.contains('announcement-idle-caret'), true);
  assert.equal(alertCard.children[1].classList.contains('announcement-idle-caret'), true);
  assert.equal(detailsBody.classList.contains('announcement-idle-caret'), true);
});

function pollingSetup(responses) {
  setupBrowser(true);
  const status = new FakeElement();
  const updated = new FakeElement();
  const lines = new FakeElement();
  const root = new FakeElement();
  root.elements.set('[data-transit-status]', status);
  root.elements.set('[data-transit-updated]', updated);
  root.elements.set('[data-transit-lines]', lines);
  const container = new FakeElement();
  container.elements.set('[data-transit-widget]', root);
  const polls = [];
  let calls = 0;
  const api = { transitAlerts: async () => {
    const next = responses[Math.min(calls, responses.length - 1)];
    calls += 1;
    if (next instanceof Error) throw next;
    return next;
  } };
  const start = () => initialize(container, '{}', { enabled: false }, {
    api, setInterval: (callback, delay) => { polls.push({ callback, delay }); return 2; },
  });
  return { status, updated, lines, polls, start };
}

const overviewAt = (fetchedAt, notice = null, paused = false) => ({
  testMode: false, paused, notice, fetchedAt,
  lines: [{ line: { id: 'C01739', label: 'Transilien J' }, alerts: [] }],
});

test('polls the server every 60 seconds', async () => {
  const { polls, start } = pollingSetup([overviewAt('2026-10-28T14:00:00Z')]);

  await start();

  assert.equal(REFRESH_MS, 60_000);
  assert.equal(polls[0].delay, 60_000);
});

test('does nothing when the polled data has not changed', async () => {
  const same = overviewAt('2026-10-28T14:00:00Z');
  const { lines, updated, polls, start } = pollingSetup([same, { ...same }]);
  await start();
  const firstLine = lines.children[0];

  await polls[0].callback();

  assert.equal(lines.children[0], firstLine);
  assert.equal(updated.textContent, formatTransitUpdatedAt('2026-10-28T14:00:00Z'));
});

test('renders again when the timestamp or the status message changes', async () => {
  const { lines, updated, status, polls, start } = pollingSetup([
    overviewAt('2026-10-28T14:00:00Z'),
    overviewAt('2026-10-28T14:10:00Z'),
    overviewAt('2026-10-28T14:10:00Z', 'Alertes en pause jusqu\'à 5 h.', true),
  ]);
  await start();
  const firstLine = lines.children[0];

  await polls[0].callback();
  assert.notEqual(lines.children[0], firstLine);
  assert.equal(updated.textContent, formatTransitUpdatedAt('2026-10-28T14:10:00Z'));

  await polls[0].callback();
  assert.equal(status.hidden, false);
});

test('keeps the previous display and time when a poll fails', async () => {
  const { lines, updated, status, polls, start } = pollingSetup([
    overviewAt('2026-10-28T14:00:00Z'), new Error('offline'),
  ]);
  await start();
  const firstLine = lines.children[0];

  await polls[0].callback();

  assert.equal(lines.children[0], firstLine);
  assert.equal(updated.textContent, formatTransitUpdatedAt('2026-10-28T14:00:00Z'));
  assert.equal(status.hidden, true);
});

test('reduced-motion writes once without starting a retype timer', () => {
  setupBrowser(true);
  const scheduler = fakeScheduler();
  const element = new FakeElement();

  const loop = retypeTransitText(element, 'Trafic normal', { enabled: false }, scheduler);

  assert.equal(loop, null);
  assert.equal(element.children[1].textContent, 'Trafic normal');
  assert.equal(element.classList.contains('announcement-idle-caret'), false);
  assert.equal(scheduler.queue.length, 0);
});
