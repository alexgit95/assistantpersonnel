import test from 'node:test';
import assert from 'node:assert/strict';
import { chartCoordinates, formatWeight } from '../../main/resources/static/js/widgets/weight.js';

test('formats weight labels in French with one decimal place', () => {
  assert.equal(formatWeight(725), '72,5 kg');
});

test('plots recent entries chronologically from the oldest to newest value', () => {
  const entries = [
    { entryDate: '2026-10-27', kgTenths: 725 },
    { entryDate: '2026-10-26', kgTenths: 710 },
    { entryDate: '2026-10-25', kgTenths: 700 },
  ];

  const points = chartCoordinates(entries);

  assert.deepEqual(points.map((point) => point.entry.entryDate), [
    '2026-10-25', '2026-10-26', '2026-10-27',
  ]);
  assert.equal(points[0].x, 40);
  assert.equal(points.at(-1).x, 620);
  assert.equal(points[0].y, 260);
  assert.equal(points.at(-1).y, 50);
});

test('centers a single chart entry and handles an empty history', () => {
  assert.deepEqual(chartCoordinates([]), []);
  const [point] = chartCoordinates([{ entryDate: '2026-10-27', kgTenths: 725 }]);
  assert.equal(point.x, 330);
  assert.equal(point.y, 260);
});