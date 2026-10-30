## 1. Données et amorçage

- [x] 1.1 Créer l'entité `GuestSetting` (id fixe 1, `profile_id` simple colonne) et son dépôt, compatibles SQLite et PostgreSQL
- [x] 1.2 Créer `GuestService` (`guestProfileId`, `isGuest`, `ensureGuest` idempotent : profil « Invité », Météo emplacement 1, Actualités emplacement 3 via `ProfileAppService.replaceAll`)
- [x] 1.3 Appeler `ensureGuest` au démarrage (`ApplicationRunner`) et après la restauration (dans `AdminController`, seul appelant de `BackupService.restore`)
- [x] 1.4 Mettre à jour `docs/database.puml` avec `guest_setting`
- [x] 1.5 Tests JUnit 5 + Mockito : amorçage, idempotence, réamorçage

## 2. Liste blanche et résolution sans session

- [x] 2.1 Ajouter `AssistantApp.guestAllowed()` (faux par défaut) et le passer à vrai pour Météo et Actualités
- [x] 2.2 Ajouter `ProfileAppService.listForGuest` et `requireEnabledForGuest` (filtre `guestAllowed`, lignes explicites seulement)
- [x] 2.3 Créer l'annotation `@AllowGuest` et `AuthenticatedSession.guest(...)` / `isGuest()` sans changer le constructeur
- [x] 2.4 Étendre `SessionInterceptor` : invité si cookie absent, expiré ou inconnu, session valide prioritaire, `Cache-Control: no-store`, 401 sans invité
- [x] 2.5 Annoter les lectures (`GET /api/apps`, fragments, `GET /api/weather`, `GET /api/news/feed`) et brancher les services sur `isGuest()` ; ajouter `guest` au modèle des fragments
- [x] 2.6 Refuser dans `AuthController.login` l'identifiant du profil invité
- [x] 2.7 Tests : poids non servi malgré `defaultEnabled`, fragment interdit, écriture sans session en 401, session prioritaire, cookie expiré, login invité refusé, `no-store`

## 3. Exclusions du profil invité

- [x] 3.1 Exclure l'invité de `ProfileService.listFaces`, `listAdminProfiles` et du décompte pour `ASSISTANT_MAX_PROFILES`
- [x] 3.2 Refuser la purge de l'invité
- [x] 3.3 Tests : liste des visages, limite de profils avec invité, purge refusée

## 4. Actualités et météo invitées

- [x] 4.1 `NewsService` : pour l'invité, `categoryIds` absent = toutes les catégories ; liste enregistrée ou vide appliquée telle quelle
- [x] 4.2 Exposer `data-guest` dans `weather.html` et `news.html` et afficher « À configurer par l'administrateur » dans `weather.js` et `news.js` en mode invité
- [x] 4.3 Tests JUnit (clé absente, nouvelle catégorie, sélection enregistrée, sélection vide) et test Node du message des actualités (le message météo n'a pas de banc de test Node existant)

## 5. Formulaire d'administration

- [x] 5.1 Créer `GuestAdminController` (`GET/POST /admin/guest`, enregistrement des emplacements, de la ville et des catégories) avec validation des applications autorisées, des emplacements 0 à 4 et des doublons ; la recherche de ville est un formulaire GET de la même page, sans route JSON
- [x] 5.2 Créer le gabarit de formulaire (5 emplacements, recherche de ville, catégories d'actualités) et le lien depuis `/admin`
- [x] 5.3 Appeler `WeatherService`, `NewsService` et `ProfileAppService` avec l'identifiant de l'invité
- [x] 5.4 Tests : accès réservé ADMIN, CSRF, application interdite, emplacement invalide, doublon, ville enregistrée sans effet sur les profils

## 6. Sauvegarde

- [x] 6.1 Faire connaître `guest_setting` à `BackupService` (export, validation, restauration) et accepter un dump sans cette table
- [x] 6.2 Tests : dump ancien, dump récent, réamorçage de l'invité

## 7. Front

- [x] 7.1 `home.js` : appeler `loadProfileApps` sans session (401 de `/api/auth/session`), après `logout` et `expired`, pour afficher l'écran invité
- [x] 7.2 Vérifier la bascule invité vers profil et profil vers invité sans minuteries résiduelles des widgets

## 8. Documentation et validation

- [x] 8.1 Mettre à jour `README.md`, `CHANGELOG.md` et `pom.xml` (version 0.0.12)
- [x] 8.2 Exécuter la suite Maven via `C:\USINE_LOGICIELLE\apache-maven\bin` et les tests Node, corriger les échecs
- [x] 8.3 Valider manuellement : écran invité au chargement, après déconnexion et expiration, réglage depuis `/admin/guest`, poids invisible sans session
