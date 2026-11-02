## Why

Le Raspberry Pi 4 affiche l'application sur un écran non tactile : on ne peut ni créer de profil, ni configurer les widgets, ni saisir son poids. Le smartphone, sur le même réseau local, devient le clavier et la télécommande du Pi, via des QR codes affichés à l'écran. Les captures d'enrôlement restent prises par la caméra du Pi pour éviter l'écart de reconnaissance entre appareils.

## What Changes

- **Un QR menu toujours affiché** sur le Pi : en invité il mène à « Créer mon profil » et à l'administration ; en connecté il mène au menu mobile complet (configuration, poids, refaire mes captures, administration, déconnexion).
- **Enrôlement à distance** : le smartphone saisit le nom et lance la capture ; le Pi déroule un compte à rebours de 5 s puis 8 prises, une toutes les 4 s, avec guidage de pose textuel ; le smartphone choisit l'avatar et valide. Recapture du profil sur le même principe.
- **Jeton de transfert à usage unique** : un QR (menu connecté ou poids) est échangé contre une nouvelle session transitoire du même profil, avec portée `FULL` ou `WEIGHT` ; le QR est régénéré dès qu'il est consommé.
- **Widget poids** : plus de retournement ; graphique + QR poids. Ajout, modification et suppression d'une mesure se font uniquement sur le mobile (`/mobile/weight`).
- **Pages mobiles** `/mobile`, `/mobile/enroll`, `/mobile/weight` ; `/configuration` et `/admin/**` adaptés au petit écran.
- **Clé de kiosque** (`ASSISTANT_KIOSK_KEY`) : les appels réservés au Pi (login, heartbeat, émission de QR, session de capture) exigent l'en-tête `X-Kiosk-Key`.
- **Déploiement HTTP sur le LAN** : Caddy, le nom de domaine et les certificats sont retirés ; QR par adresse IP du Pi (`ASSISTANT_PUBLIC_BASE_URL`) ; cookie admin non `Secure`.
- **Mode développement** `assistant.qr.dev-mode` : QR cliquable (nouvel onglet), URL affichée, bascule automatique `localhost` ⇄ `127.0.0.1`.
- **BREAKING** : suppression de la page `/enroll` et du mode « refaire mes captures » au clavier ; suppression de `ASSISTANT_ENROLLMENT_MIN_PHOTOS` / `ASSISTANT_ENROLLMENT_MAX_PHOTOS` au profit de `ASSISTANT_ENROLLMENT_PHOTOS` (défaut 8) ; retrait de Caddy, de `ASSISTANT_DOMAIN`, `HTTP_PORT`, `HTTPS_PORT` et de `/root.crt` ; `POST /api/auth/login` et `POST /api/auth/heartbeat` réservés au Pi ; port 8080 publié par le compose.
- Passage en version **1.0.0** (`pom.xml`, `CHANGELOG.md`).

## Capabilities

### New Capabilities
- `remote-enrollment`: session de capture à distance pilotée depuis le smartphone, captures sur la caméra du Pi, machine à états, guidage, synchronisation Pi/smartphone, recapture.
- `mobile-companion`: QR menu et QR poids, jeton de transfert à usage unique, sessions transitoires à portée `FULL`/`WEIGHT`, pages `/mobile/**`, mode développement des QR.
- `kiosk-access`: clé de kiosque, endpoints réservés au Pi, poll d'état `GET /api/kiosk/state`.

### Modified Capabilities
- `profile-enrollment`: création de profil via la capture à distance (nombre de photos unique, 8), recapture depuis le mobile, retrait de l'enrôlement au clavier.
- `face-auth`: connexion et heartbeat réservés au Pi par la clé de kiosque ; portée et origine des sessions.
- `guest-dashboard`: le bouton « Créer un profil » est remplacé par le QR menu invité.
- `app-weight`: widget sans retournement (graphique + QR) ; saisie, modification et suppression sur le mobile.
- `admin-management`: pages d'administration adaptées au mobile et accessibles par les QR.
- `https-deployment`: déploiement HTTP sur le LAN sans Caddy ni certificat (spec remplacée) ; port publié, cookie admin, variables.
- `ui-theme`: menu du profil sans « Refaire mes captures » ni « Créer un profil ».
- `widget-periodic-refresh`: suppression de l'exigence de pause liée à la face de saisie du widget poids.
- `app-framework`: la page de configuration propose une vue liste adaptée au mobile.

## Impact

- **Backend** (`com.assistant`) : nouveaux packages `kiosk` (filtre de clé), `enrollment` (session de capture), extension de `auth` (`HandoffService`, `SessionService.loginFromHandoff`, portée des sessions), `QrUrlResolver`, contrôleurs `/mobile/**` et `/handoff`, `AdminSecurityConfig`, `AssistantProperties`, `ClientConfigController`.
- **Base de données** : colonnes `scope` et `source` sur `app_session` (Hibernate `ddl-auto`, compatible SQLite et PostgreSQL) ; `docs/database.puml` mis à jour.
- **Frontend** : `home.js` (panneau QR, pilotage de capture, clé de kiosque), nouveau module de capture, widget poids, pages `/mobile/**`, `configuration.js`, `admin.*` ; suppression de `enroll.html`, `enrollment.js`, `enrollment.css` ; bibliothèque de QR embarquée sous `/vendor/`.
- **Déploiement** : `docker-compose.yml` (Caddy retiré, port publié, nouvelles variables), `application.properties`, `application-local.properties` ; la GitHub Action n'est pas modifiée.
- **Documentation et versions** : `README.md`, `CHANGELOG.md` (1.0.0 avec `[Added]`, `[Changed]`, `[Fixed]`, `[Removed]`), `pom.xml`.
- **Risques acceptés** : trafic HTTP lisible sur le LAN, QR menu connecté toujours visible, recapture « Remplacer » non durcie, pas de limitation de débit, `GET /api/faces` sans clé (voir `docs/parcours-qrcode-smartphone.md`, section 6).
- **Source de cadrage** : `docs/parcours-qrcode-smartphone.md`.
