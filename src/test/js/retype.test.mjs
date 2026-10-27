import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  nextRetypeDelay, RETYPE_MAX_MS, RETYPE_MIN_MS, startRetypeLoop,
} from '../../main/resources/static/js/retype.js';

function fakeScheduler() {
  const queue = [];
  return {
    queue,
    schedule: (fn, ms) => { queue.push({ fn, ms }); return queue.length; },
    cancel: (id) => { queue[id - 1].cancelled = true; },
    async fire() {
      const entry = queue.find((item) => !item.done && !item.cancelled);
      entry.done = true;
      await entry.fn();
    },
    pending: () => queue.filter((item) => !item.done && !item.cancelled).length,
  };
}

test('delay is bounded between 15 s and 60 s inclusive', () => {
  assert.equal(nextRetypeDelay(() => 0), RETYPE_MIN_MS);
  assert.equal(nextRetypeDelay(() => 0.999999999), RETYPE_MAX_MS);
});

test('loop runs the task and re-arms with a bounded delay', async () => {
  const scheduler = fakeScheduler();
  let runs = 0;
  startRetypeLoop({ run: () => { runs += 1; }, ...scheduler });

  await scheduler.fire();
  await scheduler.fire();

  assert.equal(runs, 2);
  assert.equal(scheduler.pending(), 1);
  assert.ok(scheduler.queue.every(({ ms }) => ms >= RETYPE_MIN_MS && ms <= RETYPE_MAX_MS));
});

test('paused cycle skips the task but is re-armed', async () => {
  const scheduler = fakeScheduler();
  let runs = 0;
  startRetypeLoop({ run: () => { runs += 1; }, isPaused: () => true, ...scheduler });

  await scheduler.fire();

  assert.equal(runs, 0);
  assert.equal(scheduler.pending(), 1);
});

test('loop stops when inactive or when stop is called', async () => {
  const inactive = fakeScheduler();
  startRetypeLoop({ run: () => {}, isActive: () => false, ...inactive });
  await inactive.fire();
  assert.equal(inactive.pending(), 0);

  const manual = fakeScheduler();
  const loop = startRetypeLoop({ run: () => {}, ...manual });
  loop.stop();
  assert.equal(manual.pending(), 0);
});

test('a failing task does not break the loop', async () => {
  const scheduler = fakeScheduler();
  startRetypeLoop({ run: () => { throw new Error('boom'); }, ...scheduler });

  await scheduler.fire();

  assert.equal(scheduler.pending(), 1);
});
