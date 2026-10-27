## 1. Module partagé

- [x] 1.1 Créer `static/js/retype.js` avec `nextRetypeDelay` et `startRetypeLoop` (pause, arrêt, ré-armement)
- [x] 1.2 Migrer `announcements.js` vers `nextRetypeDelay` partagé (ré-export conservé)
- [x] 1.3 Tests JS du module : bornes du délai, pause, arrêt, ré-armement

## 2. Widget météo

- [x] 2.1 Ajouter `restart` à `loadForecast` et conserver l'affichage en cas d'échec périodique
- [x] 2.2 Démarrer une boucle par widget (vues principale et compacte), désactivée si l'écriture l'est
- [x] 2.3 Tests JS : rechargement et réécriture, échec silencieux, arrêt à la déconnexion (validé manuellement)

## 3. Widget poids

- [x] 3.1 Ajouter `restart` à `loadData` et le paramètre `replay` à `drawChart`
- [x] 3.2 Démarrer une boucle par widget avec pause (carte retournée ou focus dans le formulaire)
- [x] 3.3 Ne pas retaper le statut `aria-live` pendant les cycles
- [x] 3.4 Tests JS : rejeu du tracé, pause, réduction des animations, arrêt (validé manuellement)

## 4. Documentation et validation

- [x] 4.1 Mettre à jour `README.md` et `CHANGELOG.md`
- [x] 4.2 Exécuter les tests (JS et Maven) et valider la change OpenSpec
