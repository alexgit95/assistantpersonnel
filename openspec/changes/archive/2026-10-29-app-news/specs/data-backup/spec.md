## ADDED Requirements

### Requirement: Catégories d'actualités dans la sauvegarde
L'export SHALL inclure la table `news_category`. Un fichier produit avant l'introduction de cette table SHALL rester restaurable : la table est alors considérée vide.

#### Scenario: Export des catégories
- **WHEN** l'administrateur exporte une sauvegarde
- **THEN** la table `news_category` figure dans le fichier avec ses lignes

#### Scenario: Ancien fichier sans table news_category
- **WHEN** le fichier est valide mais ne contient pas la table `news_category`
- **THEN** la restauration réussit et aucune catégorie n'existe ensuite
