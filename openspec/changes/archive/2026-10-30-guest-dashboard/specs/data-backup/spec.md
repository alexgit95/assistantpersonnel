## ADDED Requirements

### Requirement: Invité dans la sauvegarde
La sauvegarde SHALL inclure la table `guest_setting` avec le profil invité et sa configuration. Un dump sans cette table MUST rester restaurable, et l'invité MUST être recréé avec ses valeurs par défaut à l'issue de toute restauration.

#### Scenario: Dump ancien
- **WHEN** l'administrateur restaure un dump sans `guest_setting`
- **THEN** la restauration réussit et un invité par défaut existe ensuite

#### Scenario: Dump récent
- **WHEN** l'administrateur restaure un dump contenant l'invité et sa configuration
- **THEN** la ville, les catégories et les emplacements de l'invité sont restitués
