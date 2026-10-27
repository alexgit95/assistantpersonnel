export const RETYPE_MIN_MS = 15000;
export const RETYPE_MAX_MS = 60000;

export function nextRetypeDelay(random = Math.random) {
  return RETYPE_MIN_MS + Math.floor(random() * (RETYPE_MAX_MS - RETYPE_MIN_MS + 1));
}

/** Execute run() a delai aleatoire, en se re-armant apres chaque passage ; un cycle en pause est saute. */
export function startRetypeLoop({ run, isPaused = () => false, isActive = () => true, random = Math.random,
  schedule = (fn, ms) => window.setTimeout(fn, ms), cancel = (id) => window.clearTimeout(id) }) {
  let timer = null;
  let stopped = false;

  const arm = () => {
    timer = schedule(async () => {
      timer = null;
      if (stopped) return;
      if (!isActive()) {
        stopped = true;
        return;
      }
      if (!isPaused()) {
        try {
          await run();
        } catch {
          // Un echec periodique ne doit pas interrompre les cycles suivants.
        }
      }
      if (!stopped) arm();
    }, nextRetypeDelay(random));
  };

  arm();
  return {
    stop() {
      stopped = true;
      if (timer !== null) cancel(timer);
      timer = null;
    },
  };
}
