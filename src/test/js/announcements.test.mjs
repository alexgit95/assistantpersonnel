import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  announcementLabel, countdownParts, countdownProgress, formatCountdown, nextRetypeDelay,
} from '../../main/resources/static/js/widgets/announcements.js';

const at = (iso) => Date.parse(iso);

test('progress goes from 0 to 1 between start and end and is clamped', () => {
  assert.equal(countdownProgress(50, 0, 100), 0.5);
  assert.equal(countdownProgress(-10, 0, 100), 0);
  assert.equal(countdownProgress(150, 0, 100), 1);
  assert.equal(countdownProgress(5, 10, 10), 1);
});

test('label shows the Paris end date and time only without countdown', () => {
  const endsAt = at('2026-10-27T17:00:00Z');

  assert.equal(announcementLabel({ title: 'Fête', endsAt, countdown: false }),
    'Fête \u00b7 fin le 27/10/2026 \u00e0 18:00');
  assert.equal(announcementLabel({ title: 'Fête', endsAt, countdown: true }), 'Fête');
});

test('countdown shows seconds only when less than a minute remains', () => {
  const now = at('2026-10-27T10:00:00Z');
  const parts = countdownParts(now, now + 42_000);

  assert.deepEqual(parts, { months: 0, days: 0, hours: 0, minutes: 0, seconds: 42 });
  assert.equal(formatCountdown(parts), '42 s');
});

test('leading zero units are hidden but later zero units stay', () => {
  const parts = { months: 0, days: 2, hours: 0, minutes: 5, seconds: 3 };

  assert.equal(formatCountdown(parts), '2 j 0 h 5 min 3 s');
  assert.equal(formatCountdown({ months: 1, days: 0, hours: 0, minutes: 0, seconds: 0 }),
    '1 mois 0 j 0 h 0 min 0 s');
  assert.equal(formatCountdown({ months: 0, days: 0, hours: 0, minutes: 0, seconds: 0 }), '0 s');
});

test('months are calendar months in Paris time, not 30 days', () => {
  const now = at('2026-01-15T11:00:00Z');
  const end = at('2026-03-15T11:00:00Z');
  const parts = countdownParts(now, end);

  assert.equal(parts.months, 2);
  assert.equal(parts.days, 0);
  assert.equal(parts.hours, 0);
});

test('month length varies: one month from 15 February is 28 days', () => {
  const now = at('2026-02-15T11:00:00Z');
  const parts = countdownParts(now, at('2026-03-14T11:00:00Z'));

  assert.equal(parts.months, 0);
  assert.equal(parts.days, 27);
  assert.equal(countdownParts(now, at('2026-03-15T11:00:00Z')).months, 1);
});

test('remaining time is exact across the spring DST change in Paris', () => {
  const now = at('2026-03-28T12:00:00Z');
  const end = at('2026-03-29T12:00:00Z');
  const parts = countdownParts(now, end);

  assert.equal(parts.days * 24 + parts.hours, 24);
  assert.equal(parts.minutes, 0);
});

test('countdown is null once the end is reached or passed', () => {
  const now = at('2026-10-27T10:00:00Z');

  assert.equal(countdownParts(now, now), null);
  assert.equal(countdownParts(now, now - 1), null);
});

test('retype delay stays between 15 and 60 seconds', () => {
  assert.equal(nextRetypeDelay(() => 0), 15_000);
  assert.equal(nextRetypeDelay(() => 0.999999999), 60_000);
  const middle = nextRetypeDelay(() => 0.5);
  assert.ok(middle > 15_000 && middle < 60_000);
});
