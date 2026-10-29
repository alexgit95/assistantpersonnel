# Purpose

Define the extensible application registry, per-profile widget assignments, fixed dashboard, and data isolation.

## Requirements

### Requirement: Registre d'applications extensible
Le système SHALL découvrir automatiquement les applications déclarées (clé, titre, fragment, route de données) sans modification du cœur.

#### Scenario: Ajout d'une application
- **WHEN** un nouveau module implémentant le contrat d'application est ajouté
- **THEN** il apparaît dans le catalogue des applications disponibles

### Requirement: Applications par profil
Chaque profil SHALL pouvoir activer, désactiver, affecter à un emplacement fixe et configurer ses applications. Une application affectée SHALL occuper au plus un emplacement ; un emplacement SHALL accueillir au plus une application. Un emplacement peut rester vide.

#### Scenario: Activation
- **WHEN** un profil active une application
- **THEN** elle apparaît dans l'emplacement fixe auquel le profil l'a affectée

#### Scenario: Affectation unique
- **WHEN** un profil déplace une application d'un emplacement vers un autre
- **THEN** elle est retirée de l'ancien emplacement et affichée dans le nouveau

#### Scenario: Emplacement vide
- **WHEN** aucun widget n'est affecté à un emplacement
- **THEN** l'emplacement reste réservé dans la grille et ne provoque pas de réagencement des autres emplacements

#### Scenario: Configuration en texte JSON
- **WHEN** une configuration est enregistrée
- **THEN** elle est stockée comme texte JSON compatible SQLite et PostgreSQL

### Requirement: Écran d'accueil
Le système SHALL afficher une grille fixe sans défilement : horloge en haut à gauche, aperçu webcam dans un carré « W », avatar/menu en haut à droite, un emplacement principal au centre et quatre emplacements auxiliaires autour. Il SHALL aussi afficher l'état de reconnaissance et le temps de session restant.

#### Scenario: Profil connecté
- **WHEN** une session est active
- **THEN** seuls les widgets affectés aux emplacements du profil courant sont affichés, sans modifier la géométrie de la grille

#### Scenario: Aucun défilement
- **WHEN** l'accueil est affiché sur un appareil pris en charge
- **THEN** la grille et ses widgets tiennent dans la fenêtre sans défilement horizontal ou vertical

### Requirement: Menu du profil et configuration dédiée
Tous les accès aux fonctions de configuration SHALL être regroupés dans le menu ouvert par clic sur l'avatar en haut à droite. La configuration des widgets SHALL se faire sur une page dédiée, et non dans un panneau de l'accueil.

#### Scenario: Ouverture de la configuration
- **WHEN** le profil ouvre le menu de son avatar puis choisit « Configuration »
- **THEN** une page dédiée présente les widgets disponibles et les emplacements réservés

#### Scenario: Accès administration
- **WHEN** l'utilisateur choisit l'administration dans le menu de l'avatar
- **THEN** il accède à `/admin` et reste soumis à l'authentification administrateur existante

### Requirement: Affectation des widgets par glisser-déposer
La page de configuration SHALL permettre d'affecter les widgets disponibles aux cinq emplacements fixes par glisser-déposer et d'enregistrer l'affectation pour le profil courant. La page SHALL pouvoir défiler verticalement si son contenu dépasse la fenêtre et SHALL proposer un bouton de retour à l'accueil, sous forme d'icône flèche accessible (libellé « Retour à l'accueil »).

#### Scenario: Déplacement d'un widget
- **WHEN** le profil dépose un widget sur un emplacement cible
- **THEN** l'affectation est mise à jour et sauvegardée pour ce profil

#### Scenario: Affectation persistante
- **WHEN** le profil revient sur l'accueil après enregistrement ou rechargement
- **THEN** les widgets sont rendus dans les emplacements qu'il avait choisis

#### Scenario: Configuration défilante
- **WHEN** le profil ouvre la page de configuration sur une fenêtre plus petite que son contenu
- **THEN** il peut faire défiler la page pour atteindre les cibles, les réglages et le bouton « Enregistrer »

#### Scenario: Retour à l'accueil
- **WHEN** le profil clique sur l'icône de retour de la page de configuration
- **THEN** il retourne à l'accueil `/`

### Requirement: Isolation des données
Le système MUST filtrer toutes les données d'application par le profil de la session et MUST NOT accepter d'identifiant de profil fourni par le client pour les données.

#### Scenario: Accès à un autre profil
- **WHEN** le profil P tente d'accéder aux données de Q
- **THEN** la requête n'est pas résolue pour Q et renvoie uniquement les données de P ou une erreur 403/404

#### Scenario: Sans session
- **WHEN** un appel de données est fait sans session valide
- **THEN** la réponse est 401

### Requirement: Documentation API
Chaque contrôleur REST SHALL être documenté avec les annotations OpenAPI (`@Tag`, `@Operation`, `@ApiResponse`).

#### Scenario: Swagger
- **WHEN** on ouvre l'interface Swagger
- **THEN** tous les endpoints exposent paramètres, codes de retour et DTO