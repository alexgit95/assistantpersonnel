import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  captureStatus, CANCELLED_MESSAGE, enrollStep, enrollTitle, endMessage, pollDelay, POLL_ACTIVE_MS, POLL_IDLE_MS,
  progressLabel, RECAPTURE_BUSY_MESSAGE, rejectionText, SESSION_LOST_MESSAGE,
} from '../../main/resources/static/js/mobile-enroll-flow.js';
import { GUIDANCE } from '../../main/resources/static/js/remote-capture.js';

test('the title follows the mode of the capture session', () => {
  assert.equal(enrollTitle('CREATE'), 'Créer mon profil');
  assert.equal(enrollTitle('RECAPTURE'), 'Refaire mes captures');
  assert.match(RECAPTURE_BUSY_MESSAGE, /déjà en cours/);
});

test('each server state maps to one screen', () => {
  assert.equal(enrollStep('WAITING'), 'start');
  assert.equal(enrollStep('COUNTDOWN'), 'capture');
  assert.equal(enrollStep('CAPTURING'), 'capture');
  assert.equal(enrollStep('REVIEW'), 'review');
  assert.equal(enrollStep('DONE'), 'done');
  assert.equal(enrollStep('CANCELLED'), 'ended');
  assert.equal(enrollStep('EXPIRED'), 'ended');
  assert.equal(enrollStep('unknown'), 'ended');
});

test('polling is fast during the capture only', () => {
  assert.equal(pollDelay('COUNTDOWN'), POLL_ACTIVE_MS);
  assert.equal(pollDelay('CAPTURING'), POLL_ACTIVE_MS);
  assert.equal(pollDelay('WAITING'), POLL_IDLE_MS);
  assert.equal(pollDelay('REVIEW'), POLL_IDLE_MS);
});

test('progress shows the number of photos taken', () => {
  assert.equal(progressLabel(3, 8), '3 / 8 photos');
});

test('the first countdown tells the user to face the screen', () => {
  const snapshot = { state: 'COUNTDOWN', done: 0, nextShotAtMs: 15_000, guidanceIndex: 0 };
  const status = captureStatus(snapshot, 10_000, 4_000);
  assert.match(status.countdown, /dans 1 s/);
  assert.match(status.countdown, /face à l’écran/);
  assert.equal(status.guidance, GUIDANCE[0]);
});

test('later shots show the countdown, then the shooting message', () => {
  const snapshot = { state: 'CAPTURING', done: 2, nextShotAtMs: 20_000, guidanceIndex: 2 };
  assert.equal(captureStatus(snapshot, 0, 16_500).countdown, 'Prochaine photo dans 4 s.');
  assert.equal(captureStatus(snapshot, 0, 20_000).countdown, 'Photo en cours…');
  assert.equal(captureStatus(snapshot, 0, 16_500).guidance, GUIDANCE[2]);
});

test('rejections and abandon are explained, otherwise nothing is shown', () => {
  assert.match(rejectionText({ lastRejection: 'tooSmall' }), /Rapprochez-vous/);
  assert.match(rejectionText({ lastRejection: 'abandoned' }), /abandonnée/);
  assert.equal(rejectionText({ lastRejection: null }), '');
});

test('end messages distinguish success, cancellation and expiry', () => {
  assert.equal(endMessage('DONE'), 'C’est terminé.');
  assert.equal(endMessage('CANCELLED'), CANCELLED_MESSAGE);
  assert.equal(endMessage('EXPIRED'), SESSION_LOST_MESSAGE);
});
