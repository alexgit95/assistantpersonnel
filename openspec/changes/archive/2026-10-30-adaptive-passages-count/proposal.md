## Why

Le widget « Prochains passages » affiche toujours 4 passages par arrêt. Avec un ou deux arrêts, la tuile garde de la place libre alors que des passages utiles (lignes peu fréquentes) restent masqués.

## What Changes

- Le nombre de passages affichés par arrêt dépend du nombre d'arrêts configurés par le profil : 6 passages avec 1 ou 2 arrêts, 4 passages avec 3 arrêts. L'affichage reste borné à 12 lignes.
- Le mode TEST simule assez de passages (7 visites) pour remplir 6 lignes sans régénérer le cache à chaque appel.
- La description Swagger de `GET /api/transit-passages/next` reflète la règle.
- Version du projet portée à 0.0.13 (`pom.xml`) et entrée correspondante dans `CHANGELOG.md`.
- Aucun changement de l'appel PRIM, du cache, du budget ni du front : la troncature reste côté serveur.

## Capabilities

### New Capabilities

### Modified Capabilities
- `transit-next-passages`: l'exigence « Affichage des quatre prochains passages » devient un affichage adaptatif (6 passages pour 1 ou 2 arrêts, 4 pour 3 arrêts).

## Impact

- Code : `NextPassagesService` (limite calculée par profil), `MockPassageGateway` (`VISITS`), `TransitPassagesController` (libellé Swagger).
- Tests : `NextPassagesServiceTest` (cas 1, 2 et 3 arrêts, arrêt indisponible compté, cache TEST stable), `MockPassageGatewayTest`.
- Documentation : `README.md`, `CHANGELOG.md`, spec `transit-next-passages`.
- Build : `pom.xml` (version 0.0.12 → 0.0.13).
- Aucun impact sur la base de données, le quota PRIM ni la mémoire.
