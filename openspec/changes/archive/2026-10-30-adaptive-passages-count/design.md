## Context

`NextPassagesService.MAX_PASSAGES` (4) tronque l'affichage de chaque arrêt (`view`) et fixe le seuil de régénération du cache simulé en mode TEST (`testEntry`). Le cache réel stocke le flux complet de PRIM ; la troncature est donc purement une décision d'affichage par profil. Le front affiche tout ce que renvoie `/next`.

## Goals / Non-Goals

**Goals:**
- 6 passages par arrêt avec 1 ou 2 arrêts configurés, 4 avec 3 arrêts (12 lignes au maximum).
- Aucune variation de l'appel PRIM, du cache, du quota ni du front.

**Non-Goals:**
- Nombre de passages configurable par profil ou par propriété.
- Changement du plafond de 3 arrêts.

## Decisions

- **Limite calculée dans `next()`** à partir de `readStops(...).size()` : `perStop = size <= 2 ? 6 : 4`, passée à `view` et `testEntry`. Deux constantes nommées (`PASSAGES_FEW_STOPS = 6`, `PASSAGES_MANY_STOPS = 4`) et un seuil (`FEW_STOPS_MAX = 2`). Alternative écartée : propriété de configuration, qui ajoute de la surface pour un gain faible.
- **Arrêts comptés tels qu'enregistrés**, y compris un arrêt « Arrêt indisponible » : le nombre ne dépend pas de la réponse PRIM, ce qui évite un affichage instable.
- **Mode TEST** : `testEntry` régénère quand il reste moins de `perStop` passages futurs ; `MockPassageGateway.VISITS` passe de 5 à 7 pour que 6 passages soient toujours atteignables.
- **Swagger** : libellé « 4 à 6 prochains passages selon le nombre d'arrêts ».
- **Versioning** : 0.0.12 → 0.0.13 dans `pom.xml`, avec section `[0.0.13]` dans `CHANGELOG.md`.

## Risks / Trade-offs

- [Cache simulé régénéré à chaque appel si `VISITS` < limite] → `VISITS` = 7, couvert par un test.
- [Valeurs 6/4/2 en dur] → constantes nommées uniques dans le service, facilement ajustables.
- [Hauteur du widget] → borne de 12 lignes identique à l'existant ; `.passages-stops` conserve `overflow: auto`.
