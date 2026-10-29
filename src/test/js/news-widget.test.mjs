import assert from 'node:assert/strict';
import { test } from 'node:test';
import { COLLAPSE_MS, initialize, ROTATE_MS, stepIndex } from '../../main/resources/static/js/widgets/news.js';

class FakeElement {
  constructor(tag = 'div') {
    this.tag = tag;
    this.children = [];
    this.dataset = {};
    this.attributes = new Map();
    this.listeners = new Map();
    this.hidden = false;
    this.isConnected = true;
    this.elements = new Map();
    this.className = '';
    this.id = '';
    this.type = '';
    this.classList = { add() {}, remove() {}, contains: () => false };
    this.text = '';
  }

  replaceChildren(...children) {
    this.children = children;
  }

  append(...children) {
    this.children.push(...children);
  }

  setAttribute(name, value) {
    this.attributes.set(name, String(value));
  }

  getAttribute(name) {
    return this.attributes.get(name) ?? null;
  }

  addEventListener(name, handler) {
    this.listeners.set(name, handler);
  }

  click() {
    this.listeners.get('click')?.();
  }

  querySelector(selector) {
    return this.elements.get(selector) ?? null;
  }

  get textContent() {
    return this.children.length ? this.children.map((child) => child.textContent ?? '').join('') : this.text;
  }

  set textContent(value) {
    this.children = [];
    this.text = String(value);
  }
}

class FakeTimers {
  constructor() {
    this.now = 0;
    this.next = 1;
    this.pending = new Map();
  }

  set(callback, delay) {
    const id = this.next++;
    this.pending.set(id, { callback, at: this.now + delay });
    return id;
  }

  clear(id) {
    this.pending.delete(id);
  }

  async advance(ms) {
    const target = this.now + ms;
    for (;;) {
      const due = [...this.pending.entries()].filter(([, timer]) => timer.at <= target)
        .sort((a, b) => a[1].at - b[1].at)[0];
      if (!due) break;
      this.pending.delete(due[0]);
      this.now = due[1].at;
      await due[1].callback();
    }
    this.now = target;
  }
}

function setup(overview) {
  globalThis.document = { createElement: (tag) => new FakeElement(tag) };
  globalThis.window = { matchMedia: () => ({ matches: true }), setTimeout, clearTimeout };
  const root = new FakeElement();
  const parts = {
    status: new FakeElement('p'), header: new FakeElement(), category: new FakeElement('h3'),
    previous: new FakeElement('button'), next: new FakeElement('button'), list: new FakeElement('ul'),
  };
  root.elements.set('[data-news-status]', parts.status);
  root.elements.set('[data-news-header]', parts.header);
  root.elements.set('[data-news-category]', parts.category);
  root.elements.set('[data-news-prev]', parts.previous);
  root.elements.set('[data-news-next]', parts.next);
  root.elements.set('[data-news-entries]', parts.list);
  const container = new FakeElement();
  container.elements.set('[data-news-widget]', root);
  const timers = new FakeTimers();
  const api = { newsFeed: async () => overview };
  const start = () => initialize(container, '{}', { enabled: false }, { api, timers });
  return { root, parts, timers, start };
}

const entries = (name) => [
  { title: `${name} 1`, description: `${name} desc 1` },
  { title: `${name} 2`, description: `${name} desc 2` },
];
const three = {
  testMode: false,
  categories: [
    { id: 1, name: 'Tech', entries: entries('Tech'), notice: null },
    { id: 2, name: 'Sport', entries: entries('Sport'), notice: null },
    { id: 3, name: 'Culture', entries: entries('Culture'), notice: null },
  ],
};

test('stepIndex wraps in both directions', () => {
  assert.equal(stepIndex(2, 3, 1), 0);
  assert.equal(stepIndex(0, 3, -1), 2);
  assert.equal(stepIndex(1, 3, 1), 2);
  assert.equal(stepIndex(0, 0, 1), 0);
});

test('the widget rotates to the next category every 15 seconds and loops', async () => {
  const { parts, timers, start } = setup(three);
  await start();
  assert.match(parts.category.textContent, /^Tech \(1\/3\)$/);

  await timers.advance(ROTATE_MS);
  assert.match(parts.category.textContent, /^Sport \(2\/3\)$/);
  await timers.advance(ROTATE_MS * 2);
  assert.match(parts.category.textContent, /^Tech \(1\/3\)$/);
});

test('arrows loop and restart the 15 second counter', async () => {
  const { parts, timers, start } = setup(three);
  await start();

  parts.previous.click();
  assert.match(parts.category.textContent, /^Culture/);
  await timers.advance(ROTATE_MS - 1);
  assert.match(parts.category.textContent, /^Culture/);
  parts.next.click();
  assert.match(parts.category.textContent, /^Tech/);
  await timers.advance(ROTATE_MS - 1);
  assert.match(parts.category.textContent, /^Tech/);
  await timers.advance(1);
  assert.match(parts.category.textContent, /^Sport/);
});

test('an expanded entry suspends the rotation and collapses after 20 seconds', async () => {
  const { parts, timers, start } = setup(three);
  await start();
  const [item] = parts.list.children;
  const [button, description] = item.children;
  assert.equal(description.hidden, true);

  button.click();
  assert.equal(description.hidden, false);
  assert.equal(button.getAttribute('aria-expanded'), 'true');
  await timers.advance(COLLAPSE_MS - 1);
  assert.match(parts.category.textContent, /^Tech/);
  assert.equal(description.hidden, false);

  await timers.advance(1);
  assert.equal(description.hidden, true);
  assert.match(parts.category.textContent, /^Tech/);
  await timers.advance(ROTATE_MS - 1);
  assert.match(parts.category.textContent, /^Tech/);
  await timers.advance(1);
  assert.match(parts.category.textContent, /^Sport/);
});

test('clicking an expanded entry collapses it and clicking another expands only that one', async () => {
  const { parts, timers, start } = setup(three);
  await start();
  const [first, second] = parts.list.children;

  first.children[0].click();
  second.children[0].click();
  assert.equal(first.children[1].hidden, true);
  assert.equal(second.children[1].hidden, false);

  second.children[0].click();
  assert.equal(second.children[1].hidden, true);
  await timers.advance(ROTATE_MS);
  assert.match(parts.category.textContent, /^Sport/);
});

test('a single category has no rotation and no arrows', async () => {
  const { parts, timers, start } = setup({ testMode: false, categories: [three.categories[0]] });
  await start();

  assert.equal(parts.previous.hidden, true);
  assert.equal(parts.next.hidden, true);
  assert.equal(parts.category.textContent, 'Tech');
  await timers.advance(ROTATE_MS * 4);
  assert.equal(parts.category.textContent, 'Tech');
});

test('without any selected category the widget invites to configure', async () => {
  const { parts, start } = setup({ testMode: false, categories: [] });
  await start();

  assert.equal(parts.header.hidden, true);
  assert.equal(parts.status.hidden, false);
  assert.match(parts.status.textContent, /Choisissez des catégories/);
});

test('timers stop once the widget leaves the DOM', async () => {
  const { root, timers, start } = setup(three);
  await start();

  root.isConnected = false;
  await timers.advance(ROTATE_MS);
  assert.equal(timers.pending.size, 0);
});
