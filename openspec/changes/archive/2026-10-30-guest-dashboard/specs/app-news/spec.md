## ADDED Requirements

### Requirement: Actualités de l'écran invité
Le widget Actualités SHALL être autorisé pour l'invité. Tant que l'administrateur n'a enregistré aucune sélection pour l'invité (clé `categoryIds` absente), toutes les catégories existantes, y compris celles créées ensuite, MUST s'afficher. Une sélection enregistrée, même vide, MUST s'appliquer telle quelle. Sans catégorie à afficher, le widget MUST indiquer que l'administrateur doit configurer l'écran invité.

#### Scenario: Aucune sélection enregistrée
- **WHEN** l'invité n'a pas de `categoryIds` et que trois catégories existent
- **THEN** les trois catégories sont affichées

#### Scenario: Nouvelle catégorie
- **WHEN** une catégorie est créée alors que l'invité n'a pas de sélection enregistrée
- **THEN** elle apparaît sur l'écran invité

#### Scenario: Sélection enregistrée
- **WHEN** l'administrateur enregistre uniquement la catégorie 2
- **THEN** seule la catégorie 2 est affichée, et une nouvelle catégorie n'est pas ajoutée

#### Scenario: Sélection vide
- **WHEN** l'administrateur enregistre une sélection vide
- **THEN** aucune catégorie n'est affichée et le message de configuration est montré
