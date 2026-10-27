## ADDED Requirements

### Requirement: HTTPS via Caddy
Le déploiement SHALL fournir un conteneur Caddy en `tls internal` servant l'application en HTTPS sur le LAN, l'application restant en HTTP interne.

#### Scenario: Accès depuis un appareil
- **WHEN** un appareil du LAN ouvre `https://assistant.lan` avec le certificat racine installé
- **THEN** la page se charge et l'accès à la caméra est autorisé

### Requirement: Distribution du certificat racine
Le système SHALL permettre de télécharger le certificat racine et le README SHALL décrire son installation (dont iOS).

#### Scenario: Téléchargement
- **WHEN** l'utilisateur ouvre la page d'aide au certificat
- **THEN** le certificat racine est téléchargeable

### Requirement: Stack compatible Portainer
Le dépôt SHALL fournir un `docker-compose.yml` déployable tel quel comme stack Portainer (sans `build`, image référencée par variable, configuration Caddy inline, volumes nommés, variables avec valeurs par défaut).

#### Scenario: Déploiement Portainer
- **WHEN** la stack est créée dans Portainer avec ses variables d'environnement
- **THEN** Caddy, l'application et PostgreSQL démarrent sans fichier externe

### Requirement: Réglages par variables d'environnement
Tous les réglages généraux (session, intervalles de scan, seuil, pause, confirmations, limite de profils, nombre de captures, météo, base de données) SHALL être modifiables par variable d'environnement avec une valeur par défaut, et le README SHALL les lister avec exemples.

#### Scenario: Surcharge
- **WHEN** `ASSISTANT_SESSION_DURATION_MINUTES=20` est défini
- **THEN** la session glissante dure 20 minutes

#### Scenario: Documentation
- **WHEN** on ouvre le README
- **THEN** chaque variable est décrite (nom, défaut, rôle) avec un exemple de `.env` et de stack Portainer

### Requirement: Ressources limitées
La configuration SHALL tenir dans les 4 Go du Raspberry Pi 4 (Serial GC, mémoire JVM bornée) et ne pas modifier la CI GitHub existante.

#### Scenario: Démarrage Docker Compose
- **WHEN** le compose est lancé sur le Pi
- **THEN** Caddy, l'application et PostgreSQL démarrent avec des limites mémoire définies
