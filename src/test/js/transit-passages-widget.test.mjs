import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import {
  formatMinutes, formatPassagesUpdatedAt, initialize, REFRESH_MS,
} from '../../main/resources/static/js/widgets/transit-passages.js';
import { ApiError } from '../../main/resources/static/js/api.js';

class FakeElement {
  constructor() {
    this.children = [];
    this.dataset = {};
    this.style = { props: {}, setProperty(name, value) { this.props[name] = value; } };
    this.hidden = false;
    this.isConnected = true;
    this.elements = new Map();
    this.textContent = '';
    this.className = '';
  }

  replaceChildren(...children) {
    this.children = children;
  }

  append(...children) {
    this.children.push(...children);
  }

  querySelector(selector) {
    return this.elements.get(selector) ?? null;
  }
}

const originalDocument = globalThis.document;

afterEach(() => {
  if (originalDocument === undefined) delete globalThis.document;
  else globalThis.document = originalDocument;
});

function setup(testMode = 'false') {
  globalThis.document = { createElement: () => new FakeElement() };
  const root = new FakeElement();
  root.dataset.testMode = testMode;
  const status = new FakeElement();
  const stops = new FakeElement();
  const badge = new FakeElement();
  root.elements.set('[data-passages-status]', status);
  root.elements.set('[data-passages-stops]', stops);
  root.elements.set('[data-external-test-mode]', badge);
  const container = { querySelector: (selector) => (selector === '[data-passages-widget]' ? root : null) };
  return { root, status, stops, badge, container };
}

const OVERVIEW = {
  testMode: false,
  notice: null,
  stops: [{
    stop: { arrid: '24377', name: 'Carnot', town: 'Paris', type: 'bus' },
    state: 'OK',
    message: null,
    updatedAt: '2026-10-30T08:12:30Z',
    passages: [
      { line: 'Bus 172', lineColor: '00aa00', destination: "Porte d'Orléans", minutes: 5 },
      { line: 'Bus 172', lineColor: 'zz', destination: 'Gare', minutes: 0 },
    ],
  }],
};

test('formats minutes remaining and the Paris update time', () => {
  assert.equal(formatMinutes(5), 'dans 5 min');
  assert.equal(formatMinutes(0), "À l'approche");
  assert.equal(formatPassagesUpdatedAt('2026-10-30T08:12:30Z'), 'Mis à jour le 30/10/2026 09:12');
  assert.equal(formatPassagesUpdatedAt(null), '');
  assert.equal(formatPassagesUpdatedAt('invalid'), '');
});

test('renders stops with minutes, line colour and update time, then polls every minute', async () => {
  const { status, stops, badge, container } = setup();
  const polls = [];
  await initialize(container, '{}', {}, {
    api: { transitPassages: async () => OVERVIEW },
    setInterval: (callback, delay) => { polls.push({ callback, delay }); return 1; },
  });

  assert.equal(polls[0].delay, REFRESH_MS);
  assert.equal(REFRESH_MS, 60000);
  assert.equal(status.hidden, true);
  assert.equal(badge.hidden, true);
  const stop = stops.children[0];
  assert.equal(stop.children[0].textContent, 'Carnot · Paris');
  const rows = stop.children[1].children;
  assert.equal(rows[0].children[2].textContent, 'dans 5 min');
  assert.equal(rows[0].children[0].style.props['--line-color'], '#00aa00');
  assert.equal(rows[1].children[0].style.props['--line-color'], undefined);
  assert.equal(rows[1].children[2].textContent, "À l'approche");
  assert.equal(stop.children[2].textContent, 'Mis à jour le 30/10/2026 09:12');
});

test('shows the server notice such as the missing PRIM key and the test badge', async () => {
  const { status, badge, container } = setup('true');
  await initialize(container, '{}', {}, {
    api: { transitPassages: async () => ({ testMode: true, notice: 'Renseignez la clé PRIM', stops: [] }) },
    setInterval: () => 1,
  });

  assert.equal(status.hidden, false);
  assert.equal(status.textContent, 'Renseignez la clé PRIM');
  assert.equal(badge.hidden, false);
});

test('asks to pick a stop when none is configured', async () => {
  const { status, container } = setup();
  await initialize(container, '{}', {}, {
    api: { transitPassages: async () => ({ testMode: false, notice: null, stops: [] }) },
    setInterval: () => 1,
  });

  assert.match(status.textContent, /Aucun arrêt sélectionné/);
});

test('keeps the previous display on a later failure but reports the first one', async () => {
  const first = setup();
  await initialize(first.container, '{}', {}, {
    api: { transitPassages: async () => { throw new ApiError(503, 'Indisponible.'); } },
    setInterval: () => 1,
  });
  assert.equal(first.status.textContent, 'Indisponible.');

  const second = setup();
  let fail = false;
  const polls = [];
  await initialize(second.container, '{}', {}, {
    api: { transitPassages: async () => { if (fail) throw new Error('réseau'); return OVERVIEW; } },
    setInterval: (callback) => { polls.push(callback); return 1; },
  });
  fail = true;
  await polls[0]();
  assert.equal(second.stops.children.length, 1);
  assert.equal(second.status.hidden, true);
});

test('stops polling when the widget leaves the document or the session is lost', async () => {
  const detached = setup();
  const cleared = [];
  const polls = [];
  let calls = 0;
  await initialize(detached.container, '{}', {}, {
    api: { transitPassages: async () => { calls += 1; return OVERVIEW; } },
    setInterval: (callback) => { polls.push(callback); return 7; },
    clearInterval: (timer) => cleared.push(timer),
  });
  detached.root.isConnected = false;
  await polls[0]();
  assert.deepEqual(cleared, [7]);
  assert.equal(calls, 1);

  const expired = setup();
  const timers = [];
  await initialize(expired.container, '{}', {}, {
    api: { transitPassages: async () => { throw new ApiError(401, 'Session expirée.'); } },
    setInterval: () => { timers.push('armed'); return 1; },
    clearInterval: () => {},
  });
  assert.deepEqual(timers, []);
});
