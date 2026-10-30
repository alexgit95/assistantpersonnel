import assert from 'node:assert/strict';
import { test } from 'node:test';
import { captureKioskKey, extractKioskKey, kioskHeaders, KIOSK_HEADER } from '../../main/resources/static/js/kiosk-key.js';

function memoryStorage() {
  const data = new Map();
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
  };
}

function fakeHistory() {
  const calls = [];
  return { calls, replaceState: (state, title, url) => calls.push(url) };
}

test('extractKioskKey separates the key from the other parameters', () => {
  assert.deepEqual(extractKioskKey('?kiosk=abc&x=1'), { key: 'abc', search: '?x=1' });
  assert.deepEqual(extractKioskKey('?kiosk=abc'), { key: 'abc', search: '' });
});

test('extractKioskKey leaves a query string without key untouched', () => {
  assert.deepEqual(extractKioskKey('?x=1'), { key: null, search: '?x=1' });
  assert.deepEqual(extractKioskKey(''), { key: null, search: '' });
});

test('an empty kiosk parameter is removed without storing a key', () => {
  assert.deepEqual(extractKioskKey('?kiosk='), { key: null, search: '' });
});

test('captureKioskKey stores the key and cleans the address bar', () => {
  const storage = memoryStorage();
  const history = fakeHistory();
  const key = captureKioskKey(storage, { search: '?kiosk=secret&x=1', pathname: '/', hash: '#a' }, history);
  assert.equal(key, 'secret');
  assert.deepEqual(history.calls, ['/?x=1#a']);
  assert.deepEqual(kioskHeaders(storage), { [KIOSK_HEADER]: 'secret' });
});

test('captureKioskKey keeps the stored key on later loads without parameter', () => {
  const storage = memoryStorage();
  captureKioskKey(storage, { search: '?kiosk=secret', pathname: '/', hash: '' }, fakeHistory());
  const history = fakeHistory();
  const key = captureKioskKey(storage, { search: '', pathname: '/', hash: '' }, history);
  assert.equal(key, 'secret');
  assert.deepEqual(history.calls, []);
});

test('kioskHeaders is empty when no key is stored', () => {
  assert.deepEqual(kioskHeaders(memoryStorage()), {});
});
