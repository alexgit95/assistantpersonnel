import test from 'node:test';
import assert from 'node:assert/strict';
import { iconId, windLabel } from '../../main/resources/static/js/widgets/weather-icons.js';

test('maps WMO codes to the monochrome sprite symbols', () => {
  assert.equal(iconId(0), 'sun');
  assert.equal(iconId(1), 'sun');
  assert.equal(iconId(2), 'partly');
  assert.equal(iconId(3), 'cloud');
  assert.equal(iconId(45), 'fog');
  assert.equal(iconId(53), 'rain');
  assert.equal(iconId(61), 'rain');
  assert.equal(iconId(82), 'rain');
  assert.equal(iconId(73), 'snow');
  assert.equal(iconId(86), 'snow');
  assert.equal(iconId(95), 'storm');
});

test('falls back to a neutral symbol for unknown codes', () => {
  assert.equal(iconId(-1), 'unknown');
  assert.equal(iconId(1234), 'unknown');
  assert.equal(iconId(undefined), 'unknown');
});

test('labels wind gusts in French', () => {
  assert.equal(windLabel(62), "Rafales jusqu'à 62 km/h");
});
