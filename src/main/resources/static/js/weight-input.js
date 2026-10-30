// Saisie du poids sur le smartphone : memes regles que WeightService.parseWeight (sans DOM, testable sous Node).
const WEIGHT_PATTERN = /^[0-9]{1,3}(?:[.,][0-9])?$/;
export const MIN_KG_TENTHS = 200;
export const MAX_KG_TENTHS = 4000;
export const INVALID_FORMAT_MESSAGE = 'Saisissez un poids valide avec au maximum une décimale.';
export const OUT_OF_RANGE_MESSAGE = 'Le poids doit être compris entre 20,0 et 400,0 kg.';

/** `{ ok: true, kgTenths, value }` ou `{ ok: false, message }` ; `value` est le texte nettoye envoye au serveur. */
export function parseWeight(text) {
  const value = String(text ?? '').trim();
  if (!WEIGHT_PATTERN.test(value)) {
    return { ok: false, message: INVALID_FORMAT_MESSAGE };
  }
  const kgTenths = Math.round(Number(value.replace(',', '.')) * 10);
  if (kgTenths < MIN_KG_TENTHS || kgTenths > MAX_KG_TENTHS) {
    return { ok: false, message: OUT_OF_RANGE_MESSAGE };
  }
  return { ok: true, kgTenths, value };
}

/** 725 -> "72,5". */
export function formatKg(kgTenths) {
  return (kgTenths / 10).toFixed(1).replace('.', ',');
}

/** Valeur pre-remplie : le dernier releve (liste triee du plus recent au plus ancien), sinon vide. */
export function defaultWeightValue(entries) {
  return entries?.length ? formatKg(entries[0].kgTenths) : '';
}

/** Date du jour locale au format AAAA-MM-JJ attendu par `input[type=date]` et l'API. */
export function localDateValue(date = new Date()) {
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${date.getFullYear()}-${month}-${day}`;
}
