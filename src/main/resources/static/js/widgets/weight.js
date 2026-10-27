import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';

const CHART_PAGE_SIZE = 6;
const RECENT_MEASURE_COUNT = 3;

function localDateValue() {
  const date = new Date();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}

export function formatWeight(kgTenths) {
  return `${(kgTenths / 10).toLocaleString('fr-FR', { minimumFractionDigits: 1, maximumFractionDigits: 1 })} kg`;
}

function formatDate(value) {
  return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(`${value}T00:00:00`));
}

export function chartCoordinates(entries) {
  const ordered = [...entries].reverse();
  if (ordered.length === 0) return [];
  const values = ordered.map((entry) => entry.kgTenths / 10);
  const min = Math.min(...values);
  const max = Math.max(...values);
  const spread = Math.max(1, max - min);
  return values.map((value, index) => {
    const x = ordered.length === 1 ? 330 : 40 + (580 * index) / (ordered.length - 1);
    const y = 260 - ((value - min) / spread) * 210;
    return { entry: ordered[index], x, y };
  });
}

function drawChart(svg, labelLayer, entries) {
  const plotted = chartCoordinates(entries);
  const line = svg.querySelector('[data-weight-line]');
  const pointsGroup = svg.querySelector('[data-weight-points]');
  const signature = plotted.map(({ entry }) => `${entry.entryDate}:${entry.kgTenths}`).join('|');
  if (svg.dataset.signature === signature) return;
  svg.dataset.signature = signature;
  pointsGroup.replaceChildren();
  labelLayer.replaceChildren();
  if (plotted.length === 0) {
    line.setAttribute('points', '');
    svg.setAttribute('aria-label', 'Évolution du poids, aucune mesure enregistrée.');
    return;
  }

  svg.setAttribute('aria-label', `Évolution du poids : ${plotted.map(({ entry }) =>
    `${formatDate(entry.entryDate)}, ${formatWeight(entry.kgTenths)}`).join('; ')}.`);
  line.setAttribute('points', plotted.map(({ x, y }) => `${x},${y}`).join(' '));
  plotted.forEach(({ entry, x, y }, index) => {
    const point = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
    point.setAttribute('cx', String(x));
    point.setAttribute('cy', String(y));
    point.setAttribute('r', '5');
    pointsGroup.append(point);
    const label = document.createElement('span');
    label.className = 'weight-chart-label';
    if (index === 0) label.classList.add('align-start');
    if (index === plotted.length - 1) label.classList.add('align-end');
    label.textContent = formatWeight(entry.kgTenths);
    label.style.left = `${(x / 640) * 100}%`;
    label.style.top = `${(y / 320) * 100}%`;
    labelLayer.append(label);
  });

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

export async function initialize(container, _configJson, typingOptions = {}) {
  const root = container.querySelector('[data-weight-widget]');
  if (!root) return;

  const card = root.querySelector('[data-weight-card]');
  const chartFace = root.querySelector('.weight-chart-face');
  const measuresFace = root.querySelector('.weight-measures-face');
  const openButton = root.querySelector('[data-weight-open]');
  const backButton = root.querySelector('[data-weight-back]');
  const form = root.querySelector('[data-weight-form]');
  const dateInput = form.elements.entryDate;
  const valueInput = form.elements.value;
  const status = root.querySelector('[data-weight-status]');
  const history = root.querySelector('[data-weight-history]');
  const chart = root.querySelector('[data-weight-chart]');
  const chartLabels = root.querySelector('[data-weight-labels]');

  dateInput.value = localDateValue();

  const showError = (error) => {
    typeText(status, error instanceof ApiError ? error.message : 'Une erreur est survenue. Réessayez.', typingOptions);
  };

  const setFlipped = (flipped) => {
    card.classList.toggle('is-flipped', flipped);
    openButton.setAttribute('aria-expanded', String(flipped));
    backButton.setAttribute('aria-expanded', String(!flipped));
    chartFace.inert = flipped;
    chartFace.setAttribute('aria-hidden', String(flipped));
    measuresFace.inert = !flipped;
    measuresFace.setAttribute('aria-hidden', String(!flipped));
    if (flipped) {
      valueInput.focus();
    } else {
      openButton.focus();
    }
  };

  const loadData = async () => {
    try {
      const [chartPage, recentPage] = await Promise.all([
        api.weightEntries(0, CHART_PAGE_SIZE),
        api.weightEntries(0, RECENT_MEASURE_COUNT),
      ]);
      drawChart(chart, chartLabels, chartPage.entries);
      history.replaceChildren();
      for (const entry of recentPage.entries) {
        const item = document.createElement('li');
        const measure = document.createElement('button');
        measure.type = 'button';
        measure.className = 'weight-measure';
        measure.setAttribute('aria-label', 'Revenir au graphique');
        typeText(measure, `${formatDate(entry.entryDate)} · ${formatWeight(entry.kgTenths)}`, typingOptions);
        measure.addEventListener('click', () => setFlipped(false));
        item.append(measure);
        history.append(item);
      }
      if (recentPage.entries.length === 0) {
        const item = document.createElement('li');
        typeText(item, 'Aucune mesure enregistrée.', typingOptions);
        history.append(item);
      }
    } catch (error) {
      showError(error);
    }
  };

  openButton.addEventListener('click', async () => {
    await loadData();
    setFlipped(true);
  });
  backButton.addEventListener('click', () => setFlipped(false));
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    try {
      await api.createWeight(dateInput.value, valueInput.value);
      valueInput.value = '';
      dateInput.value = localDateValue();
      typeText(status, 'Mesure ajoutée.', typingOptions);
      await loadData();
    } catch (error) {
      showError(error);
    }
  });

  await loadData();
}