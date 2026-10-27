import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';

function displayDate(value, compact) {
  return new Intl.DateTimeFormat('fr-FR', {
    weekday: compact ? undefined : 'long', day: 'numeric', month: 'short',
  })
    .format(new Date(`${value}T12:00:00`));
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
  const days = root.querySelector('[data-weather-days]');
  const configRequired = root.querySelector('[data-weather-config-required]');
  let config;

  try {
    config = JSON.parse(configJson || '{}');
  } catch {
    config = {};
  }

  const showError = (error) => {
    typeText(status, error instanceof ApiError ? error.message : 'La météo est momentanément indisponible.', typingOptions);
  };

  const loadForecast = async () => {
    if (!config.location) {
      current.hidden = true;
      configRequired.hidden = false;
      typeText(status, 'Aucune ville configurée.', typingOptions);
      return;
    }
    try {
      const forecast = await api.weatherForecast();
      configRequired.hidden = true;
      current.hidden = false;
      typeText(status, '', typingOptions);
      typeText(locationHeading, forecast.location.name, typingOptions);
      const currentSummary = compact
        ? `${forecast.current.description} · ${forecast.current.temperatureC.toLocaleString('fr-FR')} °C`
        : `${forecast.current.description}, ${forecast.current.temperatureC.toLocaleString('fr-FR')} °C. `
          + `Ressenti ${forecast.current.feelsLikeC.toLocaleString('fr-FR')} °C, `
          + `humidité ${forecast.current.humidityPercent} %, vent ${forecast.current.windKmh.toLocaleString('fr-FR')} km/h.`;
      typeText(now, currentSummary, typingOptions);
      days.replaceChildren();
      for (const day of forecast.days) {
        const item = document.createElement('li');
        const date = document.createElement('strong');
        const description = document.createElement('span');
        const temperatures = document.createElement('span');
        typeText(date, displayDate(day.date, compact), typingOptions);
        typeText(description, day.description, typingOptions);
        typeText(temperatures, `${day.minC.toLocaleString('fr-FR')} à ${day.maxC.toLocaleString('fr-FR')} °C`, typingOptions);
        item.append(date, temperatures);
        if (!compact) item.append(description);
        days.append(item);
      }
    } catch (error) {
      current.hidden = true;
      showError(error);
    }
  };

  await loadForecast();
}