## MODIFIED Requirements

### Requirement: Écran d'accueil
Le système SHALL afficher une grille fixe sans défilement : horloge en haut à gauche, aperçu webcam dans un carré « W », avatar/menu en haut à droite, un emplacement principal au centre et quatre emplacements auxiliaires autour. Il SHALL aussi afficher l'état de reconnaissance et le temps de session restant.

#### Scenario: Profil connecté
- **WHEN** une session est active
- **THEN** seuls les widgets affectés aux emplacements du profil courant sont affichés, sans modifier la géométrie de la grille

#### Scenario: Sans session
- **WHEN** aucune session n'est active
- **THEN** seuls les widgets de l'invité sont affichés dans ses emplacements, sans modifier la géométrie de la grille

#### Scenario: Aucun défilement
- **WHEN** l'accueil est affiché sur un appareil pris en charge
- **THEN** la grille et ses widgets tiennent dans la fenêtre sans défilement horizontal ou vertical

## ADDED Requirements

### Requirement: Applications autorisées pour l'invité
Le contrat d'application SHALL exposer `guestAllowed()`, faux par défaut, qui indique si l'application peut être servie sans session au profil invité.

#### Scenario: Valeur par défaut
- **WHEN** une application n'implémente pas `guestAllowed()`
- **THEN** elle est considérée comme interdite à l'invité
