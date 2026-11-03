import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  ACTIVE_STATES, assessmentToReason, clockOffset, GUIDANCE, guidanceFor, isActionable, msUntilShot, nextAction,
  qrNeedsRenewal, rejectionMessage, secondsLeft,
} from '../../main/resources/static/js/remote-capture.js';

test('guidance follows the capture number and starts over when there are more photos than texts', () => {
  assert.equal(guidanceFor(0), GUIDANCE[0]);
  assert.equal(guidanceFor(3), GUIDANCE[3]);
  assert.equal(guidanceFor(GUIDANCE.length), GUIDANCE[0]);
  assert.equal(guidanceFor(GUIDANCE.length + 2), GUIDANCE[2]);
  assert.equal(guidanceFor(-1), GUIDANCE[0]);
  assert.equal(guidanceFor(undefined), GUIDANCE[0]);
});

test('every rejection reason has a French message and unknown ones fall back', () => {
  for (const reason of ['none', 'tooSmall', 'duplicate', 'ambiguous', 'multiple', 'abandoned', 'other']) {
    assert.ok(rejectionMessage(reason).length > 10, reason);
  }
  assert.equal(rejectionMessage('whatever'), rejectionMessage('other'));
});

test('enrollment assessment statuses map to the reasons understood by the server', () => {
  assert.equal(assessmentToReason('none'), 'none');
  assert.equal(assessmentToReason('tooSmall'), 'tooSmall');
  assert.equal(assessmentToReason('duplicate'), 'duplicate');
  assert.equal(assessmentToReason('ambiguous'), 'ambiguous');
  assert.equal(assessmentToReason('error'), 'other');
});

test('countdown is computed from the server clock reconstructed with the offset', () => {
  const offset = clockOffset(10_000, 4_000);
  assert.equal(offset, 6_000);
  const capture = { nextShotAtMs: 15_000 };
  assert.equal(msUntilShot(capture, offset, 4_000), 5_000);
  assert.equal(msUntilShot(capture, offset, 9_000), 0);
  assert.equal(secondsLeft(5_000), 5);
  assert.equal(secondsLeft(4_001), 5);
  assert.equal(secondsLeft(0), 0);
  assert.equal(secondsLeft(-300), 0);
});

test('nextAction waits for the countdown, shoots when due and never while a shot is in flight', () => {
  const capture = { state: 'COUNTDOWN', nextShotAtMs: 10_000 };
  assert.equal(nextAction(capture, 0, 5_000), 'wait');
  assert.equal(nextAction(capture, 0, 10_000), 'shoot');
  assert.equal(nextAction(capture, 0, 10_000, { inFlight: true }), 'wait');
  assert.equal(nextAction({ ...capture, state: 'CAPTURING' }, 0, 12_000), 'shoot');
});

test('a failed shot is retried only after a pause', () => {
  const capture = { state: 'CAPTURING', nextShotAtMs: 10_000 };
  assert.equal(nextAction(capture, 0, 10_500, { lastShotAt: 10_000 }), 'wait');
  assert.equal(nextAction(capture, 0, 11_600, { lastShotAt: 10_000 }), 'shoot');
});

test('nextAction does nothing outside an active capture', () => {
  for (const state of ['WAITING', 'REVIEW', 'DONE', 'CANCELLED', 'EXPIRED']) {
    assert.equal(nextAction({ state, nextShotAtMs: 0 }, 0, 99_999), 'none', state);
  }
  assert.equal(nextAction(null, 0, 1), 'none');
  assert.deepEqual([...ACTIVE_STATES].sort(), ['CAPTURING', 'COUNTDOWN']);
});

test('a QR is renewed when missing, consumed, expired or about to expire', () => {
  const entry = { expiresAtClientMs: 100_000 };
  assert.equal(qrNeedsRenewal(null, undefined, 0), true);
  assert.equal(qrNeedsRenewal(entry, 'ACTIVE', 50_000), false);
  assert.equal(qrNeedsRenewal(entry, undefined, 50_000), false);
  assert.equal(qrNeedsRenewal(entry, 'CONSUMED', 50_000), true);
  assert.equal(qrNeedsRenewal(entry, 'EXPIRED', 50_000), true);
  assert.equal(qrNeedsRenewal(entry, 'ACTIVE', 85_000), true);
  assert.equal(qrNeedsRenewal(entry, 'ACTIVE', 84_999), false);
});

test('an enrollment capture is played only while the Pi is a guest', () => {
  const capture = { mode: 'CREATE', state: 'COUNTDOWN' };
  assert.equal(isActionable(capture, false, null), true);
  assert.equal(isActionable(capture, true, 5), false);
  assert.equal(isActionable({ ...capture, state: 'REVIEW' }, false, null), false);
  assert.equal(isActionable(null, false, null), false);
});

test('a recapture is played only for the profile connected on the Pi', () => {
  const capture = { mode: 'RECAPTURE', state: 'CAPTURING', profileId: 5 };
  assert.equal(isActionable(capture, true, 5), true);
  assert.equal(isActionable(capture, true, 6), false);
  assert.equal(isActionable(capture, false, null), false);
  assert.equal(isActionable(capture, true, null), false);
});
