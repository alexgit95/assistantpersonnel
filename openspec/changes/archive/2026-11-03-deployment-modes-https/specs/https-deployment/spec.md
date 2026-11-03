## MODIFIED Requirements

### Requirement: Déploiement HTTP sur le LAN
Les modes `dev` et `onprem` SHALL servir l'application en HTTP uniquement, sans reverse proxy TLS, sans certificat et sans nom de domaine. Le Pi ou l'ordinateur du miroir MUST ouvrir l'application via `http://localhost:8080/?kiosk=<clé>` (contexte sécurisé pour la caméra) et les smartphones via `ASSISTANT_PUBLIC_BASE_URL` (adresse IP de la machine). `SERVER_SERVLET_SESSION_COOKIE_SECURE` MUST valoir `false` par défaut afin que la connexion d'administration fonctionne en HTTP. `server.forward-headers-strategy` MUST valoir `none` en l'absence de proxy.

#### Scenario: Accès du kiosque
- **WHEN** le navigateur du kiosque ouvre `http://localhost:8080/?kiosk=<clé>`
- **THEN** la page se charge dans un contexte sécurisé et la caméra est autorisée

#### Scenario: Accès du smartphone
- **WHEN** un smartphone du LAN ouvre `http://<ip-de-la-machine>:8080/mobile`
- **THEN** la page se charge sans certificat à installer

#### Scenario: Connexion d'administration en HTTP
- **WHEN** l'administrateur se connecte depuis un appareil du LAN en HTTP
- **THEN** son cookie de session est accepté par le navigateur

#### Scenario: Aucune référence à Caddy
- **WHEN** on inspecte le compose, le README et la documentation
- **THEN** aucune référence à Caddy, à `ASSISTANT_DOMAIN` ou à `/root.crt` ne subsiste

### Requirement: Stack compatible Portainer
Le dépôt SHALL fournir un `docker-compose.yml` déployable tel quel comme stack Portainer pour le mode `remote` (sans `build`, image référencée par variable, volumes nommés dont `certs`, ports HTTP et HTTPS publiés, variables avec valeurs par défaut).

#### Scenario: Déploiement Portainer
- **WHEN** la stack est créée dans Portainer avec ses variables d'environnement
- **THEN** l'application et PostgreSQL démarrent sans fichier externe, avec les ports `${APP_PORT:-8080}` et `${APP_HTTPS_PORT:-8443}` publiés

#### Scenario: Certificat conservé
- **WHEN** la stack est redéployée
- **THEN** le volume `certs` conserve le certificat généré au premier démarrage

### Requirement: Réglages par variables d'environnement
Tous les réglages généraux (mode de déploiement, ports HTTP et HTTPS, certificat HTTPS, session, intervalles de scan, seuil, pause, confirmations, limite de profils, nombre de photos d'enrôlement, capture à distance, transfert de session, clé de kiosque, URL publique des QR, mode développement des QR, météo, base de données) SHALL être modifiables par variable d'environnement avec une valeur par défaut (sauf la clé de kiosque, obligatoire en `onprem` et `remote`), et le README SHALL les lister avec exemples.

#### Scenario: Surcharge
- **WHEN** `ASSISTANT_SESSION_DURATION_MINUTES=20` est défini
- **THEN** la session glissante dure 20 minutes

#### Scenario: Documentation
- **WHEN** on ouvre le README
- **THEN** chaque variable est décrite (nom, défaut, rôle) avec un exemple de `.env` et de stack Portainer

#### Scenario: Ancienne variable supprimée
- **WHEN** `ASSISTANT_ENROLLMENT_MIN_PHOTOS` ou `ASSISTANT_ENROLLMENT_MAX_PHOTOS` est défini
- **THEN** elle est ignorée et le README indique de la remplacer par `ASSISTANT_ENROLLMENT_PHOTOS`

## ADDED Requirements

### Requirement: Déploiement Docker réservé au mode remote
Docker SHALL être utilisé uniquement pour le mode `remote` : l'image démarre par défaut en profil `remote`, expose les ports 8080 et 8443 et exécute le script d'entrypoint de génération du certificat. Les modes `dev` et `onprem` MUST fonctionner sans Docker (JAR et SQLite).

#### Scenario: Image par défaut
- **WHEN** le conteneur démarre sans `SPRING_PROFILES_ACTIVE`
- **THEN** le profil `remote` est actif et les ports 8080 et 8443 sont exposés

#### Scenario: Mode onprem sans Docker
- **WHEN** l'application est lancée par `java -jar` avec `SPRING_PROFILES_ACTIVE=onprem`
- **THEN** elle démarre avec SQLite sans dépendre de Docker ni de PostgreSQL
