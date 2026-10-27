// Appels a l'API du backend (cookie de session HttpOnly envoye automatiquement).

export class ApiError extends Error {
  constructor(status, detail) {
    super(detail);
    this.status = status;
  }
}

async function request(method, url, body) {
  const options = { method, credentials: 'same-origin', headers: {} };
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
  login: (profileId) => request('POST', '/api/auth/login', { profileId }),
  heartbeat: () => request('POST', '/api/auth/heartbeat'),
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
  announcements: () => request('GET', '/api/announcements'),
  createProfile: (name, embeddings, avatar) => request('POST', '/api/profiles', { name, embeddings, avatar }),
  updateEmbeddings: (profileId, embeddings, replace, avatar) => request(
    'PUT', `/api/profiles/${profileId}/embeddings`, { embeddings, replace, avatar }),
};
