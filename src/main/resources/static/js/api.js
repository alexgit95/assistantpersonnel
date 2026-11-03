// Appels a l'API du backend (cookie de session HttpOnly envoye automatiquement).
import { kioskHeaders } from './kiosk-key.js';

export class ApiError extends Error {
  constructor(status, detail) {
    super(detail);
    this.status = status;
  }
}

/** `kiosk: true` joint la cle de kiosque aux appels reserves au Pi ; `headers` ajoute les jetons du smartphone. */
async function request(method, url, body, { kiosk = false, headers = {} } = {}) {
  const options = { method, credentials: 'same-origin', headers: { ...(kiosk ? kioskHeaders(window.sessionStorage) : {}), ...headers } };
  if (body !== undefined) {
    options.headers['Content-Type'] = 'application/json';
    options.body = JSON.stringify(body);
  }
  const response = await fetch(url, options);
  if (response.status === 204) {
    return null;
  }
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new ApiError(response.status, payload.detail || 'Erreur de communication avec le serveur.');
  }
  return payload;
}

async function requestText(url) {
  const response = await fetch(url, { credentials: 'same-origin', cache: 'no-store' });
  if (!response.ok) {
    const payload = await response.json().catch(() => ({}));
    throw new ApiError(response.status, payload.detail || 'Fragment indisponible.');
  }
  return response.text();
}

export const api = {
  config: () => request('GET', '/api/config'),
  faces: () => request('GET', '/api/faces'),
  session: () => request('GET', '/api/auth/session'),
  profile: () => request('GET', '/api/profiles/me'),
  login: (profileId) => request('POST', '/api/auth/login', { profileId }, { kiosk: true }),
  heartbeat: () => request('POST', '/api/auth/heartbeat', undefined, { kiosk: true }),
  logout: () => request('POST', '/api/auth/logout'),
  apps: () => request('GET', '/api/apps'),
  saveApps: (selections) => request('PUT', '/api/apps', { selections }),
  appFragment: (key) => requestText(`/apps/${encodeURIComponent(key)}/fragment`),
  weightEntries: (page = 0, size = 20) => request('GET', `/api/weight?page=${page}&size=${size}`),
  createWeight: (entryDate, value) => request('POST', '/api/weight', { entryDate, value }),
  updateWeight: (id, entryDate, value) => request('PUT', `/api/weight/${id}`, { entryDate, value }),
  deleteWeight: (id) => request('DELETE', `/api/weight/${id}`),
  weatherCities: (query) => request('GET', `/api/weather/cities?query=${encodeURIComponent(query)}`),
  saveWeatherLocation: (location) => request('PUT', '/api/weather/location', location),
  weatherForecast: () => request('GET', '/api/weather'),
  transitLines: (query) => request('GET', `/api/transit/lines?query=${encodeURIComponent(query)}`),
  transitFavorites: () => request('GET', '/api/transit/favorites'),
  saveTransitFavorites: (lineIds) => request('PUT', '/api/transit/favorites', { lineIds }),
  transitAlerts: () => request('GET', '/api/transit/alerts'),
  transitStopSearch: (query) => request('GET', `/api/transit-passages/stops/search?query=${encodeURIComponent(query)}`),
  transitStops: () => request('GET', '/api/transit-passages/stops'),
  saveTransitStops: (stops) => request('PUT', '/api/transit-passages/stops', { stops }),
  transitPassages: () => request('GET', '/api/transit-passages/next'),
  announcements: () => request('GET', '/api/announcements'),
  newsCategories: () => request('GET', '/api/news/categories'),
  newsSelection: () => request('GET', '/api/news/selection'),
  saveNewsSelection: (categoryIds) => request('PUT', '/api/news/selection', { categoryIds }),
  newsFeed: () => request('GET', '/api/news/feed'),
  createProfile: (name, embeddings, avatar) => request('POST', '/api/profiles', { name, embeddings, avatar }),
  kioskState: (qrIds = []) => request('GET',
    `/api/kiosk/state${qrIds.length ? `?qr=${qrIds.map(encodeURIComponent).join(',')}` : ''}`, undefined, { kiosk: true }),
  openEnrollmentSession: () => request('POST', '/api/enrollment-sessions', undefined, { kiosk: true }),
  submitCapture: (id, payload) => request('POST', `/api/enrollment-sessions/${encodeURIComponent(id)}/captures`,
    payload, { kiosk: true }),
  cancelEnrollmentSession: (id) => request('DELETE', `/api/enrollment-sessions/${encodeURIComponent(id)}`,
    undefined, { kiosk: true }),
  issueHandoff: (scope) => request('POST', `/api/session/handoff?scope=${encodeURIComponent(scope)}`,
    undefined, { kiosk: true }),
  updateEmbeddings: (profileId, embeddings, replace, avatar) => request(
    'PUT', `/api/profiles/${profileId}/embeddings`, { embeddings, replace, avatar }),
  enrollmentByPairing: (pairing) => request('GET', '/api/enrollment-sessions/current', undefined,
    { headers: tokenHeaders({ pairing }) }),
  enrollmentState: (id, tokens) => request('GET', `${enrollmentPath(id)}/state`, undefined,
    { headers: tokenHeaders(tokens) }),
  enrollmentStart: (id, tokens, name) => request('POST', `${enrollmentPath(id)}/start`,
    name === undefined ? {} : { name }, { headers: tokenHeaders(tokens) }),
  enrollmentAvatars: (id, tokens) => request('GET', `${enrollmentPath(id)}/avatars`, undefined,
    { headers: tokenHeaders(tokens) }),
  enrollmentRestart: (id, tokens) => request('POST', `${enrollmentPath(id)}/restart`, undefined,
    { headers: tokenHeaders(tokens) }),
  enrollmentConfirm: (id, tokens, avatarIndex, replace) => request('POST', `${enrollmentPath(id)}/confirm`,
    { avatarIndex, replace }, { headers: tokenHeaders(tokens) }),
  enrollmentCancel: (id, tokens) => request('DELETE', enrollmentPath(id), undefined,
    { headers: tokenHeaders(tokens) }),
  recapture: () => request('POST', '/api/enrollment-sessions/recapture'),
};

function enrollmentPath(id) {
  return `/api/enrollment-sessions/${encodeURIComponent(id)}`;
}

/** Jetons du smartphone : `pairing` (lu dans le QR) ou `claim` (rendu par /start). */
function tokenHeaders({ pairing, claim } = {}) {
  return {
    ...(pairing ? { 'X-Pairing-Token': pairing } : {}),
    ...(claim ? { 'X-Claim-Token': claim } : {}),
  };
}
