## ADDED Requirements

### Requirement: Météo de l'écran invité
Le widget Météo SHALL être autorisé pour l'invité et utiliser la ville enregistrée par l'administrateur. Tant qu'aucune ville n'est enregistrée pour l'invité, il MUST afficher un message indiquant que l'administrateur doit configurer l'écran invité, et non l'invitation à ouvrir la configuration du profil.

#### Scenario: Ville configurée
- **WHEN** l'administrateur a enregistré une ville pour l'invité
- **THEN** le widget affiche la météo de cette ville sans session

#### Scenario: Aucune ville
- **WHEN** aucune ville n'est enregistrée pour l'invité
- **THEN** le widget affiche « À configurer par l'administrateur » et n'appelle pas la source météo
