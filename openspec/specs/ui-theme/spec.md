# Purpose

Define the shared OLED visual theme, French-only interface, accessible typing effect, and fixed no-scroll dashboard.

## Requirements

### Requirement: Fond noir pur
Tous les écrans SHALL utiliser un fond noir pur `#000000` sans dégradé, image de fond ni surface grise claire, afin que les pixels soient éteints sur un écran OLED.

#### Scenario: Écran d'accueil
- **WHEN** une page est affichée
- **THEN** la couleur de fond de la page et des conteneurs est `#000000`

### Requirement: Texte blanc
Le texte SHALL être blanc `#ffffff`, avec des nuances de gris réservées aux éléments secondaires tout en conservant un contraste suffisant sur fond noir.

#### Scenario: Texte principal et secondaire
- **WHEN** un texte principal est affiché
- **THEN** sa couleur est `#ffffff`
- **WHEN** un texte secondaire est affiché
- **THEN** son contraste avec le fond reste d'au moins 7:1

### Requirement: Charte centralisée
Les couleurs, polices et espacements SHALL être définis par des variables CSS dans une feuille de style unique réutilisée par tous les écrans et widgets, y compris pour les futures applications.

#### Scenario: Nouveau widget
- **WHEN** une nouvelle application est ajoutée
- **THEN** elle hérite de la charte sans définir ses propres couleurs

### Requirement: Texte écrit en direct
Tout texte qui apparaît (messages d'état, contenus de widgets, résultats) SHALL s'afficher caractère par caractère comme s'il était écrit en direct.

#### Scenario: Apparition d'un texte
- **WHEN** un widget affiche un nouveau texte
- **THEN** les caractères apparaissent progressivement au rythme de `ASSISTANT_UI_TYPING_SPEED_MS`

#### Scenario: Désactivation
- **WHEN** `ASSISTANT_UI_TYPING_ENABLED` vaut false
- **THEN** les textes s'affichent immédiatement en entier

### Requirement: Accessibilité et sobriété de l'effet
L'effet SHALL être désactivé si le navigateur déclare `prefers-reduced-motion`, SHALL exposer le texte complet aux lecteurs d'écran, et SHALL plafonner sa durée totale pour les textes longs.

#### Scenario: Mouvement réduit
- **WHEN** l'utilisateur a activé la réduction des animations
- **THEN** le texte s'affiche immédiatement

#### Scenario: Texte long
- **WHEN** un texte dépasse la durée maximale d'animation
- **THEN** la vitesse est accélérée pour que l'effet se termine dans le plafond

### Requirement: Interface en français uniquement
Tous les textes de l'interface, messages d'erreur, dates (fr-FR) et réponses d'erreur destinées à l'utilisateur SHALL être en français ; le système MUST NOT inclure de mécanisme de traduction multilingue.

#### Scenario: Langue de la page
- **WHEN** une page est affichée
- **THEN** elle déclare `lang="fr"` et tous ses libellés sont en français

### Requirement: Textes déjà affichés
Un texte déjà affiché et inchangé MUST NOT être ré-animé lors d'une actualisation périodique, sauf lorsqu'un widget demande explicitement sa réécriture (réécriture aléatoire du widget Annonces).

#### Scenario: Rafraîchissement d'un widget
- **WHEN** un widget se rafraîchit avec un contenu identique
- **THEN** aucun effet d'écriture n'est rejoué

#### Scenario: Réécriture explicitement demandée
- **WHEN** un widget demande la réécriture d'un texte inchangé
- **THEN** l'effet d'écriture est rejoué, en respectant la désactivation et la réduction des animations

### Requirement: Accueil sans défilement
L'accueil SHALL tenir dans la fenêtre sans défilement et SHALL conserver une géométrie fixe : horloge et aperçu webcam en haut à gauche, avatar en haut à droite, emplacement principal central et quatre emplacements secondaires. Les dimensions peuvent s'adapter à l'écran, mais les emplacements SHALL conserver leurs relations spatiales. Ce verrouillage du défilement SHALL s'appliquer à l'accueil seul ; les pages `/configuration` et `/admin` MUST pouvoir défiler verticalement lorsque leur contenu dépasse la fenêtre.

#### Scenario: Affichage desktop ou mobile
- **WHEN** l'accueil est affiché sur un appareil pris en charge
- **THEN** tous les emplacements et commandes visibles tiennent dans la fenêtre sans défilement horizontal ou vertical

#### Scenario: Page de configuration longue
- **WHEN** le contenu de `/configuration` dépasse la hauteur de la fenêtre
- **THEN** la page défile verticalement et toutes ses commandes restent atteignables

#### Scenario: Page d'administration longue
- **WHEN** le contenu de `/admin` dépasse la hauteur de la fenêtre
- **THEN** la page défile verticalement

### Requirement: Menu de l'avatar
Les commandes de configuration SHALL être accessibles depuis le menu ouvert en cliquant sur l'avatar en haut à droite. L'accueil SHALL afficher l'avatar comme contrôle d'ouverture du menu, et non un bouton de configuration séparé.

#### Scenario: Accès à la configuration
- **WHEN** le profil clique sur son avatar puis choisit « Configuration »
- **THEN** il arrive sur la page dédiée de configuration des widgets
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
