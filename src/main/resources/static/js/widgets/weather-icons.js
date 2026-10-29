const SPRITE_URL = '/img/weather-icons.svg';
const SVG_NS = 'http://www.w3.org/2000/svg';

export function iconId(code) {
  if (code === 0 || code === 1) return 'sun';
  if (code === 2) return 'partly';
  if (code === 3) return 'cloud';
  if (code === 45 || code === 48) return 'fog';
  if ((code >= 51 && code <= 57) || (code >= 61 && code <= 67) || (code >= 80 && code <= 82)) return 'rain';
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return 'snow';
  if (code === 95 || code === 96 || code === 99) return 'storm';
  return 'unknown';
}

export function createIcon(id, label) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('class', 'weather-icon');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('role', 'img');
  svg.setAttribute('aria-label', label);
  const title = document.createElementNS(SVG_NS, 'title');
  title.textContent = label;
  const use = document.createElementNS(SVG_NS, 'use');
  use.setAttribute('href', `${SPRITE_URL}#${id}`);
  svg.append(title, use);
  return svg;
}

export function windLabel(gustKmh) {
  return `Rafales jusqu'à ${gustKmh} km/h`;
}
