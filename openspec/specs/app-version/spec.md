# Purpose

Define the application version (pom version and commit SHA), how it is resolved and exposed by `/api/config`, and its display on the kiosk screen.

## Requirements

### Requirement: Version de l'application
Le système SHALL composer la version de l'application sous la forme `version_du_pom-sha`, où le SHA est celui du commit réduit à 7 caractères (par exemple `1.1.1-a1b2c3d`). Le SHA MUST être lu, par ordre de priorité, dans la variable d'environnement `GIT_COMMIT`, puis dans `git.properties` généré au build ; sans SHA connu, le SHA MUST valoir `dev` (par exemple `1.1.1-dev`). La version SHALL être exposée par `GET /api/config` dans le champ `appVersion`. La version MUST NOT contenir d'information secrète.

#### Scenario: SHA fourni par la variable d'environnement
- **WHEN** `GIT_COMMIT` vaut `a1b2c3d4e5f60718293a4b5c6d7e8f9012345678` et que la version du pom est `1.1.1`
- **THEN** `GET /api/config` renvoie `appVersion` égal à `1.1.1-a1b2c3d`

#### Scenario: SHA fourni par git.properties
- **WHEN** `GIT_COMMIT` est absent et que `git.properties` contient un commit
- **THEN** `appVersion` utilise les 7 premiers caractères de ce commit

#### Scenario: SHA inconnu
- **WHEN** `GIT_COMMIT` est absent et qu'aucun `git.properties` n'existe
- **THEN** `appVersion` vaut `1.1.1-dev`

### Requirement: Affichage de la version sur le kiosque
L'écran d'accueil du kiosque SHALL afficher la version de l'application en bas à droite, en texte discret superposé (petit, grisé), sans réserver de place dans la grille des widgets, sans intercepter les événements de pointage et sous l'écran de capture. La page d'accueil MUST embarquer la version avec laquelle elle a été servie, afin de pouvoir la comparer à celle du serveur. La version MUST NOT être affichée sur les pages du smartphone ni sur l'administration.

#### Scenario: Version visible sur le kiosque
- **WHEN** la page d'accueil est chargée
- **THEN** la version `version_du_pom-sha` apparaît en bas à droite de l'écran

#### Scenario: Version embarquée dans la page
- **WHEN** le serveur rend la page d'accueil
- **THEN** la page contient la version du serveur à cet instant, indépendamment de `GET /api/config`

#### Scenario: Pages hors kiosque
- **WHEN** un smartphone ouvre une page `/mobile/**`
- **THEN** aucune version de l'application n'y est affichée
