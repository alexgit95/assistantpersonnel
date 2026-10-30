import assert from 'node:assert/strict';
import { test } from 'node:test';
import { createQrTicket } from '../../main/resources/static/js/qr-ticket.js';

function setup({ ttlSeconds = 120, issueFailure = null } = {}) {
  const clock = { now: 1_000_000 };
  const calls = { issue: 0, poll: [], shown: [], cleared: 0 };
  const status = {};
  const ticket = createQrTicket({
    now: () => clock.now,
    issue: async () => {
      calls.issue += 1;
      if (issueFailure) {
        throw issueFailure;
      }
      return { qrId: `qr${calls.issue}`, url: `http://pi/handoff?t=${calls.issue}`, expiresInSeconds: ttlSeconds };
    },
    poll: async (ids) => {
      calls.poll.push(ids);
      return { qr: ids.map((qrId) => ({ qrId, state: status[qrId] ?? 'ACTIVE' })) };
    },
    show: async (url) => calls.shown.push(url),
    clear: () => { calls.cleared += 1; },
  });
  return { clock, calls, status, ticket };
}

test('the first tick issues and shows a QR without polling', async () => {
  const { calls, ticket } = setup();
  await ticket.tick();
  assert.equal(calls.issue, 1);
  assert.deepEqual(calls.shown, ['http://pi/handoff?t=1']);
  assert.deepEqual(calls.poll, []);
  assert.equal(ticket.current.qrId, 'qr1');
});

test('an active QR is kept and its id is sent to the server', async () => {
  const { calls, clock, ticket } = setup();
  await ticket.tick();
  clock.now += 30_000;
  await ticket.tick();
  assert.equal(calls.issue, 1);
  assert.deepEqual(calls.poll, [['qr1']]);
});

test('a consumed QR is replaced at the next tick', async () => {
  const { calls, status, ticket } = setup();
  await ticket.tick();
  status.qr1 = 'CONSUMED';
  await ticket.tick();
  assert.equal(calls.issue, 2);
  assert.equal(ticket.current.qrId, 'qr2');
  assert.equal(calls.shown.length, 2);
});

test('an expired QR is replaced too', async () => {
  const { calls, status, ticket } = setup();
  await ticket.tick();
  status.qr1 = 'EXPIRED';
  await ticket.tick();
  assert.equal(calls.issue, 2);
});

test('a new QR is requested 15 seconds before the expiry', async () => {
  const { calls, clock, ticket } = setup({ ttlSeconds: 120 });
  await ticket.tick();
  clock.now += 104_000;
  await ticket.tick();
  assert.equal(calls.issue, 1);
  clock.now += 1_000;
  await ticket.tick();
  assert.equal(calls.issue, 2);
});

test('QR disabled on the server (503) is not retried for a minute and clears the display', async () => {
  const { calls, clock, ticket } = setup({ issueFailure: Object.assign(new Error('off'), { status: 503 }) });
  await ticket.tick();
  assert.equal(calls.cleared, 1);
  assert.equal(ticket.current, null);
  await ticket.tick();
  assert.equal(calls.issue, 1);
  clock.now += 60_001;
  await ticket.tick();
  assert.equal(calls.issue, 2);
});

test('a lost session (401) clears the display without raising', async () => {
  const { calls, ticket } = setup({ issueFailure: Object.assign(new Error('401'), { status: 401 }) });
  await ticket.tick();
  assert.equal(calls.cleared, 1);
});

test('other failures are raised to the caller and leave no stale QR', async () => {
  const { ticket } = setup({ issueFailure: Object.assign(new Error('boom'), { status: 500 }) });
  await assert.rejects(ticket.tick(), /boom/);
  assert.equal(ticket.current, null);
});

test('reset forgets the QR and clears the display', async () => {
  const { calls, ticket } = setup();
  await ticket.tick();
  ticket.reset();
  assert.equal(ticket.current, null);
  assert.equal(calls.cleared, 1);
});
