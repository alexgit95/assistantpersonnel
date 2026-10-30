## Why

Le widget « Transports » n'affiche que les alertes trafic. L'utilisateur veut aussi savoir dans combien de minutes arrivent les prochains bus, trams ou trains à ses arrêts habituels, sans consulter une application externe. L'API PRIM « Prochains passages » est accessible avec la clé PRIM déjà saisie en administration.

## What Changes

- Nouveau widget distinct « Prochains passages » (clé `transit-passages`), activable par profil comme les autres.
- Choix de 1 à 3 arrêts par profil, via une recherche à la demande sur l'API publique Île-de-France Mobilités (jeu de données `arrets`, champs `arrid`, `arrname`, `arrtown`, `arrtype`). Aucun téléchargement de l'export complet, aucun référentiel local, aucune nouvelle table : seuls les arrêts choisis sont conservés dans la configuration du profil.
- Affichage, pour chaque arrêt, des 4 prochains passages sous la forme « dans X min » (avec ligne et destination) et de l'heure de mise à jour de la donnée.
- Appel PRIM `stop-monitoring` (`MonitoringRef = STIF:StopPoint:Q:<arrid>:`) avec la clé PRIM existante, uniquement tant qu'une session faciale est ouverte, au rythme d'une fois par minute par arrêt. Cache serveur partagé entre profils, budget journalier PRIM et pause nocturne existants appliqués.
- Sans clé PRIM, le widget affiche un message demandant de la renseigner.
- Mode TEST : réponse construite à partir de `docs/reponse-prochain-passage.json`, avec horaires décalés aléatoirement dans le futur, sans appel externe.
- Pas de job quotidien de revérification des arrêts choisis.

## Capabilities

### New Capabilities
- `transit-next-passages`: widget des prochains passages : recherche et sélection d'arrêts (3 max), appel PRIM `stop-monitoring`, affichage en minutes restantes, rafraîchissement conditionné à la session faciale, clé absente, quiet hours, budget, mode TEST.

### Modified Capabilities
<!-- Aucune : les règles de budget, de pause et de clé de app-transit sont réutilisées sans modification de leurs exigences. -->

## Impact

- Code : nouveau paquet `com.assistant.apps.transit.passages` (application, contrôleur REST avec annotations OpenAPI, service, client PRIM stop-monitoring, client de recherche d'arrêts, passerelle de test), widget front (`templates/fragments/widgets`, `static/js/widgets`).
- Réutilise `PrimKeyService`, `ApiQuotaService`, `QuietHours`, `ExternalApiModeService`, `OutboundHttp` et la configuration JSON des profils.
- Aucune modification de schéma de base de données ni de pipeline CI/CD.
- Dépendances externes : `prim.iledefrance-mobilites.fr` (clé) et `data.iledefrance-mobilites.fr` (public, recherche uniquement).
- Documentation : README, CHANGELOG, tests JUnit 5 et Mockito.
