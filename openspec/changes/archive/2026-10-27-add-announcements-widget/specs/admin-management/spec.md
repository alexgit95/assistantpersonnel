## ADDED Requirements

### Requirement: Gestion des annonces
L'administrateur SHALL pouvoir créer, modifier et supprimer manuellement des annonces depuis le panneau Administration. Une annonce comporte un titre, une date de début, une date de fin postérieure à la date de début et un indicateur de compte à rebours. Les dates sont saisies et affichées en heure de Paris. Aucune purge automatique des annonces expirées n'est effectuée.

#### Scenario: Création
- **WHEN** l'administrateur saisit un titre et des dates valides, puis valide
- **THEN** l'annonce est enregistrée et apparaît dans la liste

#### Scenario: Dates incohérentes
- **WHEN** la date de fin n'est pas postérieure à la date de début, ou le titre est vide
- **THEN** l'enregistrement est refusé avec un message explicite et rien n'est modifié

#### Scenario: Modification
- **WHEN** l'administrateur modifie une annonce existante
- **THEN** les changements sont visibles dans le widget des utilisateurs

#### Scenario: Suppression manuelle
- **WHEN** l'administrateur confirme la suppression d'une annonce
- **THEN** elle disparaît de la liste et du widget

#### Scenario: Annonce expirée conservée
- **WHEN** la date de fin d'une annonce est passée
- **THEN** l'annonce reste listée dans l'administration jusqu'à suppression manuelle

### Requirement: Statut des annonces
La liste d'administration SHALL afficher toutes les annonces avec leur statut : « à venir », « en cours » ou « expirée ».

#### Scenario: Statuts
- **WHEN** l'administrateur ouvre la liste des annonces
- **THEN** chaque annonce porte le statut correspondant à l'instant serveur courant

### Requirement: Accès réservé à l'administrateur pour les annonces
Les opérations de gestion des annonces MUST exiger une session administrateur.

#### Scenario: Sans session admin
- **WHEN** un utilisateur non administrateur appelle un endpoint de gestion des annonces
- **THEN** il est redirigé vers la connexion ou reçoit 401/403
