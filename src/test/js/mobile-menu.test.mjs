import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  capturePairing, clearEnrollment, clearPairing, extractPairing, menuFor, PAIRING_STORAGE_KEY, readEnrollment,
  saveEnrollment, SCAN_AGAIN, SCAN_PROMPT, unauthorizedMessage,
} from '../../main/resources/static/js/mobile-menu.js';

function memoryStorage() {
  const data = new Map();
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
    removeItem: (key) => data.delete(key),
  };
}

const ids = (model) => model.items.map((item) => item.id);

test('guest menu offers profile creation and administration', () => {
  const model = menuFor({ pairingValid: true });
  assert.deepEqual(ids(model), ['enroll', 'admin']);
  assert.equal(model.items[0].href, '/mobile/enroll');
  assert.equal(model.items[1].href, '/admin');
});

test('a fresh QR scan wins over an older session', () => {
  const model = menuFor({ pairingValid: true, session: { scope: 'FULL', profileName: 'Alice' } });
  assert.deepEqual(ids(model), ['enroll', 'admin']);
});

test('connected menu has configuration, weight, recapture, administration and logout', () => {
  const model = menuFor({ session: { scope: 'FULL', profileName: 'Alice' } });
  assert.deepEqual(ids(model), ['configuration', 'weight', 'recapture', 'admin', 'logout']);
  assert.match(model.note, /Alice/);
  assert.equal(model.items.find((item) => item.id === 'logout').action, 'logout');
  assert.equal(model.items.find((item) => item.id === 'recapture').action, 'recapture');
  assert.equal(model.items.find((item) => item.id === 'recapture').label, 'Refaire mes captures');
});

test('weight session only gets the weight page and logout', () => {
  const model = menuFor({ session: { scope: 'WEIGHT', profileName: 'Alice' } });
  assert.deepEqual(ids(model), ['weight', 'logout']);
  assert.ok(!ids(model).includes('recapture'));
});

test('without token nor session the menu asks for a scan and keeps administration', () => {
  const model = menuFor({});
  assert.equal(model.note, SCAN_PROMPT);
  assert.deepEqual(ids(model), ['admin']);
});

test('an expired session asks to scan again', () => {
  assert.equal(menuFor({ wasConnected: true }).note, SCAN_AGAIN);
});

test('401 asks to scan again and 403 explains the limit', () => {
  assert.equal(unauthorizedMessage(401), SCAN_AGAIN);
  assert.match(unauthorizedMessage(403), /ne permet pas/);
});

test('the pairing token is read from the query string and removed from it', () => {
  assert.deepEqual(extractPairing('?s=abc'), { token: 'abc', search: '' });
  assert.deepEqual(extractPairing('?x=1&s=abc'), { token: 'abc', search: '?x=1' });
  assert.deepEqual(extractPairing('?x=1'), { token: null, search: '?x=1' });
  assert.deepEqual(extractPairing(''), { token: null, search: '' });
});

test('capturing stores the token and cleans the address bar once', () => {
  const storage = memoryStorage();
  const replaced = [];
  const history = { replaceState: (...args) => replaced.push(args[2]) };
  const location = { search: '?s=tok', pathname: '/mobile', hash: '' };
  assert.equal(capturePairing(storage, location, history), 'tok');
  assert.deepEqual(replaced, ['/mobile']);
  assert.equal(storage.getItem(PAIRING_STORAGE_KEY), 'tok');
  assert.equal(capturePairing(storage, { search: '', pathname: '/mobile', hash: '' }, history), 'tok');
  assert.equal(replaced.length, 1);
  clearPairing(storage);
  assert.equal(capturePairing(storage, { search: '', pathname: '/mobile', hash: '' }, history), null);
});

test('an empty pairing parameter is dropped without being stored', () => {
  const storage = memoryStorage();
  const history = { replaceState() {} };
  assert.equal(capturePairing(storage, { search: '?s=', pathname: '/mobile', hash: '' }, history), null);
});

test('the claimed capture session survives a reload and ignores corrupted data', () => {
  const storage = memoryStorage();
  assert.equal(readEnrollment(storage), null);
  saveEnrollment(storage, 'id1', 'claim1');
  assert.deepEqual(readEnrollment(storage), { id: 'id1', claim: 'claim1' });
  storage.setItem('assistant.enrollment', '{not json');
  assert.equal(readEnrollment(storage), null);
  storage.setItem('assistant.enrollment', JSON.stringify({ id: 1 }));
  assert.equal(readEnrollment(storage), null);
  saveEnrollment(storage, 'id1', 'claim1');
  clearEnrollment(storage);
  assert.equal(readEnrollment(storage), null);
});
