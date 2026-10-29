## ADDED Requirements

### Requirement: Avatar hexagonal
L'icône du profil SHALL être un hexagone régulier à côté plat en haut, sur tous les écrans où l'avatar du profil est affiché : accueil, page de configuration, liste d'administration et choix de l'avatar à l'enrôlement. La silhouette de secours, affichée sans avatar, SHALL suivre la même forme. Les autres éléments d'interface MUST NOT changer de forme.

#### Scenario: Avatar sur l'accueil
- **WHEN** un profil connecté ouvre l'accueil
- **THEN** son avatar, qui sert de contrôle d'ouverture du menu, est un hexagone à côté plat en haut

#### Scenario: Cohérence entre écrans
- **WHEN** l'avatar d'un profil est affiché dans l'administration ou dans le choix d'avatar de l'enrôlement
- **THEN** il a la même forme hexagonale qu'à l'accueil

#### Scenario: Profil sans avatar
- **WHEN** aucun avatar n'est disponible
- **THEN** la silhouette de secours est contenue dans le même hexagone

### Requirement: Entrées du menu selon la session
Le menu du profil MUST masquer effectivement « Configuration », « Refaire mes captures » et « Déconnexion » lorsqu'aucun profil n'est connecté. L'entrée « Administration » SHALL rester toujours visible. L'accès direct à `/configuration` sans session MUST rester refusé par le serveur.

#### Scenario: Utilisateur non connecté
- **WHEN** aucun profil n'est connecté et que le menu du profil est ouvert
- **THEN** « Configuration », « Refaire mes captures » et « Déconnexion » ne sont pas affichés et « Administration » l'est

#### Scenario: Profil connecté
- **WHEN** un profil est connecté et ouvre le menu
- **THEN** « Configuration », « Refaire mes captures » et « Déconnexion » sont affichés

#### Scenario: Accès direct sans session
- **WHEN** un utilisateur sans session ouvre `/configuration`
- **THEN** le serveur répond 401
