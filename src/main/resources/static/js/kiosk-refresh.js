// Verification d'une minute du kiosque : configuration des widgets et nouvelle version du serveur (logique pure, sans DOM).
export const CHECK_INTERVAL_MS = 60_000;
export const RELOAD_GUARD_KEY = 'assistant.reloaded-for';

// WAITING (QR invite affiche) ne bloque pas : sans cela l'ecran invite n'accepterait jamais de rechargement.
const ENROLLMENT_BLOCKING_STATES = new Set(['COUNTDOWN', 'CAPTURING', 'REVIEW']);

/** Une session d'enrolement est en cours : un smartphone a demarre la capture et n'a pas encore termine. */
export function isEnrollmentBlocking(capture) {
  return Boolean(capture) && ENROLLMENT_BLOCKING_STATES.has(capture.state);
}

export function appsSignature(apps) {
  return JSON.stringify(apps);
}

/** Vrai si le serveur annonce une autre version que la page et qu'on n'a pas deja recharge pour elle. */
export function shouldReloadForVersion({ pageVersion, serverVersion, reloadedFor }) {
  if (!pageVersion || !serverVersion || serverVersion === pageVersion) {
    return false;
  }
  return serverVersion !== reloadedFor;
}

/**
 * deps : pageVersion, isBusy(), isConnected(), fetchConfig(), fetchApps(), currentSignature(),
 * applyApps(apps), reload(), storage.
 * tick() renvoie 'busy' | 'running' | 'reload' | 'refreshed' | 'unchanged' | 'error'.
 */
export function createRefresher(deps) {
  let running = false;

  async function readServerVersion() {
    try {
      const config = await deps.fetchConfig();
      return config?.appVersion || null;
    } catch (error) {
      return null;
    }
  }

  async function tick() {
    if (running) {
      return 'running';
    }
    running = true;
    try {
      if (deps.isBusy()) {
        return 'busy';
      }
      const serverVersion = await readServerVersion();
      const reloadedFor = deps.storage.getItem(RELOAD_GUARD_KEY);
      // Rechargement seulement quand personne n'est connecte ; sinon il est differe a la minute suivante.
      if (shouldReloadForVersion({ pageVersion: deps.pageVersion, serverVersion, reloadedFor })
          && !deps.isConnected() && !deps.isBusy()) {
        deps.storage.setItem(RELOAD_GUARD_KEY, serverVersion);
        deps.reload();
        return 'reload';
      }
      let apps;
      try {
        apps = await deps.fetchApps();
      } catch (error) {
        return 'error';
      }
      if (appsSignature(apps) === deps.currentSignature() || deps.isBusy()) {
        return 'unchanged';
      }
      await deps.applyApps(apps);
      return 'refreshed';
    } finally {
      running = false;
    }
  }

  return { tick };
}
