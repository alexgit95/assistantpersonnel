import { api } from './api.js';
import { animateMarkedText } from './typewriter.js';

async function start() {
  let typing = { enabled: true, speedMs: 25 };
  try {
    const config = await api.config();
    typing = { enabled: config.typingEnabled, speedMs: config.typingSpeedMs };
  } catch (error) {
    console.warn('Configuration du thème indisponible', error);
  }
  animateMarkedText(document, typing);

  document.querySelectorAll('.purge-form').forEach((form) => {
    form.addEventListener('submit', (event) => {
      const name = form.dataset.profileName || 'ce profil';
      if (!window.confirm(`Purger ${name} et toutes ses données ? La personne devra créer un nouveau profil.`)) {
        event.preventDefault();
      }
    });
  });

  document.querySelectorAll('.backup-restore-form').forEach((form) => {
    form.addEventListener('submit', (event) => {
      if (!window.confirm('Cette restauration remplacera toutes les données et invalidera les sessions des profils. Continuer ?')) {
        event.preventDefault();
        return;
      }
      form.querySelector('input[name="confirmed"]').value = 'true';
    });
  });
}

start();
