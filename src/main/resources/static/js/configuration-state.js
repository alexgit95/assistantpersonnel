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

export function toAppSelections(preferences) {
  return preferences.map((app) => ({
    key: app.key,
    enabled: app.enabled,
    position: app.enabled ? app.position : UNASSIGNED_SLOT,
    configJson: app.configJson || '{}',
  }));
}