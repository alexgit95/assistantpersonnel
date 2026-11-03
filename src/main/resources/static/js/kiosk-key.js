// Cle de kiosque : lue une fois dans l'URL de lancement du Pi, gardee en sessionStorage, retiree de la barre d'adresse.
export const KIOSK_HEADER = 'X-Kiosk-Key';
const STORAGE_KEY = 'assistant.kiosk-key';

/** Separe le parametre `kiosk` du reste de la query string. */
export function extractKioskKey(search) {
  const params = new URLSearchParams(search);
  if (!params.has('kiosk')) {
    return { key: null, search };
  }
  const key = params.get('kiosk');
  params.delete('kiosk');
  const rest = params.toString();
  return { key: key || null, search: rest ? `?${rest}` : '' };
}

/** Memorise la cle presente dans l'URL puis nettoie la barre d'adresse ; renvoie la cle memorisee. */
export function captureKioskKey(storage, location, history) {
  const { key, search } = extractKioskKey(location.search);
  if (search !== location.search) {
    if (key) {
      storage.setItem(STORAGE_KEY, key);
    }
    history.replaceState(null, '', `${location.pathname}${search}${location.hash}`);
  }
  return storage.getItem(STORAGE_KEY);
}

/** En-tete a joindre aux appels reserves au Pi (vide si aucune cle n'est memorisee). */
export function kioskHeaders(storage) {
  const key = storage.getItem(STORAGE_KEY);
  return key ? { [KIOSK_HEADER]: key } : {};
}

/** Message a afficher quand le serveur refuse (403) un appel reserve au Pi, selon qu'une cle est memorisee ou non. */
export function kioskKeyProblem(storage) {
  return storage.getItem(STORAGE_KEY)
    ? 'QR indisponible : la clé de kiosque est refusée par le serveur. Rouvrez cet écran avec l’adresse contenant la bonne clé (?kiosk=…).'
    : 'QR indisponible : clé de kiosque absente. Ouvrez cet écran avec l’adresse contenant la clé (?kiosk=…).';
}
