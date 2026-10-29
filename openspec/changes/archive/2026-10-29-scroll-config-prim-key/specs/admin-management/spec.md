## ADDED Requirements

### Requirement: Gestion de la clé PRIM
L'administrateur authentifié SHALL pouvoir enregistrer, remplacer et supprimer la clé API PRIM depuis un formulaire de `/admin`. La clé MUST être stockée en clair en base, MUST ne jamais être réaffichée dans l'interface ni apparaître dans les logs, et la page SHALL indiquer seulement si une clé est configurée. Un champ laissé vide lors d'un enregistrement MUST conserver la clé existante. Les opérations MUST exiger une session administrateur et être protégées contre le CSRF.

#### Scenario: Enregistrement
- **WHEN** l'administrateur saisit une clé non vide et valide le formulaire
- **THEN** la clé est enregistrée, la page indique « Clé PRIM configurée » et la valeur n'est pas réaffichée

#### Scenario: Remplacement
- **WHEN** l'administrateur saisit une nouvelle clé alors qu'une clé existe
- **THEN** la nouvelle clé remplace l'ancienne

#### Scenario: Champ vide
- **WHEN** l'administrateur valide le formulaire avec un champ vide alors qu'une clé existe
- **THEN** la clé existante est conservée

#### Scenario: Suppression
- **WHEN** l'administrateur demande la suppression de la clé
- **THEN** la clé est effacée et la page indique « Clé PRIM non configurée »

#### Scenario: Sans session admin
- **WHEN** un utilisateur non administrateur appelle les endpoints de gestion de la clé PRIM
- **THEN** la réponse est 401 ou 403 et rien n'est modifié
