## Context

Le widget `transit` (alertes trafic) s'appuie sur `PrimClient`, `PrimKeyService`, `ApiQuotaService` (clé `prim`, budget journalier persisté), `QuietHours` et `ExternalApiModeService`. Les préférences par profil sont stockées en JSON via `ProfileAppService`. Le Raspberry Pi 4 (4 Go) impose une empreinte mémoire minimale. Le jeu de données `arrets` est volumineux ; l'API publique `records` permet une recherche ciblée sans clé.

## Goals / Non-Goals

**Goals:**
- Afficher les 4 prochains passages de 1 à 3 arrêts par profil, en minutes restantes.
- Rechercher les arrêts à la demande, sans stockage de référentiel.
- Réutiliser clé, budget, pause nocturne, mode TEST et proxy sortant existants.

**Non-Goals:**
- Télécharger ou stocker l'export complet des arrêts, ou le rafraîchir quotidiennement.
- Revérifier périodiquement les arrêts enregistrés.
- Filtrer par ligne ou par sens à la configuration.
- Modifier le widget `transit`, le schéma de base ou la CI/CD.

## Decisions

- **Recherche à la demande** : `GET .../arrets/records?where=search(arrname,"<q>")&select=arrid,arrname,arrtown,arrtype&limit=20`. Alternative écartée : export complet quotidien (volume, RAM, table à maintenir). Texte validé (≥ 3 caractères, guillemets et barres obliques inverses échappés), cache court en mémoire borné (taille maximale, TTL ~5 min).
- **Stockage** : liste de 3 arrêts max dans `configJson` du profil (`stops`), comme `favoriteLineIds`. Aucune entité, donc `docs/database.puml` inchangé.
- **Application distincte** `transit-passages` (nouvelle `AssistantApp`, fragment et module JS propres) pour ne pas alourdir `transit`.
- **Appel PRIM** : `MonitoringRef=STIF:StopPoint:Q:<arrid>:`, en-tête `apikey`, via `OutboundHttp`, timeouts courts comme `PrimClient`. Heure = `ExpectedArrivalTime` sinon `ExpectedDepartureTime`, tri croissant, passages écoulés ignorés, 4 premiers conservés.
- **Cache serveur par arrêt** (~55 s) avec fusion des demandes concurrentes, partagé entre profils. Dernière valeur connue conservée pour la pause, le budget épuisé et les échecs. Nombre d'entrées borné.
- **Condition de session** : le front interroge toutes les 60 s tant que l'état de session est connecté et arrête sinon ; le serveur exige la session sur chaque endpoint et ne déclenche aucun appel PRIM planifié.
- **Budget** : réutilise le compteur PRIM (`ApiQuotaService`, clé `prim`, quota 800/jour). Environ 30 min de connexion par jour, soit ≤ 90 appels pour 3 arrêts, compatible avec les 10 minutes d'actualisation des alertes. Pause nocturne via `QuietHours`.
- **Mode TEST** : passerelle de test qui charge `docs/reponse-prochain-passage.json` (embarqué comme `integration/`), puis recale chaque horaire à `now + décalage` aléatoire croissant, via `Random` injectable pour des tests déterministes.
- **API REST** : contrôleur sous `/api/transit-passages` (recherche, lecture/enregistrement des arrêts, passages) avec `@Tag`, `@Operation`, `@ApiResponse` (200, 400, 401/403, 404, 500, 503).
- **Affichage** : minutes calculées côté serveur à l'instant de réponse (`minutes`), plus `updatedAt`, pour éviter les écarts d'horloge client ; le front recalcule seulement l'affichage entre deux actualisations si nécessaire.

## Risks / Trade-offs

- [Préfixe `StopPoint:Q` valable seulement pour certains modes] → Vérifier avec la clé réelle sur bus, métro, RER et tram pendant l'implémentation ; à défaut, dériver le préfixe de `arrtype` ou utiliser `StopArea`.
- [Arrêt supprimé côté IDFM, sans revérification] → Message « Arrêt indisponible » et retrait possible.
- [Homonymes nombreux (« Carnot »)] → Nom, ville et type affichés dans la recherche ; ligne et destination affichées dans les passages.
- [API publique de recherche indisponible] → Seule la configuration est touchée, les passages restent servis.
- [Injection dans la clause `where` ODS] → Validation et échappement stricts du texte saisi.
- [Budget partagé avec les alertes] → Réservation atomique existante ; dernière valeur servie si épuisé.
- [Données de test périmées] → Horaires toujours recalés sur l'instant courant.
