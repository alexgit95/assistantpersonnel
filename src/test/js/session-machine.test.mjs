import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { SessionMachine, State } from '../../main/resources/static/js/session-machine.js';

const config = {
  sessionDurationSeconds: 600,
  heartbeatMinIntervalSeconds: 30,
  logoutPauseSeconds: 60,
  switchConfirmations: 2,
  scanIntervalDisconnectedMs: 1000,
  scanIntervalConnectedMs: 5000,
};
const matched = (profileId) => ({ status: 'matched', profileId });
const T0 = 1_000_000;
let machine;

beforeEach(() => {
  machine = new SessionMachine(config);
});

test('visage reconnu : ouverture de session', () => {
  assert.deepEqual(machine.observe(matched(1), T0), [{ type: 'login', profileId: 1 }]);
  assert.equal(machine.state, State.CONNECTED);
  assert.equal(machine.profileId, 1);
});

test('visage inconnu, ambigu ou absent : aucune session', () => {
  for (const status of ['none', 'tooSmall', 'unknown', 'ambiguous']) {
    assert.deepEqual(machine.observe({ status }, T0), []);
  }
  assert.equal(machine.state, State.SCAN);
});

test('intervalles selon l\'etat', () => {
  assert.equal(machine.intervalMs(T0), 1000);
  machine.observe(matched(1), T0);
  assert.equal(machine.intervalMs(T0), 5000);
});

test('heartbeat au plus une fois par intervalle minimal', () => {
  machine.observe(matched(1), T0);
  assert.deepEqual(machine.observe(matched(1), T0 + 5_000), []);
  assert.deepEqual(machine.observe(matched(1), T0 + 29_999), []);
  assert.deepEqual(machine.observe(matched(1), T0 + 30_000), [{ type: 'heartbeat' }]);
  assert.deepEqual(machine.observe(matched(1), T0 + 35_000), []);
  assert.deepEqual(machine.observe(matched(1), T0 + 60_000), [{ type: 'heartbeat' }]);
});

test('expiration locale 10 min apres le dernier heartbeat', () => {
  machine.observe(matched(1), T0);
  machine.observe(matched(1), T0 + 30_000);
  assert.deepEqual(machine.tick(T0 + 30_000 + 599_000), []);
  assert.equal(machine.remainingSeconds(T0 + 30_000 + 599_000), 1);
  assert.deepEqual(machine.tick(T0 + 30_000 + 600_000), [{ type: 'expired' }]);
  assert.equal(machine.state, State.SCAN);
  assert.equal(machine.profileId, null);
});

test('un autre visage isole ne change pas la session et ne la prolonge pas', () => {
  machine.observe(matched(1), T0);
  assert.deepEqual(machine.observe(matched(2), T0 + 40_000), []);
  assert.equal(machine.profileId, 1);
  assert.equal(machine.lastHeartbeatAt, T0);
});

test('bascule apres N confirmations consecutives', () => {
  machine.observe(matched(1), T0);
  assert.deepEqual(machine.observe(matched(2), T0 + 5_000), []);
  assert.deepEqual(machine.observe(matched(2), T0 + 10_000), [{ type: 'login', profileId: 2 }]);
  assert.equal(machine.profileId, 2);
  assert.equal(machine.lastHeartbeatAt, T0 + 10_000);
});

test('les confirmations doivent etre consecutives', () => {
  machine.observe(matched(1), T0);
  machine.observe(matched(2), T0 + 5_000);
  machine.observe({ status: 'unknown' }, T0 + 10_000);
  assert.deepEqual(machine.observe(matched(2), T0 + 15_000), []);
  machine.observe(matched(1), T0 + 20_000);
  assert.deepEqual(machine.observe(matched(2), T0 + 25_000), []);
  assert.equal(machine.profileId, 1);
});

test('deux autres profils alternes ne declenchent pas de bascule', () => {
  machine.observe(matched(1), T0);
  machine.observe(matched(2), T0 + 5_000);
  assert.deepEqual(machine.observe(matched(3), T0 + 10_000), []);
  assert.equal(machine.profileId, 1);
});

test('deconnexion : pause puis reprise du scan', () => {
  machine.observe(matched(1), T0);
  assert.deepEqual(machine.logout(T0 + 1_000), [{ type: 'logout' }]);
  assert.equal(machine.state, State.PAUSED);
  assert.equal(machine.pauseRemainingSeconds(T0 + 1_000), 60);
  assert.deepEqual(machine.observe(matched(1), T0 + 2_000), []);
  assert.deepEqual(machine.tick(T0 + 60_999), []);
  assert.equal(machine.intervalMs(T0 + 31_000), 30_000);
  assert.deepEqual(machine.tick(T0 + 61_000), [{ type: 'resumed' }]);
  assert.equal(machine.state, State.SCAN);
  assert.deepEqual(machine.observe(matched(1), T0 + 62_000), [{ type: 'login', profileId: 1 }]);
});

test('deconnexion en pause ou sans session : aucune action serveur', () => {
  assert.deepEqual(machine.logout(T0), []);
  assert.deepEqual(machine.logout(T0 + 1), []);
});

test('pause configurable', () => {
  const short = new SessionMachine({ ...config, logoutPauseSeconds: 5 });
  short.observe(matched(1), T0);
  short.logout(T0);
  assert.deepEqual(short.tick(T0 + 5_000), [{ type: 'resumed' }]);
});

test('echec de connexion ou session rejetee : retour a la reconnaissance', () => {
  machine.observe(matched(1), T0);
  machine.loginFailed();
  assert.equal(machine.state, State.SCAN);
  machine.observe(matched(1), T0 + 1_000);
  machine.sessionLost();
  assert.equal(machine.state, State.SCAN);
  assert.equal(machine.remainingSeconds(T0), 0);
});

test('restauration d\'une session apres rechargement de page', () => {
  machine.restore(4, 420, T0);
  assert.equal(machine.state, State.CONNECTED);
  assert.equal(machine.profileId, 4);
  assert.equal(machine.remainingSeconds(T0), 420);
  assert.deepEqual(machine.tick(T0 + 420_000), [{ type: 'expired' }]);
});

test('configuration des confirmations a 1 : bascule immediate', () => {
  const instant = new SessionMachine({ ...config, switchConfirmations: 1 });
  instant.observe(matched(1), T0);
  assert.deepEqual(instant.observe(matched(2), T0 + 5_000), [{ type: 'login', profileId: 2 }]);
});
