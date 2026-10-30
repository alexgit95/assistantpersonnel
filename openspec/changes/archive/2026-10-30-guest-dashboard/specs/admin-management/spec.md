## ADDED Requirements

### Requirement: Gestion de l'écran invité
L'administrateur SHALL pouvoir configurer l'écran invité depuis `/admin/guest`, accessible depuis la page d'administration, avec les mêmes garanties d'accès que les autres fonctions d'administration. Cette configuration MUST se limiter aux emplacements des applications autorisées, à la ville météo et aux catégories d'actualités de l'invité.

#### Scenario: Lien depuis l'administration
- **WHEN** l'administrateur ouvre `/admin`
- **THEN** un accès au formulaire de l'écran invité est proposé

### Requirement: Invité absent de la liste et de la purge
La liste des profils de l'administration MUST NOT contenir le profil invité et la purge MUST refuser son identifiant.

#### Scenario: Liste des profils
- **WHEN** l'administrateur ouvre la liste des profils
- **THEN** le profil invité n'y figure pas
