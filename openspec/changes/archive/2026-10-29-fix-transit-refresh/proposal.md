## Why

L'heure de dernière actualisation du widget Transports semble figée quand l'utilisateur reste connecté sans recharger la page. Le navigateur ne relit le serveur que toutes les 10 minutes, ce qui est la durée de la session faciale, et l'actualisation planifiée côté serveur saute régulièrement un tour parce que sa garde d'âge compare le flux à 10 minutes pile.

## What Changes

- Widget Transports : relecture du serveur toutes les 60 secondes (au lieu de 10 minutes), sans appel PRIM supplémentaire puisque le serveur répond depuis son cache.
- Le widget ne met à jour son affichage (heure, lignes, animation d'écriture) que si la donnée a changé : `fetchedAt`, `notice` ou `paused` différents ; sinon il ne fait rien.
- En cas d'échec d'une relecture, l'affichage et l'heure précédents sont conservés.
- Serveur : la garde d'âge de l'actualisation (planifiée et à la demande) tolère quelques secondes d'avance, pour qu'un tick exactement 10 minutes après la précédente actualisation rafraîchisse bien le flux. Les appels à PRIM restent espacés de 10 minutes au plus.
- Le mode TEST n'est pas modifié.
- Version du projet portée à 0.0.9 (`pom.xml`) et changelog.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-transit`: relecture périodique du widget synchronisée avec le serveur, et actualisation planifiée à l'échéance.

## Impact

- Code : `TransitService` (gardes d'âge de `refreshIfDue` et `refreshLive`), `transit.js` (intervalle de relecture et comparaison avant rendu).
- Tests : `TransitServiceTest`, `transit-widget.test.mjs`.
- Documentation : `README.md`, `CHANGELOG.md`, `pom.xml` (0.0.9).
- Aucun changement d'API, de schéma de base de données ni de dépendance. Le nombre d'appels à PRIM et le budget journalier ne changent pas.
