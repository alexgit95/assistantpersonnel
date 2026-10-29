import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';
import { startRetypeLoop } from '../retype.js';
import { createIcon, iconId, windLabel } from './weather-icons.js';

const formatDay = (value, options) => new Intl.DateTimeFormat('fr-FR', options)
  .format(new Date(`${value}T12:00:00`));

const formatTemperature = (value) => `${Math.round(value).toLocaleString('fr-FR')} °C`;

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