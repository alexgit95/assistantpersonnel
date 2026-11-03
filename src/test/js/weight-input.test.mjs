import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  defaultWeightValue, formatKg, INVALID_FORMAT_MESSAGE, localDateValue, OUT_OF_RANGE_MESSAGE, parseWeight,
} from '../../main/resources/static/js/weight-input.js';

test('accepts the values the backend accepts, with a comma or a dot', () => {
  assert.deepEqual(parseWeight('72,5'), { ok: true, kgTenths: 725, value: '72,5' });
  assert.deepEqual(parseWeight(' 72.5 '), { ok: true, kgTenths: 725, value: '72.5' });
  assert.equal(parseWeight('80').kgTenths, 800);
  assert.equal(parseWeight('20,0').ok, true);
  assert.equal(parseWeight('400').ok, true);
});

test('refuses more than one decimal and non numeric input', () => {
  for (const text of ['72,55', '72,', ',5', 'abc', '', '  ', '7 2', '-72', '1e2', '1000', '72,5kg']) {
    assert.deepEqual(parseWeight(text), { ok: false, message: INVALID_FORMAT_MESSAGE }, text);
  }
  assert.equal(parseWeight(null).ok, false);
  assert.equal(parseWeight(undefined).ok, false);
});

test('refuses values outside 20,0 and 400,0 kg', () => {
  for (const text of ['19,9', '5', '0', '400,1', '999']) {
    assert.deepEqual(parseWeight(text), { ok: false, message: OUT_OF_RANGE_MESSAGE }, text);
  }
});

test('floating point rounding never shifts a tenth', () => {
  assert.equal(parseWeight('72,3').kgTenths, 723);
  assert.equal(parseWeight('81,7').kgTenths, 817);
});

test('weights are shown in French notation with one decimal', () => {
  assert.equal(formatKg(725), '72,5');
  assert.equal(formatKg(800), '80,0');
});

test('the form is pre-filled with the latest measure or left empty', () => {
  assert.equal(defaultWeightValue([{ kgTenths: 731 }, { kgTenths: 700 }]), '73,1');
  assert.equal(defaultWeightValue([]), '');
  assert.equal(defaultWeightValue(undefined), '');
});

test('the date of the day uses local time and zero padding', () => {
  assert.equal(localDateValue(new Date(2026, 0, 5, 23, 59)), '2026-01-05');
  assert.equal(localDateValue(new Date(2026, 11, 31, 0, 0)), '2026-12-31');
});
