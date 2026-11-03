// Un QR a usage unique renouvele automatiquement (consomme, expire ou proche de l'expiration).
import { qrNeedsRenewal } from './remote-capture.js';

const RETRY_AFTER_UNAVAILABLE_MS = 60_000;

/**
 * deps : `issue()` -> `{ qrId, url, expiresInSeconds }`, `poll(qrIds)` -> `{ qr: [{ qrId, state }] }`,
 * `show(url)` / `clear()` pour l'affichage, `now()` optionnel. `tick()` est a appeler a chaque poll du Pi.
 */
export function createQrTicket({ issue, poll, show, clear, now = Date.now }) {
  let ticket = null;
  let retryAt = 0;

  async function renew() {
    if (now() < retryAt) {
      return;
    }
    try {
      const issued = await issue();
      ticket = { qrId: issued.qrId, url: issued.url, expiresAtClientMs: now() + issued.expiresInSeconds * 1000 };
      await show(issued.url);
    } catch (error) {
      ticket = null;
      if (error?.status === 503) {
        retryAt = now() + RETRY_AFTER_UNAVAILABLE_MS;
      }
      if (error?.status === 503 || error?.status === 401) {
        clear();
        return;
      }
      throw error;
    }
  }

  return {
    async tick() {
      let status;
      if (ticket) {
        const state = await poll([ticket.qrId]);
        status = state.qr.find((entry) => entry.qrId === ticket.qrId)?.state;
      }
      if (qrNeedsRenewal(ticket, status, now())) {
        await renew();
      }
    },
    get current() {
      return ticket;
    },
    reset() {
      ticket = null;
      clear();
    },
  };
}
