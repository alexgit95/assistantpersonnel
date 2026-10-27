const animationStates = new WeakMap();

export function characterDelay(textLength, speedMs, maxDurationMs) {
  if (textLength <= 0) {
    return 0;
  }
  return Math.max(1, Math.min(speedMs, maxDurationMs / textLength));
}

function stopAnimation(element) {
  const state = animationStates.get(element);
  if (!state) {
    return;
  }
  if (state.timer !== null) {
    clearTimeout(state.timer);
  }
  state.visual.classList.remove('is-typing');
  state.resolve();
  animationStates.delete(element);
}

export function typeText(element, value, options = {}) {
  const text = String(value ?? '');
  const current = animationStates.get(element);
  if (current?.text === text && !options.restart) {
    return current.promise;
  }
  stopAnimation(element);

  const accessibleText = document.createElement('span');
  accessibleText.className = 'screen-reader-only';
  accessibleText.textContent = text;
  const visualText = document.createElement('span');
  visualText.className = 'typewriter-visual';
  visualText.setAttribute('aria-hidden', 'true');
  element.replaceChildren(accessibleText, visualText);

  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  const enabled = options.enabled !== false && !reducedMotion && text.length > 0;
  if (!enabled) {
    visualText.textContent = text;
    const promise = Promise.resolve();
    animationStates.set(element, { text, visual: visualText, timer: null, promise, resolve: () => {} });
    return promise;
  }

  const delay = characterDelay(text.length, options.speedMs ?? 25, options.maxDurationMs ?? 2500);
  let resolveAnimation;
  const promise = new Promise((resolve) => { resolveAnimation = resolve; });
  const state = { text, visual: visualText, timer: null, promise, resolve: resolveAnimation };
  animationStates.set(element, state);
  visualText.classList.add('is-typing');

  let charactersShown = 0;
  const writeNextCharacter = () => {
    if (animationStates.get(element) !== state) {
      return;
    }
    charactersShown += 1;
    visualText.textContent = text.slice(0, charactersShown);
    if (charactersShown === text.length) {
      visualText.classList.remove('is-typing');
      state.timer = null;
      state.resolve();
      return;
    }
    state.timer = window.setTimeout(writeNextCharacter, delay);
  };
  state.timer = window.setTimeout(writeNextCharacter, delay);
  return promise;
}

export function animateMarkedText(root = document, options = {}) {
  root.querySelectorAll('[data-typewriter]').forEach((element) => {
    if (!element.closest('[hidden]')) {
      const sourceText = animationStates.get(element)?.text ?? element.textContent;
      typeText(element, sourceText, options);
    }
  });
}
