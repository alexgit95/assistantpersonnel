## Context

Projet vierge. Assistant personnel pour 3 personnes, sur réseau local uniquement, utilisable depuis tout appareil avec caméra. Cible de déploiement : Raspberry Pi 4 (4 Go) via Docker Compose (Arcane). Dev local : SQLite ; prod : PostgreSQL ; pas d'outil de migration (Hibernate `ddl-auto`). Conventions : Spring Boot 4.x, Java 25, springdoc obligatoire, tests JUnit 5 + Mockito, `docs/database.puml`, README et CHANGELOG à jour, CI GitHub existante intouchée.

## Goals / Non-Goals

**Goals:**
- Identification faciale entièrement calculée dans le navigateur.
- Session glissante de 10 min, bascule automatique, déconnexion.
- Ajout simple d'une application sans toucher au cœur.
- Empreinte mémoire et CPU minimale côté Pi.

**Non-Goals:**
- Liveness / anti-usurpation (accepté : foyer de 3, LAN pur).
- Exposition Internet.
- Application Google Calendar (change ultérieur ; OAuth et tokens chiffrés).
- Migration de schéma (Flyway/Liquibase).

## Decisions

### D1. Frontend Thymeleaf + JS statique (pas de SPA)
Thymeleaf rend la coquille de page et les fragments de widgets ; la reconnaissance tourne en JavaScript (modules statiques). Les widgets chargent leur contenu par fragments HTML (`fetch` + insertion, ou htmx si adopté). Raison : un seul artefact Spring Boot, pas de chaîne Node. Alternative rejetée : React/Vite (build supplémentaire inutile ici).

### D2. Reconnaissance côté navigateur avec `@vladmandic/face-api`
Détecteur léger (TinyFaceDetector) + embeddings 128 dimensions, modèles servis en statique et mis en cache navigateur. Comparaison par distance euclidienne, minimum sur tous les embeddings du profil, seuil configurable. Alternative : MediaPipe (pas d'embeddings).

Mode `local` : le navigateur télécharge les URL jsDelivr avec versions épinglées (`@vladmandic/face-api@1.7.15`, `@tensorflow/tfjs-backend-wasm@4.22.0`). Mode `prod`/Docker : le stage Node du Dockerfile récupère ces mêmes paquets lors du build et les ajoute à `static/vendor/face-api`; aucune dépendance CDN n'existe au runtime. Le navigateur précharge les scripts, manifestes, shards et WASM avec une barre de progression puis laisse le cache HTTP utiliser les entêtes de cache CDN ou les entêtes `/vendor/**` (30 jours). Aucun flux caméra ni embedding ne quitte le navigateur pour le CDN.

### D3. Le backend stocke, ne reconnaît pas
Endpoint public LAN `GET /api/faces` : id, nom, embeddings. Le client décide et appelle `POST /api/auth/login {profileId}`. Le backend fait confiance au client (limite assumée). Les embeddings sont ceux de tous les profils, exposés avant authentification : accepté sur LAN, documenté.

### D4. Session serveur glissante
Cookie `HttpOnly`, `SameSite=Strict`, identifiant opaque. Table `app_session(id, profile_id, expires_at)`. `POST /api/auth/heartbeat` prolonge `expires_at = now + durée`, limité côté client à 1 par 30 s. Purge des sessions expirées par tâche planifiée. Pas de collection en mémoire non bornée. Toute requête de données résout le `profileId` depuis la session uniquement.

### D5. Machine à états côté client
SCAN (rapide, 1 s) -> CONNECTÉ (lent, 5 s) -> PAUSE (60 s après déconnexion) -> SCAN. En CONNECTÉ : profil courant revu = heartbeat ; autre profil reconnu N=2 fois de suite = nouveau login (bascule). Visage le plus grand retenu. Tous les intervalles, seuil, durée de session, pause et N sont configurables (`application.properties`, exposés au client via un endpoint de configuration).

### D6. Framework d'applications
Interface `AssistantApp` (clé, titre, fragment Thymeleaf, route de données) implémentée par chaque module Spring et découverte automatiquement (injection de `List<AssistantApp>`). Table `profile_app(profile_id, app_key, position, enabled, config)` avec `config` en `TEXT` (JSON), compatible SQLite et PostgreSQL. Ajouter une application = nouveau module + fragment, sans modifier le cœur.

### D17. Accueil à emplacements fixes et configuration par avatar
L'accueil utilise une grille fixe qui tient dans la fenêtre sans défilement : barre supérieure avec horloge à gauche, aperçu webcam dans le carré « W », avatar/menu à droite ; zone widgets avec un emplacement principal central et quatre emplacements secondaires (haut/bas à gauche et à droite). Les emplacements ne sont pas déplaçables ni redimensionnables ; le profil choisit seulement quel widget occupe chacun.

Tous les accès à la configuration passent par le menu ouvert en cliquant sur l'avatar. Le menu contient l'accès à une page dédiée de configuration des widgets et l'accès à l'administration selon les droits. La page dédiée expose les widgets disponibles et les cinq emplacements cibles ; l'utilisateur affecte/déplace les widgets par glisser-déposer, puis enregistre. Aucun panneau de configuration n'est affiché sur l'accueil.

Un widget ne peut occuper qu'un seul emplacement à la fois ; un emplacement peut rester vide. Un dépôt sur un emplacement occupé échange les widgets si le widget déplacé vient d'un autre emplacement ; si le widget vient de la liste disponible, le widget remplacé retourne à cette liste. L'affectation est enregistrée par profil, séparément de la configuration métier de chaque widget. Les valeurs de `position` sont stables : `0` principal, `1` haut gauche, `2` bas gauche, `3` haut droite, `4` bas droite, `-1` disponible/non affecté. Les anciennes préférences ordonnées gardent leur ordre : les positions valides sont conservées et les anciennes valeurs hors plage sont attribuées au premier slot libre dans l'ordre précédent, sans perdre les configurations JSON existantes.

La grille conserve ses relations spatiales sur petit écran en adaptant les dimensions ; ni l'accueil ni la page de configuration ne doivent demander de défilement. Les widgets doivent donc adapter leur contenu à leur emplacement.

### D7. Modèle de données (compatible SQLite/PostgreSQL)
`profile(id, name, avatar TEXT data URL)`, `face_embedding(profile_id, vector TEXT)`, `app_session`, `profile_app`, `weight_entry(profile_id, entry_date, kg)`. Types standards uniquement, embeddings sérialisés en texte JSON.

### D7b. Avatar, relance des captures et administrateur
L'avatar est la seule image conservée : choisie parmi les captures, réduite à 128x128 côté navigateur, stockée en data URL dans une colonne TEXT (évite `bytea`/`oid` vs `BLOB`). Une personne mal reconnue n'a pas de session et ne peut donc pas modifier un profil existant : l'administrateur purge le profil (suppression du profil, de ses embeddings, de son avatar et de ses données) et l'utilisateur se réenrôle. Alternative rejetée : code de récupération par profil.

### D7d. Compte administrateur
Un seul compte admin, identifiants fournis par `ASSISTANT_ADMIN_USERNAME` et `ASSISTANT_ADMIN_PASSWORD` (obligatoire, pas de valeur par défaut ; haché BCrypt au démarrage, jamais journalisé). Session admin distincte de la session faciale (cookie propre, durée courte), pages sous `/admin`, protégées par Spring Security avec verrouillage temporaire après échecs répétés. L'admin liste et purge les profils mais ne peut pas lire leurs données d'application.

### D11. Charte graphique
Une feuille de style unique avec variables CSS (`--bg: #000000`, `--fg: #ffffff`, nuances de gris pour le secondaire) : fond noir pur pour l'OLED, texte blanc, pas de dégradé ni d'ombre claire. Effet « écriture en direct » par un module JS `typewriter` appliqué à tout texte qui apparaît (messages d'état, contenu des widgets) ; texte complet exposé aux lecteurs d'écran, effet désactivé si `prefers-reduced-motion`. Vitesse et activation configurables (`ASSISTANT_UI_TYPING_SPEED_MS`, `ASSISTANT_UI_TYPING_ENABLED`). Durée totale plafonnée pour les longs textes.

### D7c. Configuration par variables d'environnement
`application.properties` utilise des placeholders explicites `assistant.x=${ASSISTANT_X:défaut}` regroupés dans une classe `@ConfigurationProperties` validée. Variables : `ASSISTANT_SESSION_DURATION_MINUTES` (10), `ASSISTANT_SCAN_INTERVAL_DISCONNECTED_MS` (1000), `ASSISTANT_SCAN_INTERVAL_CONNECTED_MS` (5000), `ASSISTANT_LOGOUT_PAUSE_SECONDS` (60), `ASSISTANT_MATCH_THRESHOLD` (0.55), `ASSISTANT_SWITCH_CONFIRMATIONS` (2), `ASSISTANT_HEARTBEAT_MIN_INTERVAL_SECONDS` (30), `ASSISTANT_MAX_PROFILES` (0 = illimité), `ASSISTANT_ENROLLMENT_MIN_PHOTOS` (5), `ASSISTANT_ENROLLMENT_MAX_PHOTOS` (8), `ASSISTANT_WEATHER_CACHE_MINUTES` (30), `ASSISTANT_WEATHER_FORECAST_DAYS` (5), `ASSISTANT_UI_TYPING_ENABLED` (true), `ASSISTANT_UI_TYPING_SPEED_MS` (25), `ASSISTANT_ADMIN_USERNAME` (admin), `ASSISTANT_ADMIN_PASSWORD` (obligatoire), plus `SPRING_PROFILES_ACTIVE`, `SPRING_DATASOURCE_URL|USERNAME|PASSWORD`, `JAVA_TOOL_OPTIONS`.

### D8. HTTPS
Caddy dans le compose, `tls internal`, reverse proxy vers Spring Boot en HTTP interne. Le certificat racine est à installer une fois par appareil ; téléchargeable depuis l'application (le volume de données Caddy est monté en lecture seule dans l'application, qui n'expose que `root.crt`), guide dans le README. Requis car `getUserMedia` exige un contexte sécurisé.

Le `docker-compose.yml` est conçu pour Portainer : pas de `build` (image via `APP_IMAGE`), Caddyfile fourni par `configs` avec `content` inline (aucun fichier à monter), volumes nommés, variables avec `${VAR:-défaut}`.

### D9. Météo
Open-Meteo (sans clé), appels côté backend, cache borné (TTL 30 min) partagé par localisation.

### D10. Ressources Pi
Serial GC, pagination JPA pour l'historique, caches à taille bornée, aucun traitement d'image côté serveur.

### D12. Qualité de reconnaissance ajustable
Le client reçoit un objet de configuration (`GET /api/config`) construit depuis les variables : `ASSISTANT_DETECTOR_INPUT_SIZE` (224 ; multiples de 32 de 128 à 416), `ASSISTANT_DETECTOR_MIN_CONFIDENCE` (0.5), `ASSISTANT_MATCH_THRESHOLD` (0.55), `ASSISTANT_MATCH_MARGIN` (0.05, 0 = désactivé), `ASSISTANT_MIN_FACE_SIZE_PX` (80), `ASSISTANT_CAMERA_WIDTH` (640), `ASSISTANT_RECOGNITION_BACKEND` (auto : WebGL puis repli WASM), `ASSISTANT_RECOGNITION_DEBUG` (false). Validation au démarrage. Cibles de test : téléphones de 2025 (WebGL, réglages par défaut) et Raspberry Pi utilisé comme poste avec navigateur (GPU faible : recommander `INPUT_SIZE=160`, `BACKEND=wasm`, `SCAN_INTERVAL` plus long). Le Pi hébergeant le serveur ne fait aucun calcul de reconnaissance.

### D13. Sauvegarde et restauration JSON
Export générique par la métamodel JPA (toutes les entités sauf les sessions), donc les futures applications sont incluses automatiquement. Format : `{ "version": 1, "exportedAt": ..., "tables": { "<table>": [ {ligne}, ... ] } }`, indépendant du SGBD (SQLite et PostgreSQL interchangeables). Écriture en flux (Jackson streaming) table par table avec pagination pour ne jamais tout charger en mémoire. Restauration : upload du fichier (taille plafonnée par `ASSISTANT_BACKUP_MAX_UPLOAD_MB`, 50), validation de version et de structure avant toute écriture, puis dans une seule transaction vidage complet et réinsertion dans l'ordre des clés étrangères, remise à niveau des séquences PostgreSQL, invalidation de toutes les sessions. Le fichier contient des données biométriques et personnelles : téléchargement réservé à l'admin, jamais journalisé.

### D14. Météo par ville, poids, langue
Météo : géocodage Open-Meteo (nom de ville vers coordonnées), résultat enregistré dans `config` du profil, cache des géocodages. Poids : entier en dixièmes de kg pour éviter les erreurs de flottants et rester portable SQLite/PostgreSQL. Langue : français uniquement, `lang="fr"`, textes dans les gabarits Thymeleaf sans internationalisation.

### D15. Image et CI
La CI existante (non modifiée) exécute `./mvnw -B clean test` avec JaCoCo (rapport `target/site/jacoco/`) puis publie `linux/arm64` sur Docker Hub : `<DOCKERHUB_USERNAME>/assistant:latest` (main), `:<branche>` (autres branches), `:<tag>` (tags). Le projet doit donc fournir `mvnw`, le plugin JaCoCo et un `Dockerfile` à la racine acceptant l'argument `GIT_COMMIT`. Les tests ne doivent nécessiter ni PostgreSQL ni réseau externe.

### D16. Source et chargement des assets faciaux
`ASSISTANT_FACE_ASSETS_MODE` vaut `cdn` en profil local et `local` dans l'image Docker. Les URL des scripts, modèles et WASM sont surchargeables. Une page accessible affiche la progression par étapes ; elle précharge avec `fetch(cache: force-cache)` et le cache HTTP du navigateur est partagé avec le chargement effectif de face-api. Les versions dans les URL sont immuables pour favoriser un cache fiable. Docker assemble les assets dans un stage Node indépendant de l'architecture cible puis les copie dans le JAR, ce qui évite l'accès réseau depuis les navigateurs de production.

## Risks / Trade-offs

- [Identité forgeable par un client] -> Réseau local uniquement, non exposé ; documenté.
- [Embeddings variables selon caméra/éclairage] -> 5 à 8 captures variées, comparaison au plus proche, seuil configurable, ré-enrôlement.
- [Faux positif déclenchant une bascule] -> N confirmations consécutives.
- [Certificat à installer sur chaque appareil, iOS manuel] -> Guide README, endpoint de téléchargement.
- [Charge du scan sur mobile] -> Détecteur léger, scan lent connecté, pause.
- [Création de profil ouverte sur le LAN] -> Suppression/ré-enrôlement réservés au profil connecté ; purge réservée à l'administrateur ; limite optionnelle `ASSISTANT_MAX_PROFILES`.
- [Avatar = image stockée] -> Une seule image réduite par profil, choisie par l'utilisateur.
- [Faux positifs/négatifs de reconnaissance] -> Seuil, marge, taille de détecteur ajustables et mode debug de calibration.
- [Restauration destructive ou fichier corrompu] -> Validation avant écriture, transaction unique (rollback complet), confirmation explicite.
- [Dump contenant des données biométriques] -> Accès admin uniquement, rappel dans l'interface de stocker le fichier en lieu sûr.
- [Image arm64 seule] -> Le développement local passe par Maven, sans Docker.
- [Compte admin compromis] -> Mot de passe fort obligatoire en variable, verrouillage après échecs, l'admin n'accède à aucune donnée d'application.
- [Purge destructive] -> Confirmation explicite, suppression totale du profil et de ses données.
- [Effet machine à écrire sur de longs textes] -> Durée plafonnée, désactivable, respect de `prefers-reduced-motion`.

## Open Questions

- Seuil de distance par défaut à calibrer sur des visages réels.
- Localisation de la météo : par profil ou globale (supposé : par profil dans `config`).
