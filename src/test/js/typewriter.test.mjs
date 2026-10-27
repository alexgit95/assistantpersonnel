import assert from 'node:assert/strict';
import { afterEach, test } from 'node:test';
import { animateMarkedText, characterDelay, typeText } from '../../main/resources/static/js/typewriter.js';

class FakeElement {
  constructor() {
    this.children = [];
    this.classList = { add() {}, remove() {} };
  }

  replaceChildren(...children) {
    this.children = children;
  }

  get textContent() {
    return this.children.map((child) => child.textContent ?? '').join('');
  }

  set textContent(value) {
    this.children = [{ textContent: String(value) }];
  }

  closest() {
    return null;
  }
}

class FakeSpan {
  textContent = '';
  className = '';
  classList = {
    add() {},
    remove() {},
  };

  setAttribute() {}
}

const originalDocument = globalThis.document;
const originalWindow = globalThis.window;

afterEach(() => {
  if (originalDocument === undefined) {
    delete globalThis.document;
  } else {
    globalThis.document = originalDocument;
  }
  if (originalWindow === undefined) {
    delete globalThis.window;
  } else {
    globalThis.window = originalWindow;
  }
});

function setupBrowser(reducedMotion = false) {
  globalThis.document = { createElement: () => new FakeSpan() };
  globalThis.window = {
    matchMedia: () => ({ matches: reducedMotion }),
    setTimeout,
  };
}

test('caps the character delay so long text completes within the duration limit', () => {
  assert.equal(characterDelay(10, 25, 2500), 25);
  assert.equal(characterDelay(1000, 25, 2000), 2);
  assert.equal(characterDelay(0, 25, 2000), 0);
});

test('shows the full accessible text immediately when disabled', async () => {
  setupBrowser();
  const element = new FakeElement();

  await typeText(element, 'Texte complet', { enabled: false });

  assert.equal(element.children[0].textContent, 'Texte complet');
  assert.equal(element.children[1].textContent, 'Texte complet');
});

test('respects reduced motion and preserves full text for assistive technology', async () => {
  setupBrowser(true);
  const element = new FakeElement();

  await typeText(element, 'Bonjour');

  assert.equal(element.children[0].textContent, 'Bonjour');
  assert.equal(element.children[1].textContent, 'Bonjour');
});

test('does not restart animation for unchanged text', async () => {
  setupBrowser();
  const element = new FakeElement();
  const first = typeText(element, 'Salut', { speedMs: 1 });
  const visibleSpan = element.children[1];
  const second = typeText(element, 'Salut', { speedMs: 1 });

  assert.equal(first, second);
  assert.equal(element.children[1], visibleSpan);
  await first;
  assert.equal(visibleSpan.textContent, 'Salut');
});

test('does not animate text in a hidden step before it is shown', () => {
  setupBrowser();
  const hidden = new FakeElement();
  hidden.closest = () => ({ hidden: true });
  globalThis.document.querySelectorAll = () => [hidden];

  animateMarkedText(document, { enabled: true, speedMs: 1 });

  assert.equal(hidden.children.length, 0);
});

test('repeated marked-text rendering reuses the original string during and after animation', async () => {
  setupBrowser();
  const element = new FakeElement();
  element.textContent = 'Profil connecté';
  globalThis.document.querySelectorAll = () => [element];

  animateMarkedText(document, { enabled: true, speedMs: 20 });
  const visual = element.children[1];
  animateMarkedText(document, { enabled: true, speedMs: 20 });
  assert.equal(element.children[1], visual);

  await typeText(element, 'Profil connecté', { enabled: true, speedMs: 20 });
  animateMarkedText(document, { enabled: true, speedMs: 20 });
  assert.equal(element.children[1], visual);
  assert.equal(visual.textContent, 'Profil connecté');
});
