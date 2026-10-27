## 1. Socle du projet

- [x] 1.1 Initialiser Spring Boot 4.x (Java 25) avec web, Thymeleaf, JPA, validation, springdoc-openapi-starter-webmvc-ui
- [x] 1.2 Profils `application-local` (SQLite, ddl-auto) et `application-prod` (PostgreSQL, ddl-auto)
- [x] 1.3 Classe `@ConfigurationProperties` validée et placeholders `${ASSISTANT_*:défaut}` pour tous les réglages (voir design D7c)
- [x] 1.4 Réglages JVM sobres (Serial GC, mémoire bornée) et Dockerfile
- [x] 1.5 Créer `docs/database.puml`, `README.md` (tableau des variables d'environnement avec exemples `.env` et Portainer), `CHANGELOG.md`
- [x] 1.6 Fournir `mvnw`, le plugin JaCoCo (rapport `target/site/jacoco/`) et un `Dockerfile` racine acceptant `GIT_COMMIT`, compatibles avec la CI existante (non modifiée)
- [x] 1.7 Tests indépendants de PostgreSQL et du réseau (SQLite/mocks)

## 2. Profils et embeddings

- [x] 2.1 Entités `Profile` et `FaceEmbedding` (vecteur en TEXT JSON) compatibles SQLite/PostgreSQL
- [x] 2.2 Services et API : liste `GET /api/faces`, création, ajout d'embeddings, suppression
- [x] 2.3 Règles : limite optionnelle `ASSISTANT_MAX_PROFILES` (0 = illimité), modification réservée au profil connecté
- [x] 2.5 Avatar (data URL TEXT, 128x128)
- [x] 2.4 Tests unitaires JUnit 5 + Mockito

## 3. Session et authentification

- [x] 3.1 Entité `AppSession`, cookie HttpOnly SameSite=Strict, filtre de résolution du profil
- [x] 3.2 Endpoints login, heartbeat, logout, config client avec annotations OpenAPI
- [x] 3.3 Prolongation glissante, purge planifiée des sessions expirées
- [x] 3.4 Tests unitaires (expiration, prolongation, 401)

## 4. Reconnaissance côté navigateur

- [x] 4.1 Intégrer `@vladmandic/face-api` et les modèles : CDN en local, assets téléchargés au build Docker
- [x] 4.2 Module de capture caméra et miniature
- [x] 4.3 Machine à états SCAN / CONNECTÉ / PAUSE avec intervalles configurables
- [x] 4.4 Comparaison par distance minimale, visage le plus grand, N confirmations pour bascule
- [x] 4.5 Heartbeat limité à 1 par 30 s et bouton de déconnexion avec pause
- [x] 4.6 Endpoint `GET /api/config` et réglages de qualité (taille d'entrée, confiance, seuil, marge, taille min du visage, largeur caméra, moteur auto/webgl/wasm/cpu)
- [x] 4.7 Mode debug de calibration (distance, écart, durée)
- [x] 4.8 Validation des variables au démarrage et test sur téléphone récent et Raspberry Pi (navigateur)

## 5. Parcours de création de profil

- [x] 5.1 Page Thymeleaf de création : nom, captures guidées (bornes configurables)
- [x] 5.2 Rejet des doublons et des captures sans visage
- [x] 5.3 Écran de choix de l'avatar parmi les photos capturées
- [x] 5.4 « Refaire mes captures » à tout moment depuis une session (ajout ou remplacement)
- [x] 5.5 Message « Je ne suis pas reconnu » : créer un profil ou contacter l'administrateur

## 5b. Administration

- [x] 5b.1 Spring Security : compte admin depuis `ASSISTANT_ADMIN_USERNAME`/`ASSISTANT_ADMIN_PASSWORD` (BCrypt), échec au démarrage si le mot de passe est absent
- [x] 5b.2 Page `/admin` de connexion, liste des profils (nom, avatar), purge avec confirmation
- [x] 5b.3 Verrouillage temporaire après échecs de connexion, tests unitaires

## 5c. Charte graphique

- [x] 5c.1 Feuille de style unique `theme.css` avec variables (`#000`, `#fff`, gris)
- [x] 5c.2 Module JS `typewriter` appliqué aux textes dynamiques, `aria-label` complet, `prefers-reduced-motion`, plafond de durée
- [x] 5c.3 Variables `ASSISTANT_UI_TYPING_ENABLED` et `ASSISTANT_UI_TYPING_SPEED_MS` exposées au client
- [x] 5c.4 Appliquer le thème à tous les écrans (accueil, création, admin, widgets, graphique du poids en blancs/gris)

## 6. Framework d'applications

- [x] 6.1 Contrat `AssistantApp` et découverte automatique
- [x] 6.2 Entité `ProfileApp` (config TEXT JSON) et API d'activation, ordre, configuration
- [x] 6.3 Écran d'accueil Thymeleaf avec fragments de widgets
- [x] 6.4 Tests d'isolation des données entre profils
- [x] 6.5 Accueil à géométrie fixe : horloge, aperçu webcam « W », avatar/menu, emplacement principal central et quatre emplacements secondaires sans défilement
- [x] 6.6 Regrouper configuration et accès administration dans le menu de l'avatar en haut à droite
- [x] 6.7 Page dédiée de configuration des widgets avec glisser-déposer vers les cinq emplacements fixes
- [x] 6.8 Persister les affectations par profil, gérer les emplacements vides et préserver les anciennes préférences/configurations
- [x] 6.9 Tests d'affectation, persistance, isolation et absence de défilement desktop/mobile

## 7. Applications initiales

- [x] 7.1 Poids : entité `WeightEntry` (dixièmes de kg, entier), saisie virgule/point, validation 20,0-400,0, API paginée, widget avec graphique, tests
- [x] 7.2 Météo : géocodage par nom de ville (choix si ambigu), client Open-Meteo, cache borné 30 min, widget, gestion d'erreur, tests
- [x] 7.3 Tous les libellés en français (`lang="fr"`, dates fr-FR)
- [x] 7.4 Graphique du poids : labels de valeurs sur les points, animation de tracé, respect de `prefers-reduced-motion`, tests

## 8. HTTPS et déploiement

- [x] 8.1 `docker-compose.yml` compatible Portainer (Caddy `tls internal` avec configs inline, application, PostgreSQL, limites mémoire, variables `${VAR:-défaut}`)
- [x] 8.2 Endpoint de téléchargement de `root.crt` (volume Caddy en lecture seule)
- [x] 8.3 Guide d'installation du certificat (Windows, Android, iOS) dans le README

## 8b. Sauvegarde et restauration

- [x] 8b.1 Export JSON en flux de toutes les entités (hors sessions) via la métamodel JPA, avec pagination
- [x] 8b.2 Page admin : bouton de téléchargement du dump
- [x] 8b.3 Restauration : upload plafonné (`ASSISTANT_BACKUP_MAX_UPLOAD_MB`), validation avant écriture, transaction unique, ordre des clés étrangères, remise à niveau des séquences PostgreSQL, invalidation des sessions
- [x] 8b.4 Tests : aller-retour export/import, fichier invalide, rollback, compatibilité SQLite et PostgreSQL

## 9. Finalisation

- [x] 9.1 Vérifier la documentation Swagger sur tous les contrôleurs
- [x] 9.2 Compiler et exécuter les tests avec Maven (`C:\USINE_LOGICIELLE\apache-maven\bin`)
- [x] 9.3 Mettre à jour README, CHANGELOG et `docs/database.puml`
