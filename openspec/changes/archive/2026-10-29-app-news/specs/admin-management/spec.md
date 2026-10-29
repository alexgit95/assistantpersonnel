## ADDED Requirements

### Requirement: Gestion des catégories d'actualités
L'administrateur SHALL pouvoir créer, modifier, supprimer et ordonner des catégories d'actualités depuis le panneau Administration. Une catégorie comporte un nom non vide, l'URL de son flux RSS et une position d'affichage. L'URL MUST être en `https` et ne MUST PAS cibler une adresse locale ou privée ; sinon l'enregistrement est refusé avec un message explicite et rien n'est modifié. La suppression d'une catégorie MUST afficher un avertissement indiquant que des profils peuvent l'avoir sélectionnée, puis rester possible après confirmation.

#### Scenario: Création
- **WHEN** l'administrateur saisit un nom et une URL https valide, puis valide
- **THEN** la catégorie est enregistrée, apparaît dans la liste et est proposée dans la configuration des profils

#### Scenario: URL refusée
- **WHEN** l'URL n'est pas en https ou cible une adresse locale
- **THEN** l'enregistrement est refusé avec un message explicite

#### Scenario: Nom vide
- **WHEN** le nom est vide
- **THEN** l'enregistrement est refusé

#### Scenario: Ordre d'affichage
- **WHEN** l'administrateur modifie la position d'une catégorie
- **THEN** l'ordre de rotation et de configuration reflète la nouvelle position

#### Scenario: Suppression avec avertissement
- **WHEN** l'administrateur demande la suppression d'une catégorie
- **THEN** un avertissement est affiché et, après confirmation, la catégorie est supprimée

#### Scenario: Accès non administrateur
- **WHEN** un utilisateur sans session administrateur appelle les endpoints de gestion
- **THEN** la réponse est 401 ou 403
