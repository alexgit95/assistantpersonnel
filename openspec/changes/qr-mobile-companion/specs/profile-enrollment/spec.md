## MODIFIED Requirements

### Requirement: Création de profil par capture
Le système SHALL permettre de créer un profil depuis un smartphone : le smartphone saisit le nom et lance la capture, le Pi prend `assistant.enrollment.photos` captures (défaut 8) avec sa propre caméra, puis le smartphone choisit l'avatar et valide. Les embeddings de toutes les captures sont conservés ; parmi les images, seule celle choisie comme avatar est conservée. Il n'existe plus de parcours d'enrôlement au clavier sur le Pi (la page `/enroll` est supprimée).

#### Scenario: Création réussie
- **WHEN** l'utilisateur saisit un nom sur le smartphone, la capture se déroule sur le Pi puis il choisit son avatar et valide
- **THEN** le profil, ses embeddings et l'avatar choisi sont enregistrés ; les autres images sont jetées

#### Scenario: Captures guidées
- **WHEN** une capture est demandée
- **THEN** une consigne de pose textuelle est affichée sur le Pi et la capture est refusée si aucun visage n'est détecté

#### Scenario: Page d'enrôlement supprimée
- **WHEN** un client ouvre `/enroll`
- **THEN** la page n'existe plus (404)

### Requirement: Choix de l'image de profil
Après les captures, le système SHALL afficher sur le smartphone les photos prises et laisser l'utilisateur choisir celle qui sert d'avatar. L'avatar SHALL être réduit (128x128 maximum) et stocké en texte (data URL base64) compatible SQLite et PostgreSQL.

#### Scenario: Choix de l'avatar
- **WHEN** l'utilisateur sélectionne une des photos capturées
- **THEN** elle devient l'avatar affiché dans la liste des profils et sur l'écran d'accueil

#### Scenario: Changement ultérieur
- **WHEN** un profil connecté refait des captures depuis son smartphone
- **THEN** il peut à nouveau choisir son avatar

### Requirement: Relance des captures à tout moment
Un utilisateur connecté SHALL pouvoir relancer les captures de son profil à tout moment depuis le menu mobile (« Refaire mes captures »). Les nouveaux embeddings peuvent s'ajouter aux existants ou les remplacer, au choix de l'utilisateur.

#### Scenario: Depuis une session ouverte
- **WHEN** un profil connecté lance « Refaire mes captures » sur son smartphone
- **THEN** la capture se déroule sur le Pi et met à jour ses embeddings

#### Scenario: Utilisateur jamais ou mal reconnu
- **WHEN** un utilisateur n'est pas reconnu et n'a donc pas de session
- **THEN** il ne peut pas modifier de profil existant ; il crée un nouveau profil, ou demande à l'administrateur de purger l'ancien
