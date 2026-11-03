## Context

Le Pi 4 (4 Go) affiche `http://localhost:8080` en plein écran, sans écran tactile. La reconnaissance faciale est entièrement côté navigateur (face-api) ; le serveur ne reçoit que `profileId` (`POST /api/auth/login`) et gère des sessions glissantes par cookie `ASSISTANT_SESSION` (jeton haché en base). Le déploiement actuel place Caddy en HTTPS devant l'application. Le dépôt n'utilise aucun outil de migration (Hibernate `ddl-auto`, SQLite en local, PostgreSQL en production) et la GitHub Action ne doit pas être modifiée.

Document de cadrage détaillé : `docs/parcours-qrcode-smartphone.md` (parcours, endpoints, configuration, vérifications). Ce design en retient les décisions structurantes.

## Goals / Non-Goals

**Goals:**
- Rendre tous les parcours utilisables sans écran tactile : enrôlement, configuration, poids, administration, depuis un smartphone du LAN.
- Capturer les visages avec la caméra du Pi, pour que l'enrôlement et la reconnaissance utilisent les mêmes conditions.
- Un seul changement livré en version majeure 1.0.0, découpé en blocs ordonnés et vérifiables.
- Rester dans le budget mémoire du Pi (états en mémoire plafonnés, aucune dépendance Java ajoutée, Caddy retiré).

**Non-Goals:**
- Appairage durable d'un smartphone, HTTPS, certificats, noms de domaine.
- Enrôlement d'un tiers depuis un profil connecté, détection d'orientation du visage.
- Durcissement complet de la sécurité (limitation de débit, recapture « Remplacer » protégée, `GET /api/faces` protégé) : risques acceptés et listés dans le cadrage.
- Modification de la reconnaissance faciale elle-même.

## Decisions

### D1. Captures prises par la caméra du Pi, pilotées par le smartphone
Le smartphone saisit le nom, lance, choisit l'avatar et valide ; le Pi capture avec le flux déjà ouvert par `home.js`, `assessEnrollmentCapture` et `createRecognizer`. Le Pi reste sur la page d'accueil et suspend la boucle de reconnaissance pendant la capture.
- *Alternative rejetée* : captures sur le smartphone (écart de caméra, `getUserMedia` exige HTTPS, modèles à charger sur mobile).

### D2. Session de capture en mémoire, polling court
Une seule `EnrollmentSession` active (un seul Pi), dans une `ConcurrentHashMap`, purgée par tâche planifiée. États : `WAITING → COUNTDOWN → CAPTURING → REVIEW → DONE` (+ `CANCELLED`, `EXPIRED`). Le Pi et le smartphone interrogent le serveur (3 s au repos pour le Pi, 1 s en capture) ; le serveur fixe `countdownStartsAtMs` et `nextShotAtMs` et renvoie `serverNowMs` pour synchroniser les comptes à rebours.
- *Alternatives rejetées* : SSE/WebSocket (flux longs, 20 threads Tomcat, coupures Wi-Fi) ; table SQL (écritures sur carte SD pour des données de quelques minutes).

### D3. Secrets : clé de kiosque, `pairingToken`, `claimToken`
Le Pi est identifié par `X-Kiosk-Key` (`ASSISTANT_KIOSK_KEY`, obligatoire en production, comparaison en temps constant, désactivée si vide en profil `local`). Le smartphone est lié à la session par le `pairingToken` du QR puis par le `claimToken` renvoyé à `/start` (premier téléphone gagnant). Tous les jetons sont stockés hachés (SHA-256, comme `SessionService.hash`). `POST /api/auth/login` et `POST /api/auth/heartbeat` rejoignent les endpoints réservés au Pi, ce qui ferme le contournement par simple `profileId` et empêche un smartphone de prolonger sa propre session.
- *Alternative rejetée* : `deviceToken` par session de capture (inutile une fois le Pi identifié par la clé).

### D4. Jeton de transfert à usage unique, en mémoire
`HandoffService` émet un jeton aléatoire (256 bits) par QR, haché, avec `profileId`, `scope`, `qrId`, TTL 120 s, plafonné en nombre. `GET /handoff?t=` le consomme atomiquement, crée une session via `SessionService.loginFromHandoff` (sans fermer la session du Pi) et redirige vers `/mobile` (`FULL`) ou `/mobile/weight` (`WEIGHT`). Le Pi apprend la consommation par `GET /api/kiosk/state` (états `ACTIVE`/`CONSUMED`/`EXPIRED` par `qrId`) et régénère immédiatement le QR ; un nouveau jeton est aussi demandé 15 s avant le TTL (délai de grâce, deux jetons actifs au plus).
- *Alternative rejetée* : jeton réutilisable pendant son TTL (plus simple, mais un QR toujours affiché resterait utilisable plusieurs fois).

### D5. Sessions du smartphone : portée, durée fixe, sans prolongation
`app_session` reçoit `scope` (`FULL` par défaut) et `source` (`FACE`/`HANDOFF`). Une session `WEIGHT` n'est valide que sur une liste blanche (`/api/weight/**`, `/mobile/weight` et ses ressources, `/api/auth/session`, `/api/auth/logout`) ; tout le reste répond 403. Durées fixes : `FULL` 30 min, `WEIGHT` 10 min, non prolongeables (le heartbeat est réservé au Pi). La session du smartphone survit à la déconnexion du Pi jusqu'à son expiration.

### D6. Un seul QR menu, deux contenus ; QR poids dans le widget
Invité : `<base>/mobile?s=<pairingToken>` (menu : « Créer mon profil », « Administration »). Connecté : `<base>/handoff?t=<jeton FULL>` (menu : configuration, poids, refaire mes captures, administration, déconnexion). Le QR poids n'existe que dans le widget poids (jeton `WEIGHT`). Toutes les URL de QR sont construites par le serveur (`QrUrlResolver`) et renvoyées prêtes à l'emploi.

### D7. Widget poids sans retournement ; poids sur le mobile
Le widget n'affiche plus que le graphique et le QR poids. `/mobile/weight` porte la liste paginée, l'ajout (pré-rempli au dernier relevé), la modification et la suppression, en réutilisant `/api/weight` existant.

### D8. Suppression de l'ancien enrôlement, nombre de photos unique
`enroll.html`, `enrollment.js`, `enrollment.css`, la route `/enroll` et les entrées de menu « Créer un profil » / « Refaire mes captures » sont supprimés. `enrollment-policy.js` est conservé (réutilisé par la capture à distance) et simplifié vers « exactement N ». `assistant.enrollment.min-photos` et `max-photos` sont remplacés par `assistant.enrollment.photos` (défaut 8, `@Min(1) @Max(30)`).

### D9. Déploiement HTTP sur le LAN, IP du Pi
Caddy, `ASSISTANT_DOMAIN`, `HTTP_PORT`, `HTTPS_PORT`, les volumes Caddy et `/root.crt` sont retirés ; `app` publie `${APP_PORT:-8080}:8080`. Le Pi ouvre `http://localhost:8080/?kiosk=<clé>` (contexte sécurisé, caméra possible) ; le smartphone utilise `http://<ip-du-pi>:8080` (`ASSISTANT_PUBLIC_BASE_URL`, réservation DHCP du Pi). `SERVER_SERVLET_SESSION_COOKIE_SECURE` passe à `false` par défaut (un cookie `Secure` casse la connexion `/admin` en HTTP) ; `forward-headers-strategy` passe à `none`.
- *Alternative rejetée* : Caddy en HTTP seul pour un nom sans port (ajoute un conteneur et un nom à gérer sans bénéfice pour un QR par IP).

### D10. Mode développement des QR
`assistant.qr.dev-mode` (`ASSISTANT_QR_DEV_MODE`, faux par défaut, vrai en profil `local`) active : QR cliquable (`<a target="_blank" rel="noopener">`), URL affichée sous le QR, et si `public-base-url` est vide, URL dérivée de la requête en inversant `localhost` ⇄ `127.0.0.1` (hôtes à cookies séparés ; tout autre hôte n'est pas reflété). `public-base-url` renseignée est toujours prioritaire.

### D11. Un seul changement, découpé en blocs
Version 1.0.0 (ruptures de configuration et de déploiement). Les blocs de `tasks.md` sont ordonnés et chacun se termine par un point de contrôle (tests, démarrage) et un commit.
- *Alternative rejetée* : plusieurs changements OpenSpec (ordre et dépendances à suivre à la main, aucune dépendance native entre changements).

## Risks / Trade-offs

- **Trafic HTTP lisible sur le LAN** (jetons, cookies, mot de passe admin) → LAN protégé (WPA2/WPA3), TTL courts, jetons à usage unique, session `WEIGHT` restreinte, `/admin` depuis un poste de confiance ; documenté dans le README.
- **QR menu connecté toujours visible** : toute personne présente peut le scanner → usage unique + régénération immédiate ; durcissement possible plus tard (présence faciale récente à la consommation).
- **Recapture « Remplacer »** : un détenteur de session `FULL` pourrait écraser les empreintes avec un autre visage → accepté pour l'instant ; piste : n'autoriser que « Ajouter » ou comparer aux empreintes existantes.
- **Cadence de 4 s sur Pi 4** : le calcul d'empreinte ne doit pas décaler les prises → mesure à faire ; `interval-seconds` configurable ; le prochain tir est fixé par le serveur.
- **`SameSite=Strict` et redirection après scan** (iOS Safari, Android Chrome) → test sur appareils ; repli : page HTML intermédiaire avec `meta refresh`.
- **Plus d'enrôlement sans smartphone** → assumé ; en développement le QR cliquable ouvre l'URL dans un navigateur de bureau.
- **Changement cassant de configuration** (`MIN/MAX_PHOTOS`, variables Caddy, port publié, cookie admin) → README et `CHANGELOG.md` (`[Removed]`/`[Changed]`), version majeure.
- **Colonnes ajoutées à `app_session` sur base existante** → valeur par défaut `FULL`/`FACE` ; test de démarrage sur SQLite et PostgreSQL déjà peuplés.
- **Fuite mémoire du kiosque 24/7** (timers, SVG, miniatures) → libération à chaque changement d'état, plafonds de jetons et d'états côté serveur.
- **Rejet des captures trop fréquent à 1-2 m** → test utilisateur concluant à 1 m ; `ASSISTANT_MIN_FACE_SIZE_PX` reste réglable.

## Migration Plan

1. Poser la version 1.0.0 (`pom.xml`, `CHANGELOG.md`) ; l'image versionnée est produite par le tag git `v1.0.0` sans modifier le pipeline.
2. Avant déploiement : renseigner `ASSISTANT_KIOSK_KEY`, `ASSISTANT_PUBLIC_BASE_URL` (IP du Pi, réservation DHCP), `ASSISTANT_ENROLLMENT_PHOTOS` si différent de 8 ; retirer `ASSISTANT_ENROLLMENT_MIN_PHOTOS`, `ASSISTANT_ENROLLMENT_MAX_PHOTOS`, `ASSISTANT_DOMAIN`, `HTTP_PORT`, `HTTPS_PORT` de la stack.
3. Mettre à jour la stack Arcane/Portainer avec le nouveau `docker-compose.yml` (Caddy retiré, port 8080 publié) ; le démarrage échoue en production si `ASSISTANT_KIOSK_KEY` est vide.
4. Adapter la commande de lancement du navigateur du Pi : `http://localhost:8080/?kiosk=<clé>` ; désactiver l'isolation Wi-Fi du routeur.
5. Hibernate `ddl-auto` crée les colonnes `scope` et `source` ; les sessions existantes prennent `FULL`/`FACE`.
6. **Retour arrière** : redéployer l'image 0.0.13 avec l'ancien compose (Caddy, domaine) ; les nouvelles colonnes de `app_session` sont ignorées par l'ancienne version ; aucune donnée de profil n'est modifiée par ce changement.

## Open Questions

- Emplacement exact du QR menu sur l'écran du Pi (en-tête, coin fixe, bas de page).
- Formulation et ordre des textes de guidance de pose.
- Protéger `GET /api/faces` par la clé de kiosque (candidat, non retenu pour l'instant).
- Durées par défaut : `full-session-minutes` 30, `weight-session-minutes` 10 (à ajuster à l'usage).
- Page de confirmation avant consommation du jeton de transfert (non retenue ; à revoir si des aperçus de liens consomment les jetons).
