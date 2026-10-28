import { api, ApiError } from './api.js';
import { createProfileMenu } from './profile-menu.js';
import { moveWidget, SLOT_NAMES, toAppSelections, UNASSIGNED_SLOT } from './configuration-state.js';

const profileMenu = createProfileMenu();
const pool = document.getElementById('widget-pool');
const grid = document.getElementById('configuration-grid');
const status = document.getElementById('configuration-status');
const saveButton = document.getElementById('save-widget-layout');
let preferences = [];
let draggedKey = null;
let pointerDrag = null;

function showStatus(message) {
  status.textContent = message;
}

function render() {
  pool.replaceChildren();
  const slots = [...grid.querySelectorAll('[data-slot-index]')];
  const contents = new Map();
  for (const slot of slots) {
    let content = slot.querySelector('.configuration-slot-content');
    if (!content) {
      content = document.createElement('div');
      content.className = 'configuration-slot-content';
      slot.append(content);
    }
    content.replaceChildren();
    contents.set(Number(slot.dataset.slotIndex), content);
  }

  for (const [slotIndex, content] of contents) {
    const app = preferences.find((preference) => preference.enabled && preference.position === slotIndex);
    if (app) {
      const assignment = document.createElement('span');
      assignment.className = 'slot-widget-preview';
      assignment.textContent = app.title;
      content.append(assignment);
    }
  }

  for (const app of preferences) {
    const card = createWidgetCard(app);
    pool.append(card);
  }
}

function createWidgetCard(app) {
  const card = document.createElement('article');
  card.className = 'configuration-widget';
  card.draggable = true;
  card.dataset.appKey = app.key;

  const heading = document.createElement('h2');
  heading.textContent = app.title;
  const description = document.createElement('p');
  description.textContent = app.description;

  const assignment = document.createElement('label');
  assignment.className = 'screen-reader-only';
  assignment.htmlFor = `slot-${app.key}`;
  assignment.textContent = `Emplacement de ${app.title}`;
  const select = document.createElement('select');
  select.id = `slot-${app.key}`;
  select.setAttribute('aria-label', `Emplacement de ${app.title}`);
  const unassigned = document.createElement('option');
  unassigned.value = String(UNASSIGNED_SLOT);
  unassigned.textContent = 'Non placé';
  select.append(unassigned);
  SLOT_NAMES.forEach((name, index) => {
    const option = document.createElement('option');
    option.value = String(index);
    option.textContent = index === 0 ? 'Principal' : name;
    select.append(option);
  });
  select.value = String(app.enabled ? app.position : UNASSIGNED_SLOT);
  select.addEventListener('change', () => {
    preferences = moveWidget(preferences, app.key, Number(select.value));
    render();
  });
  card.append(heading, description, assignment, select);

  if (app.key === 'weather') {
    appendWeatherConfiguration(card, app);
  }
  if (app.key === 'transit') {
    appendTransitConfiguration(card, app);
  }

  card.addEventListener('dragstart', (event) => {
    if (event.target.closest('input, select, button, textarea')) {
      event.preventDefault();
      return;
    }
    draggedKey = app.key;
    event.dataTransfer.effectAllowed = 'move';
    event.dataTransfer.setData('text/plain', app.key);
    card.classList.add('is-dragging');
  });
  card.addEventListener('dragend', () => {
    draggedKey = null;
    card.classList.remove('is-dragging');
    grid.querySelectorAll('.is-drop-target').forEach((slot) => slot.classList.remove('is-drop-target'));
  });
  return card;
}

function appendWeatherConfiguration(card, app) {
  const form = document.createElement('form');
  form.className = 'weather-config';
  const input = document.createElement('input');
  input.type = 'search';
  input.minLength = 2;
  input.maxLength = 100;
  input.placeholder = app.configJson && JSON.parse(app.configJson).location?.name || 'Ville';
  input.setAttribute('aria-label', 'Ville pour la météo');
  const search = document.createElement('button');
  search.type = 'submit';
  search.textContent = 'Chercher';
  form.append(input, search);
  const choices = document.createElement('select');
  choices.hidden = true;
  choices.setAttribute('aria-label', 'Choisir une ville');
  const save = document.createElement('button');
  save.type = 'button';
  save.hidden = true;
  save.textContent = 'Choisir';
  const result = document.createElement('span');
  result.className = 'weather-config-status';
  result.setAttribute('role', 'status');
  result.setAttribute('aria-live', 'polite');

  let candidates = [];
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    choices.hidden = true;
    save.hidden = true;
    try {
      candidates = await api.weatherCities(input.value.trim());
      if (candidates.length === 0) {
        result.textContent = 'Ville introuvable.';
      } else if (candidates.length === 1) {
        await saveWeatherCity(app, candidates[0], input, result);
      } else {
        choices.replaceChildren();
        candidates.forEach((city, index) => {
          const option = document.createElement('option');
          option.value = String(index);
          option.textContent = [city.name, city.region, city.country].filter(Boolean).join(', ');
          choices.append(option);
        });
        choices.hidden = false;
        save.hidden = false;
        result.textContent = 'Plusieurs résultats.';
      }
    } catch (error) {
      result.textContent = error instanceof ApiError ? error.message : 'Recherche indisponible.';
    }
  });
  save.addEventListener('click', async () => {
    const city = candidates[Number(choices.value)];
    if (city) await saveWeatherCity(app, city, input, result);
  });

  card.append(form, choices, save, result);
}

async function saveWeatherCity(app, city, input, result) {
  try {
    const saved = await api.saveWeatherLocation(city);
    let config;
    try {
      config = JSON.parse(app.configJson || '{}');
    } catch {
      config = {};
    }
    app.configJson = JSON.stringify({ ...config, location: saved });
    input.value = '';
    input.placeholder = saved.name;
    result.textContent = 'Ville enregistrée.';
  } catch (error) {
    result.textContent = error instanceof ApiError ? error.message : 'Enregistrement impossible.';
  }
}

function appendTransitConfiguration(card, app) {
  const form = document.createElement('form');
  form.className = 'transit-config';
  const input = document.createElement('input');
  input.type = 'search';
  input.maxLength = 100;
  input.placeholder = 'RER A, Ligne J, Métro 1…';
  input.setAttribute('aria-label', 'Rechercher une ligne de transport');
  const search = document.createElement('button');
  search.type = 'submit';
  search.textContent = 'Rechercher';
  form.append(input, search);

  const result = document.createElement('p');
  result.className = 'transit-config-status';
  result.setAttribute('role', 'status');
  result.setAttribute('aria-live', 'polite');
  const results = document.createElement('ul');
  results.className = 'transit-config-results';
  const favorites = document.createElement('ul');
  favorites.className = 'transit-config-favorites';
  favorites.setAttribute('aria-label', 'Lignes favorites');
  const save = document.createElement('button');
  save.type = 'button';
  save.textContent = 'Enregistrer les lignes';

  let selected = [];
  const labels = new Map();
  try {
    const savedIds = JSON.parse(app.configJson || '{}').favoriteLineIds;
    selected = Array.isArray(savedIds) ? savedIds.filter((id) => typeof id === 'string').slice(0, 4) : [];
  } catch {
    selected = [];
  }

  const renderFavorites = () => {
    favorites.replaceChildren();
    selected.forEach((id) => {
      const item = document.createElement('li');
      const name = document.createElement('span');
      name.textContent = labels.get(id) || id;
      const remove = document.createElement('button');
      remove.type = 'button';
      remove.setAttribute('aria-label', `Retirer ${name.textContent}`);
      remove.textContent = 'Retirer';
      remove.addEventListener('click', () => {
        selected = selected.filter((lineId) => lineId !== id);
        renderFavorites();
      });
      item.append(name, remove);
      favorites.append(item);
    });
    results.querySelectorAll('input[type="checkbox"]').forEach((choice) => {
      choice.checked = selected.includes(choice.value);
      choice.disabled = !choice.checked && selected.length >= 4;
    });
    result.textContent = `${selected.length} ligne${selected.length === 1 ? '' : 's'} sélectionnée${selected.length === 1 ? '' : 's'} sur 4.`;
  };

  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    results.replaceChildren();
    const query = input.value.trim();
    if (!query) {
      result.textContent = 'Saisissez un nom de ligne.';
      return;
    }
    try {
      const lines = await api.transitLines(query);
      if (lines.length === 0) {
        result.textContent = 'Aucune ligne trouvée.';
        return;
      }
      for (const line of lines) {
        labels.set(line.id, line.label);
        const item = document.createElement('li');
        const label = document.createElement('label');
        const checkbox = document.createElement('input');
        checkbox.type = 'checkbox';
        checkbox.value = line.id;
        checkbox.checked = selected.includes(line.id);
        checkbox.disabled = !checkbox.checked && selected.length >= 4;
        checkbox.addEventListener('change', () => {
          selected = checkbox.checked ? [...selected, line.id] : selected.filter((id) => id !== line.id);
          renderFavorites();
          results.querySelectorAll('input[type="checkbox"]').forEach((choice) => {
            if (!choice.checked) choice.disabled = selected.length >= 4;
          });
        });
        label.append(checkbox, document.createTextNode(` ${line.label} · ${line.transportMode}`));
        item.append(label);
        results.append(item);
      }
      renderFavorites();
    } catch (error) {
      result.textContent = error instanceof ApiError ? error.message : 'Recherche de lignes indisponible.';
    }
  });

  save.addEventListener('click', async () => {
    save.disabled = true;
    try {
      const saved = await api.saveTransitFavorites(selected);
      selected = saved.lineIds;
      saved.lines.forEach((line) => labels.set(line.id, line.label));
      let config;
      try {
        config = JSON.parse(app.configJson || '{}');
      } catch {
        config = {};
      }
      app.configJson = JSON.stringify({ ...config, favoriteLineIds: selected });
      document.dispatchEvent(new CustomEvent('assistant:app-config-updated', {
        detail: { key: app.key, configJson: app.configJson },
      }));
      renderFavorites();
      result.textContent = 'Lignes favorites enregistrées.';
    } catch (error) {
      result.textContent = error instanceof ApiError ? error.message : 'Enregistrement impossible.';
    } finally {
      save.disabled = false;
    }
  });
  renderFavorites();
  api.transitFavorites().then((saved) => {
    selected = saved.lineIds;
    saved.lines.forEach((line) => labels.set(line.id, line.label));
    renderFavorites();
  }).catch((error) => {
    result.textContent = error instanceof ApiError ? error.message : 'Lignes favorites indisponibles.';
  });
  card.append(form, results, favorites, save, result);
}

function allowDrop(event) {
  event.preventDefault();
  event.dataTransfer.dropEffect = 'move';
}

grid.querySelectorAll('[data-slot-index]').forEach((slot) => {
  slot.addEventListener('dragover', (event) => {
    allowDrop(event);
    slot.classList.add('is-drop-target');
  });
  slot.addEventListener('dragleave', (event) => {
    if (!slot.contains(event.relatedTarget)) slot.classList.remove('is-drop-target');
  });
  slot.addEventListener('drop', (event) => {
    allowDrop(event);
    slot.classList.remove('is-drop-target');
    const key = event.dataTransfer.getData('text/plain') || draggedKey;
    if (!key) return;
    preferences = moveWidget(preferences, key, Number(slot.dataset.slotIndex));
    render();
  });
});

pool.addEventListener('dragover', allowDrop);
pool.addEventListener('drop', (event) => {
  allowDrop(event);
  const key = event.dataTransfer.getData('text/plain') || draggedKey;
  if (!key) return;
  preferences = moveWidget(preferences, key, UNASSIGNED_SLOT);
  render();
});

function installPointerDragging() {
  document.addEventListener('pointerdown', (event) => {
    if (event.pointerType === 'mouse') return;
    const card = event.target.closest('.configuration-widget');
    if (!card || event.target.closest('input, select, button, textarea')) return;
    pointerDrag = { key: card.dataset.appKey, x: event.clientX, y: event.clientY, moved: false };
    card.setPointerCapture?.(event.pointerId);
  });
  document.addEventListener('pointermove', (event) => {
    if (!pointerDrag) return;
    if (Math.hypot(event.clientX - pointerDrag.x, event.clientY - pointerDrag.y) > 8) pointerDrag.moved = true;
  });
  document.addEventListener('pointerup', (event) => {
    if (!pointerDrag) return;
    const drag = pointerDrag;
    pointerDrag = null;
    if (!drag.moved) return;
    const target = document.elementFromPoint(event.clientX, event.clientY);
    const slot = target?.closest('[data-slot-index]');
    preferences = moveWidget(preferences, drag.key,
      slot ? Number(slot.dataset.slotIndex) : UNASSIGNED_SLOT);
    render();
  });
}

async function saveLayout() {
  saveButton.disabled = true;
  try {
    preferences = await api.saveApps(toAppSelections(preferences));
    render();
    showStatus('Configuration enregistrée.');
  } catch (error) {
    showStatus(error instanceof ApiError ? error.message : 'Enregistrement impossible.');
  } finally {
    saveButton.disabled = false;
  }
}

async function start() {
  createProfileMenu();
  const clock = document.getElementById('clock');
  const greeting = document.getElementById('greeting');
  const updateClock = () => {
    clock.textContent = new Intl.DateTimeFormat('fr-FR', { hour: '2-digit', minute: '2-digit' }).format(new Date());
  };
  updateClock();
  window.setInterval(updateClock, 30_000);
  saveButton.addEventListener('click', saveLayout);
  installPointerDragging();
  try {
    const [profile, apps] = await Promise.all([api.profile(), api.apps()]);
    profileMenu.setProfile(profile);
    greeting.textContent = profile.name;
    document.getElementById('create-profile').hidden = true;
    document.getElementById('unrecognized-help').hidden = true;
    preferences = apps.map((app) => ({ ...app }));
    render();
    showStatus('');
  } catch (error) {
    showStatus(error instanceof ApiError ? error.message : 'Impossible de charger la configuration.');
  }
}

start();