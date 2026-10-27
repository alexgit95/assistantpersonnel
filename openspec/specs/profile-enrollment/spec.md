# Purpose

Define profile enrollment, avatar selection, recapture, duplicate rejection, and profile ownership rules.

## Requirements

### Requirement: Création de profil par capture
Le système SHALL permettre de créer un profil en saisissant un nom puis en capturant entre 5 et 8 photos (bornes configurables). Les embeddings de toutes les captures sont conservés ; parmi les images, seule celle choisie comme avatar est conservée.

#### Scenario: Création réussie
- **WHEN** l'utilisateur saisit un nom, valide les captures et choisit son avatar
- **THEN** le profil, ses embeddings et l'avatar choisi sont enregistrés ; les autres images sont jetées

#### Scenario: Captures guidées
- **WHEN** une capture est demandée
- **THEN** une consigne (angle, éclairage) est affichée et la capture est refusée si aucun visage n'est détecté

### Requirement: Choix de l'image de profil
Après les captures, le système SHALL afficher les photos prises et laisser l'utilisateur choisir celle qui sert d'avatar. L'avatar SHALL être réduit (128x128 maximum) et stocké en texte (data URL base64) compatible SQLite et PostgreSQL.

#### Scenario: Choix de l'avatar
- **WHEN** l'utilisateur sélectionne une des photos capturées
- **THEN** elle devient l'avatar affiché dans la liste des profils et sur l'écran d'accueil

#### Scenario: Changement ultérieur
- **WHEN** un profil connecté refait des captures
- **THEN** il peut à nouveau choisir son avatar

### Requirement: Nombre de profils
Le système SHALL ne pas limiter le nombre de profils par défaut ; une limite optionnelle SHALL être définissable par la variable `ASSISTANT_MAX_PROFILES` (0 = illimité).

#### Scenario: Illimité
- **WHEN** `ASSISTANT_MAX_PROFILES` vaut 0
- **THEN** aucune limite n'est appliquée à la création

#### Scenario: Limite définie
- **WHEN** `ASSISTANT_MAX_PROFILES` vaut 3 et 3 profils existent
- **THEN** la création est refusée avec une erreur explicite

### Requirement: Relance des captures à tout moment
Un utilisateur connecté SHALL pouvoir relancer les captures de son profil à tout moment. Les nouveaux embeddings peuvent s'ajouter aux existants ou les remplacer, au choix de l'utilisateur.

#### Scenario: Depuis une session ouverte
- **WHEN** un profil connecté lance « Refaire mes captures »
- **THEN** le parcours de capture démarre et met à jour ses embeddings

#### Scenario: Utilisateur jamais ou mal reconnu
- **WHEN** un utilisateur n'est pas reconnu et n'a donc pas de session
- **THEN** il ne peut pas modifier de profil existant ; il crée un nouveau profil, ou demande à l'administrateur de purger l'ancien

### Requirement: Rejet des doublons
Le système SHALL refuser un enrôlement dont le visage est reconnu comme un profil existant.

#### Scenario: Visage déjà enregistré
- **WHEN** l'embedding capturé est sous le seuil d'un profil existant
- **THEN** l'enrôlement est refusé

### Requirement: Gestion réservée au propriétaire
La modification des embeddings, de l'avatar et la suppression d'un profil SHALL exiger une session ouverte sur ce même profil, ou l'administrateur pour la purge ; un utilisateur MUST NOT accéder aux données ou au profil d'un autre. La création de nouveaux profils reste libre.

#### Scenario: Suppression d'un autre profil
- **WHEN** le profil P demande la suppression du profil Q
- **THEN** la requête est refusée (403)

#### Scenario: Ré-enrôlement depuis un autre appareil
- **WHEN** P connecté capture de nouveaux visages
- **THEN** les nouveaux embeddings sont ajoutés à ceux de P ou les remplacent selon son choix

### Requirement: Exposition des embeddings
Le système SHALL exposer aux appareils du LAN la liste des identifiants, noms, avatars et embeddings des profils pour permettre la comparaison locale, sans aucune donnée d'application.

#### Scenario: Liste avant authentification
- **WHEN** un appareil non connecté appelle `GET /api/faces`
- **THEN** il reçoit id, nom, avatar et embeddings uniquement