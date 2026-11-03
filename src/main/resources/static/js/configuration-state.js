export const SLOT_NAMES = ['principal', 'haut gauche', 'bas gauche', 'haut droite', 'bas droite'];
export const UNASSIGNED_SLOT = -1;

export function moveWidget(preferences, appKey, targetSlot) {
  if (!Number.isInteger(targetSlot) || targetSlot < UNASSIGNED_SLOT || targetSlot >= SLOT_NAMES.length) {
    throw new RangeError('Emplacement de widget invalide.');
  }
  const next = preferences.map((app) => ({ ...app }));
  const moving = next.find((app) => app.key === appKey);
  if (!moving) throw new RangeError('Widget introuvable.');

  const previousSlot = moving.enabled ? moving.position : UNASSIGNED_SLOT;
  const displaced = targetSlot < 0 ? null : next.find((app) =>
    app.key !== appKey && app.enabled && app.position === targetSlot);

  if (displaced) {
    displaced.enabled = previousSlot >= 0;
    displaced.position = previousSlot;
  }
  moving.enabled = targetSlot >= 0;
  moving.position = targetSlot;
  return next;
}

/** Premier emplacement sans widget, ou l'emplacement principal (son widget est alors d\u00e9plac\u00e9) s'ils sont tous pris. */
export function firstFreeSlot(preferences) {
  const free = SLOT_NAMES.findIndex((_, slot) => !preferences.some((app) => app.enabled && app.position === slot));
  return free < 0 ? 0 : free;
}

/** Interrupteur de la vue liste : place le widget dans un emplacement libre, ou le retire de l'\u00e9cran. */
export function toggleWidget(preferences, appKey, enabled) {
  if (!enabled) {
    return moveWidget(preferences, appKey, UNASSIGNED_SLOT);
  }
  const current = preferences.find((app) => app.key === appKey);
  if (current?.enabled) {
    return moveWidget(preferences, appKey, current.position);
  }
  return moveWidget(preferences, appKey, firstFreeSlot(preferences));
}

export function toAppSelections(preferences) {
  return preferences.map((app) => ({
    key: app.key,
    enabled: app.enabled,
    position: app.enabled ? app.position : UNASSIGNED_SLOT,
    configJson: app.configJson || '{}',
  }));
}