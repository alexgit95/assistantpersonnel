// Etat du menu mobile et jetons conserves cote navigateur (sans DOM, testable sous Node).
export const PAIRING_STORAGE_KEY = 'assistant.pairing';
export const ENROLLMENT_STORAGE_KEY = 'assistant.enrollment';
export const CONNECTED_STORAGE_KEY = 'assistant.mobile-connected';
export const SCAN_PROMPT = 'Scannez le QR affiché sur l’écran.';
export const SCAN_AGAIN = 'Scannez à nouveau le QR sur l’écran.';

/** Separe le jeton d'appairage `s` du reste de la query string. */
export function extractPairing(search) {
  const params = new URLSearchParams(search);
  if (!params.has('s')) {
    return { token: null, search };
  }
  const token = params.get('s');
  params.delete('s');
  const rest = params.toString();
  return { token: token || null, search: rest ? `?${rest}` : '' };
}

/** Memorise le jeton present dans l'URL puis la nettoie ; renvoie le jeton memorise. */
export function capturePairing(storage, location, history) {
  const { token, search } = extractPairing(location.search);
  if (search !== location.search) {
    if (token) {
      storage.setItem(PAIRING_STORAGE_KEY, token);
    }
    history.replaceState(null, '', `${location.pathname}${search}${location.hash}`);
  }
  return storage.getItem(PAIRING_STORAGE_KEY);
}

export function clearPairing(storage) {
  storage.removeItem(PAIRING_STORAGE_KEY);
}

/** Session de capture memorisee apres /start : `{ id, claim }`. */
export function readEnrollment(storage) {
  try {
    const saved = JSON.parse(storage.getItem(ENROLLMENT_STORAGE_KEY) ?? 'null');
    return saved && typeof saved.id === 'string' && typeof saved.claim === 'string' ? saved : null;
  } catch {
    return null;
  }
}

export function saveEnrollment(storage, id, claim) {
  storage.setItem(ENROLLMENT_STORAGE_KEY, JSON.stringify({ id, claim }));
}

export function clearEnrollment(storage) {
  storage.removeItem(ENROLLMENT_STORAGE_KEY);
}

/**
 * Entrees du menu selon l'etat : `pairingValid` (QR invite recent), `session` (`{ scope, profileName }` ou null),
 * `wasConnected` (une session a deja ete vue sur cet appareil, donc elle a expire).
 */
export function menuFor({ pairingValid = false, session = null, wasConnected = false } = {}) {
  const admin = { id: 'admin', label: 'Administration', href: '/admin' };
  if (pairingValid) {
    return {
      note: 'Aucun profil n’est connecté sur l’écran.',
      items: [{ id: 'enroll', label: 'Créer mon profil', href: '/mobile/enroll' }, admin],
    };
  }
  if (session?.scope === 'WEIGHT') {
    return {
      note: `Session limitée à la saisie du poids (${session.profileName}).`,
      items: [{ id: 'weight', label: 'Poids', href: '/mobile/weight' }, { id: 'logout', label: 'Se déconnecter', action: 'logout' }],
    };
  }
  if (session) {
    return {
      note: `Connecté : ${session.profileName}.`,
      items: [
        { id: 'configuration', label: 'Configuration', href: '/configuration' },
        { id: 'weight', label: 'Poids', href: '/mobile/weight' },
        { id: 'recapture', label: 'Refaire mes captures', action: 'recapture' },
        admin,
        { id: 'logout', label: 'Se déconnecter', action: 'logout' },
      ],
    };
  }
  return { note: wasConnected ? SCAN_AGAIN : SCAN_PROMPT, items: [admin] };
}

/** Message affiche quand un appel renvoie 401 (session absente ou expiree) sur une page du smartphone. */
export function unauthorizedMessage(status) {
  return status === 403 ? 'Cette session ne permet pas cette action.' : SCAN_AGAIN;
}
