import { api, ApiError } from '../api.js';
import { typeText } from '../typewriter.js';

export const ROTATE_MS = 15 * 1000;
export const COLLAPSE_MS = 20 * 1000;
const REFRESH_MS = 10 * 60 * 1000;

export function stepIndex(index, count, step) {
  if (count <= 0) return 0;
  return (((index + step) % count) + count) % count;
}

export async function initialize(container, _configJson, typingOptions = {}, dependencies = {}) {
  const root = container.querySelector('[data-news-widget]');
  if (!root) return;
  const status = root.querySelector('[data-news-status]');
  const header = root.querySelector('[data-news-header]');
  const categoryName = root.querySelector('[data-news-category]');
  const previous = root.querySelector('[data-news-prev]');
  const next = root.querySelector('[data-news-next]');
  const list = root.querySelector('[data-news-entries]');
  const testBadge = root.querySelector('[data-external-test-mode]');
  const timers = dependencies.timers ?? {
    set: (callback, delay) => window.setTimeout(callback, delay),
    clear: (id) => window.clearTimeout(id),
  };
  const client = dependencies.api ?? api;
  if (testBadge) testBadge.hidden = root.dataset.testMode !== 'true';

  let categories = [];
  let index = 0;
  let openEntry = null;
  let rotateTimer = null;
  let collapseTimer = null;
  let refreshTimer = null;
  let disposed = false;

  const dispose = () => {
    disposed = true;
    timers.clear(rotateTimer);
    timers.clear(collapseTimer);
    timers.clear(refreshTimer);
  };

  const alive = () => {
    if (disposed) return false;
    if (!root.isConnected) {
      dispose();
      return false;
    }
    return true;
  };

  const scheduleRotation = () => {
    timers.clear(rotateTimer);
    rotateTimer = null;
    if (categories.length > 1 && !openEntry) {
      rotateTimer = timers.set(() => {
        if (alive()) go(1);
      }, ROTATE_MS);
    }
  };

  const closeEntry = () => {
    timers.clear(collapseTimer);
    collapseTimer = null;
    if (!openEntry) return;
    openEntry.button.setAttribute('aria-expanded', 'false');
    openEntry.description.hidden = true;
    openEntry = null;
    scheduleRotation();
  };

  const startCollapseTimer = () => {
    timers.clear(collapseTimer);
    collapseTimer = timers.set(() => {
      if (alive()) closeEntry();
    }, COLLAPSE_MS);
  };

  const openEntryItem = (entry) => {
    if (openEntry && openEntry !== entry) {
      openEntry.button.setAttribute('aria-expanded', 'false');
      openEntry.description.hidden = true;
    }
    openEntry = entry;
    entry.button.setAttribute('aria-expanded', 'true');
    entry.description.hidden = false;
    timers.clear(rotateTimer);
    rotateTimer = null;
    startCollapseTimer();
  };

  // Un défilement dans le widget compte comme une action : les compteurs repartent de zéro.
  const onActivity = () => {
    if (!alive() || categories.length === 0) return;
    if (openEntry) {
      startCollapseTimer();
    } else {
      scheduleRotation();
    }
  };

  const createEntry = (entry, position) => {
    const item = document.createElement('li');
    item.className = 'news-entry';
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'news-entry-title';
    button.setAttribute('aria-expanded', 'false');
    button.setAttribute('aria-controls', `news-entry-${position}`);
    const description = document.createElement('p');
    description.className = 'news-entry-description';
    description.id = `news-entry-${position}`;
    description.hidden = true;
    description.textContent = entry.description || 'Aucune description.';
    item.append(button, description);
    const handle = { button, description };
    button.addEventListener('click', () => {
      if (openEntry === handle) {
        closeEntry();
      } else {
        openEntryItem(handle);
      }
    });
    typeText(button, entry.title, typingOptions);
    return item;
  };

  const showCategory = () => {
    openEntry = null;
    timers.clear(collapseTimer);
    collapseTimer = null;
    const category = categories[index];
    categoryName.textContent = categories.length > 1
      ? `${category.name} (${index + 1}/${categories.length})` : category.name;
    previous.hidden = categories.length < 2;
    next.hidden = categories.length < 2;
    list.replaceChildren();
    category.entries.forEach((entry, position) => list.append(createEntry(entry, position)));
    status.hidden = !category.notice && category.entries.length > 0;
    status.textContent = category.notice || (category.entries.length === 0 ? 'Aucune actualité.' : '');
    scheduleRotation();
  };

  function go(step) {
    index = stepIndex(index, categories.length, step);
    showCategory();
  }

  previous.addEventListener('click', () => go(-1));
  next.addEventListener('click', () => go(1));
  list.addEventListener('scroll', onActivity);
  root.addEventListener('wheel', onActivity, { passive: true });
  root.addEventListener('touchmove', onActivity, { passive: true });

  const render = (overview) => {
    if (testBadge) testBadge.hidden = !overview.testMode;
    const currentId = categories[index]?.id;
    categories = overview.categories;
    if (categories.length === 0) {
      timers.clear(rotateTimer);
      rotateTimer = null;
      header.hidden = true;
      list.replaceChildren();
      status.hidden = false;
      status.textContent = 'Choisissez des catégories dans la configuration du widget.';
      return;
    }
    header.hidden = false;
    index = Math.max(0, categories.findIndex((category) => category.id === currentId));
    showCategory();
  };

  const load = async () => {
    if (!alive()) return;
    try {
      render(await client.newsFeed());
    } catch (error) {
      status.hidden = false;
      status.textContent = error instanceof ApiError ? error.message : 'Les actualités sont momentanément indisponibles.';
    }
  };

  const scheduleRefresh = () => {
    refreshTimer = timers.set(async () => {
      if (!alive()) return;
      if (!openEntry) await load();
      scheduleRefresh();
    }, REFRESH_MS);
  };

  await load();
  scheduleRefresh();
}
