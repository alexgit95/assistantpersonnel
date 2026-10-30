import assert from 'node:assert/strict';
import test from 'node:test';
import { firstFreeSlot, toggleWidget, UNASSIGNED_SLOT } from '../../main/resources/static/js/configuration-state.js';

const app = (key, position) => ({ key, enabled: position >= 0, position, configJson: '{}' });
const placed = (preferences, key) => preferences.find((entry) => entry.key === key);

test('the first free slot is used, in slot order', () => {
  assert.equal(firstFreeSlot([app('a', 0), app('b', UNASSIGNED_SLOT)]), 1);
  assert.equal(firstFreeSlot([app('a', 0), app('b', 1), app('c', 3)]), 2);
  assert.equal(firstFreeSlot([]), 0);
});

test('when every slot is taken the main slot is proposed', () => {
  const full = [app('a', 0), app('b', 1), app('c', 2), app('d', 3), app('e', 4)];
  assert.equal(firstFreeSlot(full), 0);
});

test('switching a widget on places it in a free slot', () => {
  const result = toggleWidget([app('a', 0), app('b', UNASSIGNED_SLOT)], 'b', true);
  assert.deepEqual({ enabled: placed(result, 'b').enabled, position: placed(result, 'b').position },
    { enabled: true, position: 1 });
  assert.equal(placed(result, 'a').position, 0);
});

test('switching a widget off sends it back to the pool without touching the others', () => {
  const result = toggleWidget([app('a', 0), app('b', 1)], 'a', false);
  assert.deepEqual({ enabled: placed(result, 'a').enabled, position: placed(result, 'a').position },
    { enabled: false, position: UNASSIGNED_SLOT });
  assert.equal(placed(result, 'b').position, 1);
});

test('switching on a widget that is already placed keeps its slot', () => {
  const result = toggleWidget([app('a', 3)], 'a', true);
  assert.equal(placed(result, 'a').position, 3);
});

test('with all slots taken the new widget replaces the main one, which goes back to the pool', () => {
  const full = [app('a', 0), app('b', 1), app('c', 2), app('d', 3), app('e', 4), app('f', UNASSIGNED_SLOT)];
  const result = toggleWidget(full, 'f', true);
  assert.equal(placed(result, 'f').position, 0);
  assert.equal(placed(result, 'a').enabled, false);
});

test('the original preferences are never mutated', () => {
  const original = [app('a', 0), app('b', UNASSIGNED_SLOT)];
  toggleWidget(original, 'b', true);
  assert.equal(original[1].enabled, false);
});

test('an unknown widget is refused', () => {
  assert.throws(() => toggleWidget([app('a', 0)], 'zzz', true), RangeError);
});
