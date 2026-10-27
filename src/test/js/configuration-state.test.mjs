import test from 'node:test';
import assert from 'node:assert/strict';
import { moveWidget, toAppSelections, UNASSIGNED_SLOT } from '../../main/resources/static/js/configuration-state.js';

const apps = [
  { key: 'weight', enabled: true, position: 0, configJson: '{}' },
  { key: 'weather', enabled: false, position: UNASSIGNED_SLOT, configJson: '{}' },
];

test('places an available widget and returns an occupied source slot to the pool', () => {
  const moved = moveWidget(apps, 'weather', 0);

  assert.deepEqual(moved.map(({ key, enabled, position }) => ({ key, enabled, position })), [
    { key: 'weight', enabled: false, position: UNASSIGNED_SLOT },
    { key: 'weather', enabled: true, position: 0 },
  ]);
});

test('swaps two assigned widgets when a card is dropped on an occupied slot', () => {
  const placed = moveWidget(apps, 'weather', 1);
  const swapped = moveWidget(placed, 'weight', 1);

  assert.equal(swapped.find((app) => app.key === 'weight').position, 1);
  assert.equal(swapped.find((app) => app.key === 'weather').position, 0);
});

test('moves a placed widget back to the available pool', () => {
  const moved = moveWidget(apps, 'weight', UNASSIGNED_SLOT);

  assert.equal(moved[0].enabled, false);
  assert.equal(moved[0].position, UNASSIGNED_SLOT);
});

test('serializes every profile app with -1 for unassigned widgets', () => {
  assert.deepEqual(toAppSelections(apps), [
    { key: 'weight', enabled: true, position: 0, configJson: '{}' },
    { key: 'weather', enabled: false, position: UNASSIGNED_SLOT, configJson: '{}' },
  ]);
});