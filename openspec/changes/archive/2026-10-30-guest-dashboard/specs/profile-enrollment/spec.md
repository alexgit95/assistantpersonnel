## ADDED Requirements

### Requirement: Invité hors limite de profils et de reconnaissance
Le profil invité MUST NOT compter dans `ASSISTANT_MAX_PROFILES`, MUST NOT être comparé lors de la reconnaissance faciale et MUST NOT pouvoir ouvrir de session.

#### Scenario: Création avec invité
- **WHEN** `ASSISTANT_MAX_PROFILES` vaut 2 et 1 profil réel existe en plus de l'invité
- **THEN** la création d'un deuxième profil réel est acceptée
