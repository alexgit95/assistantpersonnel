## Context

Tous les widgets dépendent d'une session faciale : `SessionInterceptor` résout un `AuthenticatedSession(sessionId, profileId, expiresAtMs)` pour les endpoints `@RequireSession`, et `home.js` vide les emplacements sans session. Les services (météo, actualités) travaillent déjà par `profileId`, et la configuration vit dans `profile_app`. `AdminSecurityConfig` laisse `/api/**` en `permitAll` avec CSRF ignoré ; seul `/admin/**` exige le rôle ADMIN. `WeightAssistantApp` est `defaultEnabled()` : `ProfileAppService.listForProfile` l'active pour tout profil sans ligne. `ProfileService.listFaces` utilise `findAllWithEmbeddings` (tous les profils).

## Goals / Non-Goals

**Goals:**
- Un écran invité global, affiché sans session, configurable uniquement par l'administrateur.
- Garantie serveur qu'aucune donnée privée n'est servie sans session.
- Extension future par simple déclaration `guestAllowed()`.

**Non-Goals:**
- Plusieurs invités (par appareil ou par pièce), invité par profil.
- Prochains passages, Transports ou Poids en invité.
- Modifier la durée de session ou le mécanisme de heartbeat.
- Configuration de l'invité depuis `/configuration` ou `/api/**`.

## Decisions

- **L'invité est un vrai profil**, car `profile_app` référence `profile`. Alternative écartée : tables séparées, qui dupliqueraient `ProfileAppService` et les services météo/actualités. Il est repéré par `guest_setting` (id fixe 1, `profile_id` simple colonne sans clé étrangère, comme `app_session`), pas par une colonne sur `profile` (risque de colonne `NOT NULL` sur une table remplie avec `ddl-auto`).
- **Amorçage idempotent** par `GuestService.ensureGuest()`, appelé par un `ApplicationRunner` et par `AdminController` après `BackupService.restore` (seul appelant). Il crée le profil « Invité » puis Météo à l'emplacement 1 et Actualités à l'emplacement 3 via `ProfileAppService.replaceAll`. Aucune écriture dans une requête de lecture.
- **Résolution sans cookie dans `SessionInterceptor`** : si l'endpoint ou sa méthode porte `@AllowGuest` et que `authenticate` est vide (cookie absent, expiré ou inconnu), l'intercepteur pose `AuthenticatedSession.guest(guestProfileId)` (`sessionId` constant, `isGuest()` dérivé ; aucun changement de constructeur) et `Cache-Control: no-store`. Sans invité en base, 401. Une session valide reste prioritaire. Alternatives écartées : session factice stockée (brouille « session ouverte », heartbeat inutile) ; routes `/api/guest/*` (duplication).
- **`@AllowGuest` au niveau méthode** sur les seules lectures : `GET /api/apps`, `GET /apps/{key}/fragment`, `GET /api/weather`, `GET /api/news/feed`. Les contrôleurs sont annotés `@RequireSession` au niveau classe ; l'intercepteur lit les deux.
- **Liste blanche à la lecture** : `AssistantApp.guestAllowed()` (défaut faux) ; `ProfileAppService.listForGuest` ne renvoie que les applications autorisées avec une ligne explicite, et `requireEnabledForGuest` refuse les autres (fragment 404). Cela neutralise `defaultEnabled()` du poids. Les contrôleurs choisissent selon `session.isGuest()`.
- **Connexion refusée à l'invité** : `AuthController.login` rejette l'identifiant du profil invité, sinon un client pourrait ouvrir une vraie session à son nom et atteindre les endpoints du poids.
- **Exclusions** : `ProfileService` filtre l'identifiant invité dans `listFaces`, `listAdminProfiles` et le décompte de la limite (`count()` moins l'invité), et refuse la purge.
- **Actualités par défaut** : `NewsService.overview` distingue clé absente et liste vide ; pour le profil invité uniquement, clé absente signifie toutes les catégories. Le formulaire enregistre ensuite une liste explicite.
- **Formulaire d'administration** : `GuestAdminController` sous `/admin/guest` (page Thymeleaf sans JavaScript : emplacements, recherche de ville en GET sur la même page puis choix par `POST`, catégories ; CSRF). Il appelle `WeatherService`, `NewsService` et `ProfileAppService` avec l'identifiant de l'invité, sans route `/api`. Validation serveur : applications `guestAllowed`, emplacements 0 à 4, un seul widget par emplacement.
- **Front** : aucune nouvelle API. `home.js` rappelle `api.apps()` sans session ; la réponse est celle de l'invité. Il le fait au chargement sur 401 de `/api/auth/session`, après `logout` et `expired`. Les fragments reçoivent `guest=true` ; `weather.html` et `news.html` exposent `data-guest` et les widgets affichent le message « À configurer par l'administrateur ».
- **Données** : nouvelle table `guest_setting` ; ancien dump sans la table accepté comme pour `prim_setting`, invité recréé après restauration. `docs/database.puml` mis à jour.

## Risks / Trade-offs

- [Fuite de données privées sans session] → liste blanche serveur, `@AllowGuest` limité aux GET, tests dédiés (poids refusé, login invité refusé).
- [Réponses invité servies à un profil par un cache] → `Cache-Control: no-store` sur toute réponse résolue pour l'invité.
- [Délai avant l'invité après un départ sans déconnexion] → inchangé : jusqu'à `ASSISTANT_SESSION_DURATION_MINUTES` ; réglable sans développement.
- [Écran invité vide après une restauration] → réamorçage post-restauration.
- [Lectures anonymes sur le LAN] → données non personnelles, déjà mises en cache (météo 30 min, flux 10 min) ; aucun appel PRIM.
- [Nom « Invité » identique à un profil réel] → sans effet, l'invité est repéré par `guest_setting`.
- [Pointage de `BackupService` sur un tableau de tables] → la validation des tables et colonnes doit connaître `guest_setting` ; test d'un dump ancien et d'un dump récent.
