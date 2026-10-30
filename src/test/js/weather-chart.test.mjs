import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  chartModel, drawChart, FUTURE_HOURS, hourWindow, PAST_HOURS, referenceHour, temperatureAxis,
} from '../../main/resources/static/js/widgets/weather.js';

const pad = (value) => String(value).padStart(2, '0');

function hourlyFrom(startDay, days, temperature = (index) => index) {
  const hours = [];
  for (let day = 0; day < days; day += 1) {
    for (let hour = 0; hour < 24; hour += 1) {
      hours.push({
        time: `2026-10-${pad(startDay + day)}T${pad(hour)}:00`,
        temperatureC: temperature(hours.length),
      });
    }
  }
  return hours;
}

const forecast = (overrides = {}) => ({
  testMode: false, utcOffsetSeconds: 3600, currentTime: null, hourly: hourlyFrom(28, 4), ...overrides,
});

// 2026-10-29 13:20 UTC, soit 14 h 20 a Paris (decalage de 3600 s)
const NOW = Date.parse('2026-10-29T13:20:00Z');

test('window constants are 3 hours back and 24 hours ahead', () => {
  assert.equal(PAST_HOURS, 3);
  assert.equal(FUTURE_HOURS, 24);
});

test('the reference hour uses the browser clock shifted by the UTC offset in real mode', () => {
  assert.equal(referenceHour(forecast(), NOW), '2026-10-29T14');
});

test('the reference hour comes from the fixture current time in TEST mode', () => {
  assert.equal(referenceHour(forecast({ testMode: true, currentTime: '2026-10-28T12:00' }), NOW), '2026-10-28T12');
});

test('the window spans three hours before to twenty-four hours after, 28 points', () => {
  const window = hourWindow(forecast().hourly, '2026-10-29T14');

  assert.equal(window.length, 28);
  assert.equal(window[0].time, '2026-10-29T11:00');
  assert.equal(window.at(-1).time, '2026-10-30T14:00');
  assert.equal(window.filter((hour) => hour.isNow).length, 1);
  assert.equal(window[3].isNow, true);
});

test('the window starts at the first available hour early in the night', () => {
  const window = hourWindow(forecast().hourly, '2026-10-28T01');

  assert.equal(window[0].time, '2026-10-28T00:00');
  assert.equal(window.findIndex((hour) => hour.isNow), 1);
  assert.equal(window.length, 1 + 1 + FUTURE_HOURS);
});

test('the window is null when the reference hour is outside the data', () => {
  assert.equal(hourWindow(forecast().hourly, '2027-01-01T10'), null);
});

test('the chart plots one point per hour inside the 640 by 320 frame', () => {
  const model = chartModel(forecast({ hourly: hourlyFrom(28, 4, (index) => 10 + Math.sin(index / 4) * 5) }), NOW);

  assert.equal(model.points.length, 28);
  assert.equal(model.points[0].x, 100);
  assert.equal(model.points.at(-1).x, 620);
  assert.ok(model.points.every((point) => point.y >= 50 && point.y <= 260));
  assert.ok(model.yTicks.length >= 5);
});

test('temperature axis has regularly spaced intermediate Celsius values covering the data', () => {
  const axis = temperatureAxis(11.2, 19.1);

  assert.ok(axis.minimum <= 11.2);
  assert.ok(axis.maximum >= 19.1);
  assert.equal(axis.ticks.length, 5);
  assert.equal(axis.ticks[0].value, axis.minimum);
  assert.equal(axis.ticks.at(-1).value, axis.maximum);
  assert.ok(axis.ticks.every((tick, index) => index === 0 || tick.value > axis.ticks[index - 1].value));
  assert.ok(axis.ticks.slice(1, -1).some((tick) => tick.value > 11.2 && tick.value < 19.1));
});

test('flat temperatures still receive an informative vertical scale', () => {
  const axis = temperatureAxis(10, 10);

  assert.equal(axis.ticks.length, 5);
  assert.ok(axis.minimum < 10);
  assert.ok(axis.maximum > 10);
  assert.ok(axis.ticks.some((tick) => tick.value === 10));
});

test('only the minimum, the maximum and now are labelled and emphasised', () => {
  const model = chartModel(forecast({ hourly: hourlyFrom(28, 4, (index) => (index === 50 ? 25 : index === 55 ? 2 : 10)) }),
    NOW);

  assert.ok(model.labels.length <= 3);
  assert.deepEqual(model.labels.map((label) => label.text).sort(), ['10 °C', '2 °C', '25 °C'].sort());
  assert.equal(model.points.filter((point) => point.emphasis).length, 3);
  assert.equal(model.labels.find((label) => label.text === '2 °C').below, true);
  assert.equal(model.labels.find((label) => label.text === '25 °C').below, false);
});

test('a single label is shown when now is also the maximum', () => {
  // L'heure courante est l'index 38 (29/10 14 h) ; c'est aussi le maximum.
  const model = chartModel(forecast({ hourly: hourlyFrom(28, 4, (index) => (index === 38 ? 30 : 10)) }), NOW);

  assert.equal(model.labels.filter((label) => label.text === '30 °C').length, 1);
  assert.equal(model.labels.length, 2);
});

test('ticks fall on hours that are multiples of six inside the window', () => {
  const model = chartModel(forecast(), NOW);

  assert.deepEqual(model.ticks.map((tick) => tick.text), ['12 h', '18 h', '0 h', '6 h', '12 h']);
});

test('a missing temperature leaves a gap instead of shifting the points', () => {
  const hourly = hourlyFrom(28, 4, (index) => (index === 39 ? null : 10 + (index % 5)));
  const model = chartModel(forecast({ hourly }), NOW);

  assert.equal(model.points[4].y, null);
  assert.equal(model.points[4].temperatureC, null);
  assert.equal(model.points[5].time, '2026-10-29T16:00');
  assert.equal((model.path.match(/M/g) ?? []).length, 2);
});

test('the signature changes with the hour or the data and is stable otherwise', () => {
  const same = chartModel(forecast(), NOW).signature;

  assert.equal(chartModel(forecast(), NOW + 60_000).signature, same);
  assert.notEqual(chartModel(forecast(), NOW + 3_600_000).signature, same);
  assert.notEqual(chartModel(forecast({ hourly: hourlyFrom(28, 4, () => 5) }), NOW).signature, same);
});

test('the accessible label summarises the minimum, the maximum and the current value', () => {
  const model = chartModel(forecast({ hourly: hourlyFrom(28, 4, (index) => (index === 45 ? 3 : 12)) }), NOW);

  assert.match(model.ariaLabel, /minimum 3 °C à 21 h/);
  assert.match(model.ariaLabel, /maximum 12 °C/);
  assert.match(model.ariaLabel, /actuelle 12 °C\.$/);
});

test('there is no chart without hourly data or for an unknown hour', () => {
  assert.equal(chartModel(forecast({ hourly: [] }), NOW), null);
  assert.equal(chartModel({ testMode: false, utcOffsetSeconds: 0 }, NOW), null);
  assert.equal(chartModel(forecast({ hourly: hourlyFrom(28, 4, () => null) }), NOW), null);
});

test('identical data does not redraw until a periodic replay is requested', () => {
  globalThis.document = {
    createElement: () => ({
      className: '', textContent: '', style: {},
      classList: { add() {}, remove() {} },
    }),
    createElementNS: () => ({ attributes: {}, setAttribute(name, value) { this.attributes[name] = value; } }),
  };
  globalThis.window = { matchMedia: () => ({ matches: true }) };
  let replaceCount = 0;
  let attributeCount = 0;
  const yGuides = [];
  const labels = [];
  const line = {
    style: {}, classList: { add() {}, remove() {} },
    setAttribute() { attributeCount += 1; },
    getTotalLength: () => 100,
    getBoundingClientRect() {},
  };
  const elements = {
    svg: { dataset: {}, setAttribute() {} },
    line,
    pointsGroup: { replaceChildren() { replaceCount += 1; }, append() {} },
    yTicksGroup: { replaceChildren() { yGuides.length = 0; }, append(element) { yGuides.push(element); } },
    labelLayer: { replaceChildren() { labels.length = 0; }, append(element) { labels.push(element); } },
  };
  const model = chartModel(forecast(), NOW);

  drawChart(elements, model, false);
  const initialReplacements = replaceCount;
  const initialAttributes = attributeCount;
  assert.equal(yGuides.length, model.yTicks.length);
  assert.equal(labels.filter((label) => label.className === 'weather-chart-y-label').length, model.yTicks.length);
  assert.ok(labels.every((label) => label.className !== 'weather-chart-y-label' || label.textContent.endsWith('°')));
  drawChart(elements, model, false);
  assert.equal(replaceCount, initialReplacements);
  assert.equal(attributeCount, initialAttributes);

  drawChart(elements, model, true);
  assert.equal(replaceCount, initialReplacements + 1);
});
