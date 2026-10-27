## Why

Les annonces réécrivent leurs textes (effet machine à écrire) à intervalle aléatoire de 15 à 60 s, ce qui donne un tableau de bord vivant. Les widgets météo et poids restent figés après leur premier affichage et leurs données ne sont jamais rafraîchies.

## What Changes

- Nouveau module partagé `retype.js` : boucle de réécriture à délai aléatoire borné 15 s à 60 s, avec pause conditionnelle et arrêt.
- Widget météo : une boucle **par widget** qui recharge les prévisions puis ré-écrit tous ses textes (vue principale et compacte).
- Widget poids : une boucle **par widget** qui recharge les mesures, ré-écrit les textes et rejoue l'animation de tracé du graphique ; suspendue quand la carte est retournée ou qu'un champ du formulaire a le focus.
- En cas d'échec d'un rechargement périodique, l'affichage précédent est conservé.
- Les annonces gardent leur boucle par élément ; `nextRetypeDelay` et les bornes sont déplacés dans le module partagé.
- La boucle est désactivée quand l'animation d'écriture est désactivée.

## Capabilities

### New Capabilities
- `widget-periodic-refresh`: réécriture et rechargement périodiques aléatoires (15 s à 60 s) des widgets météo et poids, avec règles de pause et d'arrêt.

### Modified Capabilities

## Impact

- Frontend : `static/js/widgets/weather.js`, `static/js/widgets/weight.js`, `static/js/widgets/announcements.js`, nouveau `static/js/retype.js`.
- Tests JS dans `src/test/js` (module partagé, deux widgets, export déplacé de `nextRetypeDelay`).
- Aucun changement backend, API, schéma de base ni PlantUML. Le cache serveur des prévisions limite les appels Open-Meteo.
- Documentation : `README.md` et `CHANGELOG.md`.
