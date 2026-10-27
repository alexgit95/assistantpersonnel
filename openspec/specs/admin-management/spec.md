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