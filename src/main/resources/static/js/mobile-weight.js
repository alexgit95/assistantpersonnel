import { api, ApiError } from './api.js';
import { unauthorizedMessage } from './mobile-menu.js';
import { defaultWeightValue, formatKg, localDateValue, parseWeight } from './weight-input.js';

const PAGE_SIZE = 10;
const byId = (id) => document.getElementById(id);
const form = byId('weight-form');
const dateInput = byId('weight-date');
const valueInput = byId('weight-value');
const submit = byId('weight-submit');
const cancelEdit = byId('weight-cancel-edit');
const message = byId('weight-message');
const list = byId('weight-list');
const pager = byId('weight-pager');

let page = 0;
let editingId = null;

function say(text, error = false) {
  message.textContent = text;
  message.classList.toggle('mobile-error', error);
}

function formatDate(value) {
  return new Intl.DateTimeFormat('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' })
    .format(new Date(`${value}T00:00:00`));
}

function handleFailure(error) {
  if (error instanceof ApiError && (error.status === 401 || error.status === 403)) {
    form.hidden = true;
    pager.hidden = true;
    list.replaceChildren();
    say(unauthorizedMessage(error.status), true);
    return;
  }
  say(error instanceof ApiError ? error.message : 'Le serveur est injoignable. Réessayez.', true);
}

function resetForm(entries) {
  editingId = null;
  dateInput.value = localDateValue();
  valueInput.value = defaultWeightValue(entries);
  submit.textContent = 'Ajouter';
  cancelEdit.hidden = true;
}

function startEdit(entry) {
  editingId = entry.id;
  dateInput.value = entry.entryDate;
  valueInput.value = formatKg(entry.kgTenths);
  submit.textContent = 'Enregistrer';
  cancelEdit.hidden = false;
  valueInput.focus();
}

function renderEntries(entries) {
  list.replaceChildren(...entries.map((entry) => {
    const item = document.createElement('li');
    const label = document.createElement('span');
    label.textContent = `${formatDate(entry.entryDate)} · ${formatKg(entry.kgTenths)} kg`;
    const actions = document.createElement('span');
    actions.className = 'mobile-actions';
    const edit = document.createElement('button');
    edit.type = 'button';
    edit.textContent = 'Modifier';
    edit.addEventListener('click', () => startEdit(entry));
    const remove = document.createElement('button');
    remove.type = 'button';
    remove.textContent = 'Supprimer';
    remove.addEventListener('click', async () => {
      if (!window.confirm(`Supprimer la mesure du ${formatDate(entry.entryDate)} ?`)) {
        return;
      }
      try {
        await api.deleteWeight(entry.id);
        say('Mesure supprimée.');
        await load();
      } catch (error) {
        handleFailure(error);
      }
    });
    actions.append(edit, remove);
    item.append(label, actions);
    return item;
  }));
  if (entries.length === 0) {
    const empty = document.createElement('li');
    empty.textContent = 'Aucune mesure enregistrée.';
    list.append(empty);
  }
}

async function load({ prefill = false } = {}) {
  const result = await api.weightEntries(page, PAGE_SIZE);
  if (page > 0 && result.entries.length === 0) {
    page -= 1;
    return load({ prefill });
  }
  renderEntries(result.entries);
  pager.hidden = result.totalPages <= 1;
  byId('weight-page').textContent = `Page ${result.page + 1} / ${Math.max(1, result.totalPages)}`;
  byId('weight-prev').disabled = result.page === 0;
  byId('weight-next').disabled = result.page + 1 >= result.totalPages;
  if (prefill || editingId === null) {
    // Le formulaire propose toujours le dernier releve, quelle que soit la page affichee.
    const latest = page === 0 ? result : await api.weightEntries(0, 1);
    resetForm(latest.entries);
  }
}

form.addEventListener('submit', async (event) => {
  event.preventDefault();
  const parsed = parseWeight(valueInput.value);
  if (!parsed.ok) {
    say(parsed.message, true);
    valueInput.focus();
    return;
  }
  if (!dateInput.value) {
    say('Choisissez une date.', true);
    return;
  }
  try {
    if (editingId === null) {
      await api.createWeight(dateInput.value, parsed.value);
      page = 0;
      say('Mesure ajoutée.');
    } else {
      await api.updateWeight(editingId, dateInput.value, parsed.value);
      say('Mesure modifiée.');
    }
    editingId = null;
    await load({ prefill: true });
  } catch (error) {
    handleFailure(error);
  }
});

cancelEdit.addEventListener('click', () => load({ prefill: true }).catch(handleFailure));
byId('weight-prev').addEventListener('click', () => {
  page = Math.max(0, page - 1);
  load().catch(handleFailure);
});
byId('weight-next').addEventListener('click', () => {
  page += 1;
  load().catch(handleFailure);
});

async function start() {
  try {
    const session = await api.session();
    byId('weight-owner').textContent = session.profileName;
    form.hidden = false;
    await load({ prefill: true });
  } catch (error) {
    handleFailure(error);
  }
}

start();
