## MODIFIED Requirements

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
