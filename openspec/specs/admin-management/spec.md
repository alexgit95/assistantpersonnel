# Purpose

Define the administrator account, access controls, profile purge, and the administrator's data visibility.

## Requirements

### Requirement: Compte administrateur par variables d'environnement
Le système SHALL définir un compte administrateur dont le login et le mot de passe proviennent de `ASSISTANT_ADMIN_USERNAME` et `ASSISTANT_ADMIN_PASSWORD`. Le mot de passe MUST être fourni, sinon l'application refuse de démarrer, et MUST NOT être journalisé.

#### Scenario: Mot de passe absent
- **WHEN** `ASSISTANT_ADMIN_PASSWORD` n'est pas défini
- **THEN** l'application échoue au démarrage avec un message explicite

### Requirement: Connexion administrateur
L'accès à `/admin` SHALL exiger une authentification par login et mot de passe, indépendante de la session faciale.

#### Scenario: Connexion valide
- **WHEN** l'administrateur saisit des identifiants corrects
- **THEN** une session admin à durée courte est ouverte

#### Scenario: Accès sans authentification
- **WHEN** un utilisateur non administrateur appelle une URL `/admin`
- **THEN** il est redirigé vers la connexion ou reçoit 401/403

#### Scenario: Échecs répétés
- **WHEN** plusieurs connexions échouent consécutivement
- **THEN** les tentatives sont temporairement bloquées

### Requirement: Purge d'un profil
L'administrateur SHALL pouvoir purger un profil : suppression du profil, de ses embeddings, de son avatar et de toutes ses données d'application, après confirmation explicite. L'utilisateur concerné devra se réenrôler.

#### Scenario: Purge confirmée
- **WHEN** l'administrateur confirme la purge du profil P
- **THEN** P et toutes ses données sont supprimés et ses sessions sont invalidées

#### Scenario: Réenrôlement
- **WHEN** l'utilisateur purgé crée à nouveau un profil
- **THEN** il suit le parcours de création standard

### Requirement: Périmètre de l'administrateur
L'administrateur MUST NOT pouvoir consulter les données d'application des profils ; il voit uniquement nom et avatar.

#### Scenario: Liste des profils
- **WHEN** l'administrateur ouvre la liste
- **THEN** seuls nom et avatar de chaque profil sont affichés
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

### Requirement: Gestion de la clé PRIM
L'administrateur authentifié SHALL pouvoir enregistrer, remplacer et supprimer la clé API PRIM depuis un formulaire de `/admin`. La clé MUST être stockée en clair en base, MUST ne jamais être réaffichée dans l'interface ni apparaître dans les logs, et la page SHALL indiquer seulement si une clé est configurée. Un champ laissé vide lors d'un enregistrement MUST conserver la clé existante. Les opérations MUST exiger une session administrateur et être protégées contre le CSRF.

#### Scenario: Enregistrement
- **WHEN** l'administrateur saisit une clé non vide et valide le formulaire
- **THEN** la clé est enregistrée, la page indique « Clé PRIM configurée » et la valeur n'est pas réaffichée

#### Scenario: Remplacement
- **WHEN** l'administrateur saisit une nouvelle clé alors qu'une clé existe
- **THEN** la nouvelle clé remplace l'ancienne

#### Scenario: Champ vide
- **WHEN** l'administrateur valide le formulaire avec un champ vide alors qu'une clé existe
- **THEN** la clé existante est conservée

#### Scenario: Suppression
- **WHEN** l'administrateur demande la suppression de la clé
- **THEN** la clé est effacée et la page indique « Clé PRIM non configurée »

#### Scenario: Sans session admin
- **WHEN** un utilisateur non administrateur appelle les endpoints de gestion de la clé PRIM
- **THEN** la réponse est 401 ou 403 et rien n'est modifié
