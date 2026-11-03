import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  appsSignature, CHECK_INTERVAL_MS, createRefresher, isEnrollmentBlocking, RELOAD_GUARD_KEY,
  shouldReloadForVersion,
} from '../../main/resources/static/js/kiosk-refresh.js';

function memoryStorage(initial = {}) {
  const data = new Map(Object.entries(initial));
  return {
    getItem: (key) => (data.has(key) ? data.get(key) : null),
    setItem: (key, value) => data.set(key, String(value)),
  };
}

const APPS = [{ key: 'weather', enabled: true, position: 0, configJson: '{"city":"Paris"}' }];

function setup(overrides = {}) {
  const calls = { config: 0, apps: 0, applied: [], reloads: 0 };
  const deps = {
    pageVersion: '1.1.1-aaaaaaa',
    isBusy: () => false,
    isConnected: () => false,
    fetchConfig: async () => { calls.config += 1; return { appVersion: '1.1.1-aaaaaaa' }; },
    fetchApps: async () => { calls.apps += 1; return APPS; },
    currentSignature: () => appsSignature(APPS),
    applyApps: async (apps) => { calls.applied.push(apps); },
    reload: () => { calls.reloads += 1; },
    storage: memoryStorage(),
    ...overrides,
  };
  return { calls, deps, refresher: createRefresher(deps) };
}

test('the check runs every minute', () => {
  assert.equal(CHECK_INTERVAL_MS, 60_000);
});

test('an unchanged configuration is not displayed again', async () => {
  const { calls, refresher } = setup();

  assert.equal(await refresher.tick(), 'unchanged');
  assert.deepEqual(calls.applied, []);
  assert.equal(calls.reloads, 0);
});

test('a changed configuration is displayed again with the new list', async () => {
  const changed = [{ ...APPS[0], position: 3 }];
  const { calls, refresher } = setup({ fetchApps: async () => changed });

  assert.equal(await refresher.tick(), 'refreshed');
  assert.deepEqual(calls.applied, [changed]);
});

test('a configuration that could not be shown yet is loaded at the next check', async () => {
  const { calls, refresher } = setup({ currentSignature: () => null });

  assert.equal(await refresher.tick(), 'refreshed');
  assert.equal(calls.applied.length, 1);
});

test('a failed read keeps the current display and retries later', async () => {
  const { calls, refresher } = setup({ fetchApps: async () => { throw new Error('offline'); } });

  assert.equal(await refresher.tick(), 'error');
  assert.deepEqual(calls.applied, []);
  assert.equal(calls.reloads, 0);
});

test('nothing is read nor changed while the kiosk is busy', async () => {
  const { calls, refresher } = setup({
    isBusy: () => true,
    fetchConfig: async () => ({ appVersion: '2.0.0-bbbbbbb' }),
  });

  assert.equal(await refresher.tick(), 'busy');
  assert.equal(calls.apps, 0);
  assert.equal(calls.reloads, 0);
  assert.deepEqual(calls.applied, []);
});

test('only a started enrollment blocks the check, not the waiting guest QR', () => {
  for (const state of ['COUNTDOWN', 'CAPTURING', 'REVIEW']) {
    assert.equal(isEnrollmentBlocking({ state }), true, state);
  }
  for (const state of ['WAITING', 'DONE', 'CANCELLED', 'EXPIRED']) {
    assert.equal(isEnrollmentBlocking({ state }), false, state);
  }
  assert.equal(isEnrollmentBlocking(null), false);
});

test('a new server version reloads the page when nobody is connected', async () => {
  const { calls, deps, refresher } = setup({
    fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }),
  });

  assert.equal(await refresher.tick(), 'reload');
  assert.equal(calls.reloads, 1);
  assert.equal(deps.storage.getItem(RELOAD_GUARD_KEY), '1.2.0-bbbbbbb');
  assert.equal(calls.apps, 0);
});

test('a new server version waits while a profile is connected, then reloads', async () => {
  let connected = true;
  const { calls, refresher } = setup({
    isConnected: () => connected,
    fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }),
  });

  assert.equal(await refresher.tick(), 'unchanged');
  assert.equal(calls.reloads, 0);

  connected = false;
  assert.equal(await refresher.tick(), 'reload');
  assert.equal(calls.reloads, 1);
});

test('the configuration is still refreshed while a reload is deferred', async () => {
  const changed = [{ ...APPS[0], enabled: false }];
  const { calls, refresher } = setup({
    isConnected: () => true,
    fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }),
    fetchApps: async () => changed,
  });

  assert.equal(await refresher.tick(), 'refreshed');
  assert.deepEqual(calls.applied, [changed]);
  assert.equal(calls.reloads, 0);
});

test('an enrollment starting during the network calls prevents the reload', async () => {
  let busyChecks = 0;
  const { calls, refresher } = setup({
    isBusy: () => { busyChecks += 1; return busyChecks > 1; },
    fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }),
  });

  assert.notEqual(await refresher.tick(), 'reload');
  assert.equal(calls.reloads, 0);
});

test('equal, empty or unreadable server versions never reload', async () => {
  for (const fetchConfig of [
    async () => ({ appVersion: '1.1.1-aaaaaaa' }),
    async () => ({ appVersion: '' }),
    async () => ({}),
    async () => { throw new Error('offline'); },
  ]) {
    const { calls, refresher } = setup({ fetchConfig });
    await refresher.tick();
    assert.equal(calls.reloads, 0);
  }
});

test('a page without version never reloads', async () => {
  const { calls, refresher } = setup({
    pageVersion: '',
    fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }),
  });

  await refresher.tick();
  assert.equal(calls.reloads, 0);
});

test('the page is reloaded only once for a given server version', async () => {
  const storage = memoryStorage();
  const first = setup({ storage, fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }) });
  assert.equal(await first.refresher.tick(), 'reload');

  // Apres le rechargement, un cache sert encore l'ancienne page : pas de seconde tentative pour cette version.
  const second = setup({ storage, fetchConfig: async () => ({ appVersion: '1.2.0-bbbbbbb' }) });
  assert.equal(await second.refresher.tick(), 'unchanged');
  assert.equal(second.calls.reloads, 0);

  // Une version encore plus recente declenche a nouveau un rechargement.
  const third = setup({ storage, fetchConfig: async () => ({ appVersion: '1.3.0-ccccccc' }) });
  assert.equal(await third.refresher.tick(), 'reload');
});

test('a check that is already running is not started twice', async () => {
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  const { refresher } = setup({ fetchApps: async () => { await gate; return APPS; } });

  const first = refresher.tick();
  assert.equal(await refresher.tick(), 'running');
  release();
  assert.equal(await first, 'unchanged');
});

test('version comparison and list signature helpers', () => {
  assert.equal(shouldReloadForVersion({ pageVersion: 'a', serverVersion: 'b', reloadedFor: null }), true);
  assert.equal(shouldReloadForVersion({ pageVersion: 'a', serverVersion: 'b', reloadedFor: 'b' }), false);
  assert.equal(shouldReloadForVersion({ pageVersion: 'a', serverVersion: 'a', reloadedFor: null }), false);
  assert.equal(shouldReloadForVersion({ pageVersion: 'a', serverVersion: null, reloadedFor: null }), false);
  assert.equal(appsSignature(APPS), appsSignature(JSON.parse(JSON.stringify(APPS))));
  assert.notEqual(appsSignature(APPS), appsSignature([{ ...APPS[0], position: 1 }]));
});
