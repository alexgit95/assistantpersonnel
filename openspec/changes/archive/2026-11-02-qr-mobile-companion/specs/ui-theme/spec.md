## MODIFIED Requirements

### Requirement: Entrées du menu selon la session
Le menu du profil MUST masquer effectivement « Configuration » et « Déconnexion » lorsqu'aucun profil n'est connecté. L'entrée « Administration » SHALL rester toujours visible. Le menu MUST NOT proposer « Créer un profil » ni « Refaire mes captures » (ces actions passent par le menu mobile). L'accès direct à `/configuration` sans session MUST rester refusé par le serveur.

#### Scenario: Utilisateur non connecté
- **WHEN** aucun profil n'est connecté et que le menu du profil est ouvert
- **THEN** « Configuration » et « Déconnexion » ne sont pas affichés et « Administration » l'est

#### Scenario: Profil connecté
- **WHEN** un profil est connecté et ouvre le menu
- **THEN** « Configuration » et « Déconnexion » sont affichés

#### Scenario: Actions d'enrôlement
- **WHEN** le menu du profil est ouvert, connecté ou non
- **THEN** il ne contient ni « Créer un profil » ni « Refaire mes captures »

#### Scenario: Accès direct sans session
- **WHEN** un utilisateur sans session ouvre `/configuration`
- **THEN** le serveur répond 401
