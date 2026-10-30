## ADDED Requirements

### Requirement: Déploiement HTTP sur le LAN
Le déploiement SHALL servir l'application en HTTP sur le réseau local, sans reverse proxy TLS, sans certificat et sans nom de domaine. Le service `app` MUST publier son port (`${APP_PORT:-8080}:8080`). Le Pi MUST ouvrir l'application via `http://localhost:8080/?kiosk=<clé>` (contexte sécurisé pour la caméra) et les smartphones via `ASSISTANT_PUBLIC_BASE_URL` (adresse IP du Pi). `SERVER_SERVLET_SESSION_COOKIE_SECURE` MUST valoir `false` par défaut afin que la connexion d'administration fonctionne en HTTP. `server.forward-headers-strategy` MUST valoir `none` en l'absence de proxy.

#### Scenario: Accès du Pi
- **WHEN** le navigateur du Pi ouvre `http://localhost:8080/?kiosk=<clé>`
- **THEN** la page se charge dans un contexte sécurisé et la caméra est autorisée

#### Scenario: Accès du smartphone
- **WHEN** un smartphone du LAN ouvre `http://<ip-du-pi>:8080/mobile`
- **THEN** la page se charge sans certificat à installer

#### Scenario: Connexion d'administration en HTTP
- **WHEN** l'administrateur se connecte depuis un appareil du LAN en HTTP
- **THEN** son cookie de session est accepté par le navigateur

#### Scenario: Aucune référence à Caddy
- **WHEN** on inspecte le compose, le README et la documentation
- **THEN** aucune référence à Caddy, à `ASSISTANT_DOMAIN` ou à `/root.crt` ne subsiste

## MODIFIED Requirements

### Requirement: Stack compatible Portainer
Le dépôt SHALL fournir un `docker-compose.yml` déployable tel quel comme stack Portainer (sans `build`, image référencée par variable, volumes nommés, variables avec valeurs par défaut).

#### Scenario: Déploiement Portainer
- **WHEN** la stack est créée dans Portainer avec ses variables d'environnement
- **THEN** l'application et PostgreSQL démarrent sans fichier externe

### Requirement: Réglages par variables d'environnement
Tous les réglages généraux (session, intervalles de scan, seuil, pause, confirmations, limite de profils, nombre de photos d'enrôlement, capture à distance, transfert de session, clé de kiosque, URL publique des QR, mode développement des QR, météo, base de données) SHALL être modifiables par variable d'environnement avec une valeur par défaut (sauf la clé de kiosque, obligatoire en production), et le README SHALL les lister avec exemples.

#### Scenario: Surcharge
- **WHEN** `ASSISTANT_SESSION_DURATION_MINUTES=20` est défini
- **THEN** la session glissante dure 20 minutes

#### Scenario: Documentation
- **WHEN** on ouvre le README
- **THEN** chaque variable est décrite (nom, défaut, rôle) avec un exemple de `.env` et de stack Portainer

#### Scenario: Ancienne variable supprimée
- **WHEN** `ASSISTANT_ENROLLMENT_MIN_PHOTOS` ou `ASSISTANT_ENROLLMENT_MAX_PHOTOS` est défini
- **THEN** elle est ignorée et le README indique de la remplacer par `ASSISTANT_ENROLLMENT_PHOTOS`

### Requirement: Ressources limitées
La configuration SHALL tenir dans les 4 Go du Raspberry Pi 4 (Serial GC, mémoire JVM bornée) et ne pas modifier la CI GitHub existante.

#### Scenario: Démarrage Docker Compose
- **WHEN** le compose est lancé sur le Pi
- **THEN** l'application et PostgreSQL démarrent avec des limites mémoire définies

## REMOVED Requirements

### Requirement: HTTPS via Caddy
**Reason**: Le déploiement est exclusivement sur le LAN en HTTP ; le smartphone n'utilise plus la caméra et le Pi accède à l'application en `localhost`, qui est un contexte sécurisé.
**Migration**: Retirer le service Caddy, ses volumes et sa configuration du compose ; publier le port de l'application ; renseigner `ASSISTANT_PUBLIC_BASE_URL` avec l'adresse IP du Pi.

### Requirement: Distribution du certificat racine
**Reason**: Plus de certificat à gérer sans HTTPS.
**Migration**: Supprimer la route `/root.crt` et la documentation d'installation du certificat (iOS compris) du README.
