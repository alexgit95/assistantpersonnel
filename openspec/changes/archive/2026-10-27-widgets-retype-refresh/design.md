## Context

`announcements.js` programme une re-frappe par annonce (`setTimeout` de 15 à 60 s, `typeText(..., {restart: true})`). `weather.js` et `weight.js` chargent leurs données une seule fois. `typeText` ne rejoue rien si le texte est identique sauf avec `restart: true`. Le backend met déjà en cache les prévisions (`WeatherService`, TTL `cacheMinutes`). Le Raspberry Pi 4 impose de rester sobre : aucun nouveau travail serveur.

## Goals / Non-Goals

**Goals:**
- Réutiliser la mécanique des annonces pour météo et poids, avec un cycle par widget.
- Recharger les données à chaque cycle sans coût supplémentaire côté Open-Meteo.
- Rejouer le tracé du graphique du poids.

**Non-Goals:**
- Modifier le backend, les API ou le schéma de données.
- Changer le comportement par élément des annonces.
- Introduire un rechargement indépendant de la réécriture.

## Decisions

- **Module `static/js/retype.js`** : exporte `RETYPE_MIN_MS`, `RETYPE_MAX_MS`, `nextRetypeDelay(random)` et `startRetypeLoop({ run, isPaused, isActive, random })` qui retourne `{ stop() }`. Alternative écartée : dupliquer le code dans chaque widget, source de divergence.
- **Cycle** : `setTimeout` ré-armé après chaque exécution (pas de `setInterval`), pour tirer un nouveau délai aléatoire à chaque fois et éviter le chevauchement si `run` est asynchrone. Si `isActive()` est faux, le cycle s'arrête ; si `isPaused()` est vrai, `run` est sauté et le suivant est armé.
- **Météo** : `loadForecast({ restart })` ; le premier appel garde le comportement actuel, les cycles passent `restart: true` à chaque `typeText`. Sur échec d'un cycle, rien n'est modifié (le premier chargement garde l'affichage d'erreur actuel).
- **Poids** : `loadData({ restart, replay })` ; `drawChart` reçoit un paramètre `replay` qui contourne la sortie anticipée sur signature identique pour retirer puis remettre `is-drawing`. Pause si `card.classList.contains('is-flipped')` ou si `form.contains(document.activeElement)`. Après ajout d'une mesure, le rechargement existant reste sans `restart`.
- **Statut `aria-live`** : le statut n'est pas retapé pendant les cycles, pour éviter les relectures par lecteur d'écran.
- **Désactivation** : pas de boucle si `typingOptions.enabled === false`, comme les annonces.
- **Annonces** : seules les importations changent (`nextRetypeDelay` vient du module partagé, ré-exporté pour les tests existants).

## Risks / Trade-offs

- [Requêtes périodiques de chaque widget] → cache serveur pour la météo ; poids = 2 requêtes paginées légères toutes les 15 à 60 s.
- [Fuite de timers à la suppression du widget] → arrêt quand `root.isConnected` est faux, et tests dédiés.
- [Écrasement d'une saisie] → pause quand la carte est retournée ou le formulaire a le focus.
- [Flash d'erreur répétitif] → un échec périodique conserve l'affichage précédent sans message.
