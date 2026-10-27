// Machine a etats de la session faciale (sans DOM ni timers) : SCAN -> CONNECTE -> PAUSE.
// Les actions retournees sont executees par l'appelant (appels API).

export const State = Object.freeze({ SCAN: 'scan', CONNECTED: 'connected', PAUSED: 'paused' });

const MIN_PAUSE_WAIT_MS = 50;

export class SessionMachine {
  constructor(config) {
    this.config = config;
    this.state = State.SCAN;
    this.profileId = null;
    this.lastHeartbeatAt = 0;
    this.pausedUntil = 0;
    this.candidateId = null;
    this.candidateCount = 0;
  }

  /** Reprend une session deja ouverte cote serveur (rechargement de la page). */
  restore(profileId, remainingSeconds, now) {
    const elapsedMs = (this.config.sessionDurationSeconds - remainingSeconds) * 1000;
    this.state = State.CONNECTED;
    this.profileId = profileId;
    this.lastHeartbeatAt = now - Math.max(0, elapsedMs);
    this.#resetCandidate();
  }

  /** Fin de pause et expiration locale ; a appeler avant chaque observation. */
  tick(now) {
    if (this.state === State.PAUSED && now >= this.pausedUntil) {
      this.state = State.SCAN;
      return [{ type: 'resumed' }];
    }
    if (this.state === State.CONNECTED && now >= this.expiresAt()) {
      this.#disconnect();
      return [{ type: 'expired' }];
    }
    return [];
  }

  /**
   * match : { status: 'none' | 'tooSmall' | 'unknown' | 'ambiguous' | 'matched', profileId? }
   * Retourne les actions : { type: 'login', profileId } | { type: 'heartbeat' }.
   */
  observe(match, now) {
    if (this.state === State.PAUSED) {
      return [];
    }
    if (this.state === State.SCAN) {
      return match.status === 'matched' ? this.#login(match.profileId, now) : [];
    }
    if (match.status !== 'matched') {
      this.#resetCandidate();
      return [];
    }
    if (match.profileId === this.profileId) {
      this.#resetCandidate();
      return this.#heartbeatIfDue(now);
    }
    return this.#confirmSwitch(match.profileId, now);
  }

  logout(now) {
    if (this.state === State.PAUSED) {
      return [];
    }
    const wasConnected = this.state === State.CONNECTED;
    this.#disconnect();
    this.state = State.PAUSED;
    this.pausedUntil = now + this.config.logoutPauseSeconds * 1000;
    return wasConnected ? [{ type: 'logout' }] : [];
  }

  loginFailed() {
    this.#disconnect();
  }

  /** Le serveur a rejete la session (401) : retour a la reconnaissance. */
  sessionLost() {
    this.#disconnect();
  }

  expiresAt() {
    return this.lastHeartbeatAt + this.config.sessionDurationSeconds * 1000;
  }

  remainingSeconds(now) {
    if (this.state !== State.CONNECTED) {
      return 0;
    }
    return Math.max(0, Math.ceil((this.expiresAt() - now) / 1000));
  }

  pauseRemainingSeconds(now) {
    return this.state === State.PAUSED ? Math.max(0, Math.ceil((this.pausedUntil - now) / 1000)) : 0;
  }

  /** Delai avant la prochaine observation selon l'etat. */
  intervalMs(now) {
    if (this.state === State.PAUSED) {
      return Math.max(MIN_PAUSE_WAIT_MS, this.pausedUntil - now);
    }
    return this.state === State.CONNECTED
      ? this.config.scanIntervalConnectedMs
      : this.config.scanIntervalDisconnectedMs;
  }

  #login(profileId, now) {
    this.state = State.CONNECTED;
    this.profileId = profileId;
    this.lastHeartbeatAt = now;
    this.#resetCandidate();
    return [{ type: 'login', profileId }];
  }

  #heartbeatIfDue(now) {
    if (now - this.lastHeartbeatAt < this.config.heartbeatMinIntervalSeconds * 1000) {
      return [];
    }
    this.lastHeartbeatAt = now;
    return [{ type: 'heartbeat' }];
  }

  #confirmSwitch(profileId, now) {
    this.candidateCount = this.candidateId === profileId ? this.candidateCount + 1 : 1;
    this.candidateId = profileId;
    if (this.candidateCount < this.config.switchConfirmations) {
      return [];
    }
    return this.#login(profileId, now);
  }

  #disconnect() {
    this.state = State.SCAN;
    this.profileId = null;
    this.#resetCandidate();
  }

  #resetCandidate() {
    this.candidateId = null;
    this.candidateCount = 0;
  }
}
