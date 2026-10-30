import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';
import { startRetypeLoop } from '../retype.js';
import { createIcon, iconId, windLabel } from './weather-icons.js';

const formatDay = (value, options) => new Intl.DateTimeFormat('fr-FR', options)
  .format(new Date(`${value}T12:00:00`));

const formatTemperature = (value) => `${Math.round(value).toLocaleString('fr-FR')} °C`;
const formatAxisValue = (value) => new Intl.NumberFormat('fr-FR', { maximumFractionDigits: 2 }).format(value);

export const PAST_HOURS = 3;
export const FUTURE_HOURS = 24;
const MIN_CHART_HEIGHT_PX = 56;
const SVG_NS = 'http://www.w3.org/2000/svg';
const CHART_LEFT = 100;
const CHART_RIGHT = 620;
const CHART_TOP = 50;
const CHART_BOTTOM = 260;

/** Heure pleine de référence ("yyyy-MM-ddTHH") : horloge du navigateur décalée, ou heure du fixture en mode TEST. */
export function referenceHour(forecast, nowMs = Date.now()) {
  if (forecast.testMode && forecast.currentTime) return forecast.currentTime.slice(0, 13);
  return new Date(nowMs + (forecast.utcOffsetSeconds ?? 0) * 1000).toISOString().slice(0, 13);
}

/** Heures de référence − 3 h à référence + 24 h, bornées aux données disponibles. */
export function hourWindow(hourly, reference) {
  const index = hourly.findIndex((hour) => hour.time.slice(0, 13) === reference);
  if (index < 0) return null;
  const start = Math.max(0, index - PAST_HOURS);
  const end = Math.min(hourly.length - 1, index + FUTURE_HOURS);
  return hourly.slice(start, end + 1).map((hour, offset) => ({ ...hour, isNow: start + offset === index }));
}

const round1 = (value) => Number(value.toFixed(1));

function niceStep(value) {
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const fraction = value / magnitude;
  const niceFraction = fraction <= 1 ? 1 : fraction <= 2 ? 2 : fraction <= 5 ? 5 : 10;
  return niceFraction * magnitude;
}

export function temperatureAxis(minimum, maximum) {
  const step = niceStep(Math.max(1, maximum - minimum) / 4);
  let axisMinimum = Math.floor(minimum / step) * step;
  let axisMaximum = Math.ceil(maximum / step) * step;
  while (Math.round((axisMaximum - axisMinimum) / step) < 4) {
    axisMinimum -= step;
    axisMaximum += step;
  }
  const ticks = Array.from({ length: 5 }, (_, index) => {
    const value = Number((axisMinimum + ((axisMaximum - axisMinimum) * index) / 4).toFixed(2));
    return {
      value,
      y: round1(CHART_BOTTOM - ((value - axisMinimum) / (axisMaximum - axisMinimum))
        * (CHART_BOTTOM - CHART_TOP)),
      text: `${formatAxisValue(value)}°`,
    };
  });
  return { minimum: axisMinimum, maximum: axisMaximum, ticks };
}

export function chartModel(forecast, nowMs = Date.now()) {
  const reference = referenceHour(forecast, nowMs);
  const window = hourWindow(forecast.hourly ?? [], reference);
  const values = window?.map((hour) => hour.temperatureC).filter((value) => value !== null && value !== undefined);
  if (!window || values.length === 0) return null;
  const min = Math.min(...values);
  const max = Math.max(...values);
  const axis = temperatureAxis(min, max);
  const points = window.map((hour, index) => ({
    time: hour.time,
    temperatureC: hour.temperatureC ?? null,
    isNow: hour.isNow,
    x: round1(window.length === 1 ? (CHART_LEFT + CHART_RIGHT) / 2
      : CHART_LEFT + ((CHART_RIGHT - CHART_LEFT) * index) / (window.length - 1)),
    y: hour.temperatureC == null ? null : round1(CHART_BOTTOM
      - ((hour.temperatureC - axis.minimum) / (axis.maximum - axis.minimum)) * (CHART_BOTTOM - CHART_TOP)),
  }));
  const minIndex = points.findIndex((point) => point.temperatureC === min);
  const maxIndex = points.findIndex((point) => point.temperatureC === max);
  const nowIndex = points.findIndex((point) => point.isNow);
  points.forEach((point, index) => {
    point.emphasis = index === minIndex || index === maxIndex || index === nowIndex;
  });
  const labelIndexes = [...new Set([maxIndex, nowIndex, minIndex])].filter((index) => index >= 0
    && points[index].temperatureC !== null);
  const labels = labelIndexes.map((index) => ({
    x: points[index].x,
    y: points[index].y,
    text: formatTemperature(points[index].temperatureC),
    below: index === minIndex && index !== maxIndex && index !== nowIndex,
    start: index === 0,
    end: index === points.length - 1,
  }));
  const ticks = points.filter((point) => Number(point.time.slice(11, 13)) % 6 === 0)
    .map((point) => ({ x: point.x, text: `${Number(point.time.slice(11, 13))} h` }));
  let path = '';
  let open = false;
  for (const point of points) {
    if (point.y === null) {
      open = false;
    } else {
      path += `${open ? 'L' : 'M'}${point.x} ${point.y}`;
      open = true;
    }
  }
  const hourOf = (index) => `${Number(points[index].time.slice(11, 13))} h`;
  return {
    points,
    labels,
    ticks,
    yTicks: axis.ticks,
    path,
    signature: `${reference}|${JSON.stringify(points.map((point) => point.temperatureC))}`,
    ariaLabel: `Températures horaires (axe vertical de ${formatAxisValue(axis.minimum)} à `
      + `${formatAxisValue(axis.maximum)} °C) : minimum ${formatTemperature(min)} à ${hourOf(minIndex)}, `
      + `maximum ${formatTemperature(max)} à ${hourOf(maxIndex)}`
      + (nowIndex >= 0 && points[nowIndex].temperatureC !== null
        ? `, actuelle ${formatTemperature(points[nowIndex].temperatureC)}.` : '.'),
  };
}

export function drawChart(elements, model, replay) {
  const { svg, line, pointsGroup, labelLayer, yTicksGroup } = elements;
  if (svg.dataset.signature === model.signature && !replay) return;
  svg.dataset.signature = model.signature;
  pointsGroup.replaceChildren();
  yTicksGroup.replaceChildren();
  labelLayer.replaceChildren();
  line.setAttribute('d', model.path);
  svg.setAttribute('aria-label', model.ariaLabel);
  for (const point of model.points) {
    if (point.y === null) continue;
    const circle = document.createElementNS(SVG_NS, 'circle');
    circle.setAttribute('cx', String(point.x));
    circle.setAttribute('cy', String(point.y));
    circle.setAttribute('r', point.emphasis ? '5' : '3');
    pointsGroup.append(circle);
  }
  for (const label of model.labels) {
    const span = document.createElement('span');
    span.className = 'weather-chart-label';
    if (label.below) span.classList.add('is-below');
    if (label.start) span.classList.add('align-start');
    if (label.end) span.classList.add('align-end');
    span.textContent = label.text;
    span.style.left = `${(label.x / 640) * 100}%`;
    span.style.top = `${(label.y / 320) * 100}%`;
    labelLayer.append(span);
  }
  for (const tick of model.ticks) {
    const span = document.createElement('span');
    span.className = 'weather-chart-tick';
    span.textContent = tick.text;
    span.style.left = `${(tick.x / 640) * 100}%`;
    labelLayer.append(span);
  }
  for (const tick of model.yTicks) {
    const guide = document.createElementNS(SVG_NS, 'line');
    guide.setAttribute('x1', String(CHART_LEFT));
    guide.setAttribute('y1', String(tick.y));
    guide.setAttribute('x2', String(CHART_RIGHT));
    guide.setAttribute('y2', String(tick.y));
    yTicksGroup.append(guide);
    const label = document.createElement('span');
    label.className = 'weather-chart-y-label';
    label.textContent = tick.text;
    label.style.top = `${(tick.y / 320) * 100}%`;
    labelLayer.append(label);
  }
  const length = line.getTotalLength();
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  line.classList.remove('is-drawing');
  line.style.strokeDasharray = String(length);
  line.style.strokeDashoffset = reducedMotion ? '0' : String(length);
  if (!reducedMotion) {
    void line.getBoundingClientRect();
    line.classList.add('is-drawing');
  }
}

function partLabel(name, part) {
  return `${name} : ${part.description}`;
}

function fillIcon(target, code, label) {
  target.replaceChildren(createIcon(iconId(code), label));
}

function fillWind(target, day) {
  if (!day.windAlert) {
    target.replaceChildren();
    return;
  }
  target.replaceChildren(createIcon('wind', windLabel(day.maxGustKmh)));
}

function dayRow(day, writing) {
  const item = document.createElement('li');
  const name = document.createElement('strong');
  typeText(name, formatDay(day.date, { weekday: 'short', day: 'numeric' }), writing);
  item.append(name);
  for (const [label, part] of [['Matin', day.morning], ['Après-midi', day.afternoon]]) {
    const slot = document.createElement('span');
    slot.className = 'weather-slot';
    const temperature = document.createElement('span');
    typeText(temperature, formatTemperature(part.temperatureC), writing);
    slot.append(createIcon(iconId(part.code), partLabel(label, part)), temperature);
    item.append(slot);
  }
  const wind = document.createElement('span');
  wind.className = 'weather-wind';
  fillWind(wind, day);
  item.append(wind);
  return item;
}

export async function initialize(container, configJson, typingOptions = {}, { slotIndex = 0 } = {}) {
  const root = container.querySelector('[data-weather-widget]');
  if (!root) return;
  const compact = slotIndex !== 0;
  root.classList.toggle('is-compact', compact);

  const status = root.querySelector('[data-weather-status]');
  const current = root.querySelector('[data-weather-current]');
  const locationHeading = root.querySelector('[data-weather-location]');
  const now = root.querySelector('[data-weather-now]');
  const dateLabel = root.querySelector('[data-weather-date]');
  const rain = root.querySelector('[data-weather-rain]');
  const wind = root.querySelector('[data-weather-wind]');
  const advice = root.querySelector('[data-weather-advice]');
  const icons = {
    morning: root.querySelector('[data-weather-icon="morning"]'),
    afternoon: root.querySelector('[data-weather-icon="afternoon"]'),
  };
  const temperatures = {
    morning: root.querySelector('[data-weather-temp="morning"]'),
    afternoon: root.querySelector('[data-weather-temp="afternoon"]'),
  };
  const days = root.querySelector('[data-weather-days]');
  days.hidden = compact;
  const chartStage = root.querySelector('[data-weather-chart-stage]');
  const chartElements = {
    svg: root.querySelector('[data-weather-chart]'),
    line: root.querySelector('[data-weather-line]'),
    pointsGroup: root.querySelector('[data-weather-points]'),
    yTicksGroup: root.querySelector('[data-weather-y-ticks]'),
    labelLayer: root.querySelector('[data-weather-labels]'),
  };
  chartStage.hidden = true;
  const chartFrame = root.querySelector('.weather-chart-frame');
  // Le cadre garde le rapport 2:1 dans l'espace restant ; masqué s'il devient trop bas pour être lisible.
  const fitChart = () => {
    const fits = chartStage.clientHeight >= MIN_CHART_HEIGHT_PX;
    chartFrame.hidden = !fits;
    if (fits) chartFrame.style.width = `${Math.floor(Math.min(chartStage.clientWidth, chartStage.clientHeight * 2))}px`;
  };
  if (typeof ResizeObserver !== 'undefined') new ResizeObserver(fitChart).observe(chartStage);
  const configRequired = root.querySelector('[data-weather-config-required]');
  const testBadge = root.querySelector('[data-external-test-mode]');
  if (testBadge) testBadge.hidden = root.dataset.testMode !== 'true';
  let config;

  try {
    config = JSON.parse(configJson || '{}');
  } catch {
    config = {};
  }

  const showError = (error) => {
    typeText(status, error instanceof ApiError ? error.message : 'La météo est momentanément indisponible.', typingOptions);
  };

  const loadForecast = async ({ periodic = false } = {}) => {
    const writing = periodic ? { ...typingOptions, restart: true } : typingOptions;
    if (!config.location) {
      current.hidden = true;
      configRequired.hidden = false;
      typeText(status, 'Aucune ville configurée.', typingOptions);
      return;
    }
    try {
      const forecast = await api.weatherForecast();
      if (testBadge) testBadge.hidden = !forecast.testMode;
      configRequired.hidden = true;
      current.hidden = false;
      typeText(status, '', typingOptions);
      typeText(locationHeading, forecast.location.name, writing);
      const today = forecast.days[0];
      typeText(dateLabel, `Aujourd'hui · ${formatDay(today.date, { weekday: 'short', day: 'numeric', month: 'short' })}`,
        writing);
      typeText(now, `Maintenant : ${formatTemperature(forecast.current.temperatureC)}, `
        + `ressenti ${formatTemperature(forecast.current.feelsLikeC)}`, writing);
      for (const [key, name, part] of [['morning', 'Matin', today.morning], ['afternoon', 'Après-midi', today.afternoon]]) {
        fillIcon(icons[key], part.code, partLabel(name, part));
        typeText(temperatures[key], formatTemperature(part.temperatureC), writing);
      }
      typeText(rain, `Risque de pluie : ${today.rainChancePercent} %`, writing);
      fillWind(wind, today);
      typeText(advice, today.advice, writing);
      days.replaceChildren();
      if (!compact) {
        for (const day of forecast.days.slice(1)) days.append(dayRow(day, writing));
        const model = chartModel(forecast);
        chartStage.hidden = !model;
        if (model) drawChart(chartElements, model, periodic);
      }
    } catch (error) {
      if (periodic) return;
      current.hidden = true;
      showError(error);
    }
  };

  await loadForecast();
  if (config.location && typingOptions.enabled !== false) {
    startRetypeLoop({
      run: () => loadForecast({ periodic: true }),
      isActive: () => root.isConnected,
    });
  }
}