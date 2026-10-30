import { api } from '../api.js';
import { buildQr, loadQrLibrary, qrSvg } from '../qr-code.js';
import { createQrTicket } from '../qr-ticket.js';
import { startRetypeLoop } from '../retype.js';

const CHART_PAGE_SIZE = 6;
const DEFAULT_QR_POLL_MS = 3000;

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

function drawChart(svg, labelLayer, entries, replay = false) {
  const plotted = chartCoordinates(entries);
  const line = svg.querySelector('[data-weight-line]');
  const pointsGroup = svg.querySelector('[data-weight-points]');
  const signature = plotted.map(({ entry }) => `${entry.entryDate}:${entry.kgTenths}`).join('|');
  if (svg.dataset.signature === signature && !replay) return;
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

/** QR poids : un jeton WEIGHT a usage unique, renouvele des qu'il est scanne ou presque expire. */
function startWeightQr(root, clientConfig) {
  const panel = root.querySelector('[data-weight-qr]');
  const link = root.querySelector('[data-weight-qr-link]');
  const image = root.querySelector('[data-weight-qr-image]');
  const devMode = Boolean(clientConfig.qrDevMode);

  const ticket = createQrTicket({
    issue: () => api.issueHandoff('WEIGHT'),
    poll: (ids) => api.kioskState(ids),
    show: async (url) => {
      const library = await loadQrLibrary();
      image.innerHTML = qrSvg(buildQr(library, url), { label: 'QR code pour saisir son poids sur un téléphone' });
      if (devMode) {
        link.href = url;
        link.target = '_blank';
        link.rel = 'noopener';
        link.title = `Simuler le scan : ${url}`;
      }
      panel.hidden = false;
    },
    clear: () => {
      image.replaceChildren();
      link.removeAttribute('href');
      link.removeAttribute('title');
      panel.hidden = true;
    },
  });

  const run = async () => {
    if (!root.isConnected) {
      return;
    }
    try {
      if (!document.hidden) {
        await ticket.tick();
      }
    } catch (error) {
      console.warn('QR poids indisponible', error);
    }
    setTimeout(run, clientConfig.kioskPollIdleMs ?? DEFAULT_QR_POLL_MS);
  };
  run();
}

export async function initialize(container, _configJson, typingOptions = {}) {
  const root = container.querySelector('[data-weight-widget]');
  if (!root) return;

  const chart = root.querySelector('[data-weight-chart]');
  const chartLabels = root.querySelector('[data-weight-labels]');
  const empty = root.querySelector('[data-weight-empty]');

  const loadData = async ({ periodic = false } = {}) => {
    try {
      const chartPage = await api.weightEntries(0, CHART_PAGE_SIZE);
      drawChart(chart, chartLabels, chartPage.entries, periodic);
      empty.hidden = chartPage.entries.length > 0;
    } catch (error) {
      if (!periodic) console.warn('Poids indisponible', error);
    }
  };

  await loadData();
  const clientConfig = await api.config().catch(() => ({}));
  startWeightQr(root, clientConfig);
  if (typingOptions.enabled !== false) {
    startRetypeLoop({
      run: () => loadData({ periodic: true }),
      isPaused: () => false,
      isActive: () => root.isConnected,
    });
  }
}
