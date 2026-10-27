# Purpose

Define browser-side face recognition, profile sessions, automatic switching, logout, and tunable recognition quality.

## Requirements

### Requirement: Reconnaissance faciale côté navigateur
Le système SHALL effectuer la détection et le calcul d'embeddings faciaux exclusivement dans le navigateur ; le backend MUST NOT traiter d'images.

#### Scenario: Aucun traitement d'image serveur
- **WHEN** un visage est reconnu
- **THEN** seul l'identifiant du profil est envoyé au backend, aucune image ni flux vidéo

### Requirement: Connexion par visage
Le système SHALL ouvrir une session pour le profil dont la distance d'embedding avec le visage détecté est inférieure au seuil configuré.

#### Scenario: Visage reconnu
- **WHEN** un visage correspond à un profil sous le seuil
- **THEN** une session est ouverte pour ce profil et ses applications sont affichées

#### Scenario: Visage inconnu
- **WHEN** aucun profil ne correspond sous le seuil
- **THEN** aucune session n'est ouverte et un état « non reconnu » est affiché

### Requirement: Session glissante de 10 minutes
La session SHALL expirer 10 minutes (configurable) après la dernière détection du profil courant.

#### Scenario: Prolongation
- **WHEN** le profil courant est revu pendant la session
- **THEN** l'expiration est repoussée de la durée configurée, avec au plus un heartbeat par 30 s

#### Scenario: Expiration
- **WHEN** le profil courant n'est plus revu pendant 10 minutes
- **THEN** la session est invalidée côté serveur et l'écran revient à la reconnaissance

### Requirement: Scan ralenti une fois connecté
Le système SHALL poursuivre la détection après connexion à une fréquence réduite (5 s par défaut) et MUST NOT l'arrêter.

#### Scenario: Fréquences
- **WHEN** aucune session n'est active
- **THEN** le scan s'exécute toutes les secondes (configurable)
- **WHEN** une session est active
- **THEN** le scan s'exécute toutes les 5 secondes (configurable)

### Requirement: Bascule automatique de profil
Le système SHALL basculer vers un autre profil lorsqu'il est reconnu sur N détections consécutives (2 par défaut) pendant une session.

#### Scenario: Autre personne confirmée
- **WHEN** le profil Q est reconnu 2 fois de suite alors que P est connecté
- **THEN** la session de P est fermée et une session de Q est ouverte

#### Scenario: Détection isolée
- **WHEN** Q n'est reconnu qu'une seule fois
- **THEN** la session de P est conservée et non prolongée

### Requirement: Déconnexion et pause
Le système SHALL fournir un bouton de déconnexion qui ferme la session et suspend la reconnaissance 60 s (configurable).

#### Scenario: Déconnexion
- **WHEN** l'utilisateur clique sur « Déconnexion »
- **THEN** la session est supprimée côté serveur et aucune reconnexion n'a lieu pendant 60 s

### Requirement: Visage prioritaire
Lorsque plusieurs visages sont présents, le système SHALL traiter uniquement le plus grand.

#### Scenario: Plusieurs visages
- **WHEN** deux visages sont dans le champ
- **THEN** seul le plus grand est comparé aux profils

### Requirement: Qualité de reconnaissance ajustable
Le système SHALL permettre de régler la qualité de reconnaissance par variables d'environnement : taille d'entrée du détecteur (`ASSISTANT_DETECTOR_INPUT_SIZE`), confiance minimale de détection (`ASSISTANT_DETECTOR_MIN_CONFIDENCE`), seuil de distance (`ASSISTANT_MATCH_THRESHOLD`), écart minimal avec le deuxième meilleur profil (`ASSISTANT_MATCH_MARGIN`), taille minimale du visage (`ASSISTANT_MIN_FACE_SIZE_PX`), largeur de la caméra (`ASSISTANT_CAMERA_WIDTH`) et moteur de calcul (`ASSISTANT_RECOGNITION_BACKEND` : auto, webgl, wasm, cpu).

#### Scenario: Appareil modeste
- **WHEN** `ASSISTANT_DETECTOR_INPUT_SIZE` vaut 160 et `ASSISTANT_RECOGNITION_BACKEND` vaut wasm
- **THEN** la détection utilise une entrée réduite et le moteur WASM, plus légers

#### Scenario: Profils ressemblants
- **WHEN** l'écart entre le meilleur et le deuxième profil est inférieur à `ASSISTANT_MATCH_MARGIN`
- **THEN** la reconnaissance est considérée comme ambiguë et aucune session n'est ouverte

#### Scenario: Valeur hors bornes
- **WHEN** une variable de réglage a une valeur invalide
- **THEN** l'application refuse de démarrer avec un message explicite

### Requirement: Mode de calibration
Le système SHALL proposer, lorsque `ASSISTANT_RECOGNITION_DEBUG` vaut true, l'affichage de la distance au profil le plus proche, de l'écart au deuxième et de la durée de traitement, afin d'ajuster le seuil.

#### Scenario: Mode debug
- **WHEN** le mode debug est activé
- **THEN** ces mesures s'affichent à chaque détection

### Requirement: Chargement avec progression et cache
Le système SHALL afficher une barre de progression accessible pendant le chargement de la bibliothèque de reconnaissance, des manifestes, des poids et des binaires WASM. Les URL versionnées SHALL bénéficier du cache HTTP du navigateur ; les assets locaux Docker SHALL être servis avec un cache navigateur à durée bornée.

#### Scenario: Premier chargement local
- **WHEN** un navigateur en profil `local` ne possède pas encore les assets en cache
- **THEN** la progression et le nom de la ressource chargée sont affichés, puis la reconnaissance démarre une fois tous les assets prêts

#### Scenario: Chargements suivants
- **WHEN** le navigateur possède des assets CDN versionnés dans son cache HTTP
- **THEN** il les réutilise sans les télécharger à nouveau tant qu'ils restent frais

#### Scenario: Docker sans accès CDN à l'exécution
- **WHEN** l'application est exécutée avec le profil Docker `prod`
- **THEN** bibliothèque, modèles et WASM proviennent de `/vendor/face-api` intégrés à l'image, sans requête vers un CDN à l'exécution

#### Scenario: Échec de téléchargement
- **WHEN** un asset est inaccessible ou renvoie une erreur
- **THEN** la progression s'arrête sur un message explicite indiquant la source CDN ou locale et la reconnaissance ne démarre pas