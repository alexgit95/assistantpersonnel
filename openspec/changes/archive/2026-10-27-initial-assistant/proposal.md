## Why

Les 3 membres du foyer veulent un assistant personnel web, affiché sur n'importe quel appareil équipé d'une caméra, qui les reconnaît automatiquement et présente leurs applications personnelles (calendrier, poids, météo, etc.). Aucune base n'existe : cette spec initiale fixe le socle (identité, session, framework d'applications) avant tout développement.

## What Changes

- Nouvelle application Spring Boot 4 (Java 25) avec interface Thymeleaf servie par le backend, derrière un reverse proxy Caddy en HTTPS interne (LAN uniquement).
- Reconnaissance faciale exécutée **dans le navigateur** (détection + embeddings) ; le backend ne fait que stocker les embeddings et gérer la session.
- Parcours de création de profil par capture de plusieurs photos : embeddings conservés, avatar choisi par l'utilisateur parmi les captures (seule image stockée), relance des captures possible à tout moment depuis une session, nombre de profils non limité par défaut.
- Compte administrateur (login/mot de passe via variables d'environnement) pouvant purger un profil, dont l'utilisateur devra se réenrôler.
- Charte graphique : fond noir pur (OLED), texte blanc, texte qui apparaît comme écrit en direct, interface en français uniquement.
- Qualité de reconnaissance entièrement ajustable par variables d'environnement (cibles de test : téléphones de 2025, Raspberry Pi).
- Sauvegarde : dump JSON de toute la base et restauration depuis `/admin`.
- Image Docker publiée sur Docker Hub (`linux/arm64`) par la CI existante.
- Tous les réglages généraux modifiables par variables d'environnement, documentés dans le README.
- `docker-compose.yml` prêt pour une stack Portainer.
- En local, les assets faciaux sont chargés depuis un CDN versionné ; au build Docker, les mêmes assets sont téléchargés et embarqués dans l'image. Une barre de progression rend leur chargement visible et le cache HTTP navigateur évite les re-téléchargements inutiles.
- Session de 10 min glissante, scan vidéo ralenti une fois connecté, bascule automatique vers un autre profil reconnu, bouton de déconnexion avec pause de reconnaissance.
- Framework d'applications extensible : chaque profil active et ordonne ses applications ; ajout d'une application sans modifier le cœur.
- Accueil familial à géométrie fixe, sans défilement : horloge et aperçu webcam en haut à gauche, avatar et menu en haut à droite, un emplacement principal central et quatre emplacements latéraux pour les widgets.
- Configuration des widgets sur une page dédiée accessible depuis le menu de l'avatar ; l'utilisateur affecte les widgets aux emplacements réservés par glisser-déposer.
- Isolation stricte des données entre profils.
- Applications initiales : poids (saisie manuelle) et météo ; Google Calendar prévu en phase ultérieure (change dédié).

## Capabilities

### New Capabilities
- `face-auth`: reconnaissance faciale côté navigateur, session glissante, bascule, déconnexion, pause.
- `profile-enrollment`: création, ré-enrôlement et suppression de profils (3 maximum) par capture de visages.
- `admin-management`: compte administrateur, connexion et purge de profils.
- `ui-theme`: charte graphique OLED (noir/blanc), effet d'écriture en direct, interface en français.
- `data-backup`: export JSON complet de la base et restauration depuis la page d'administration.
- `app-framework`: registre d'applications, affectation des widgets aux emplacements fixes par profil, page de configuration dédiée, écran d'accueil sans défilement, isolation des données.
- `https-deployment`: exposition HTTPS sur le LAN via Caddy (CA interne), stack Portainer, configuration par variables d'environnement.
- `app-weight`: suivi du poids par saisie manuelle.
- `app-weather`: météo actuelle et prévisions des prochains jours.

### Modified Capabilities
<!-- Aucune : premier change du projet. -->

## Impact

- Nouveau code backend (packages auth, profile, apps), vues Thymeleaf, JavaScript statique (bibliothèque de reconnaissance et modèles).
- Base : SQLite (profil `local`) / PostgreSQL (prod) via `ddl-auto`, `docs/database.puml` à créer.
- `docker-compose` à ajouter avec Caddy ; la CI GitHub existante n'est pas modifiée.
- Contrainte ressources : Raspberry Pi 4 (4 Go), JVM sobre, calcul lourd côté client.
- Limite connue : le backend fait confiance au client pour l'identité (LAN pur, sans liveness).
