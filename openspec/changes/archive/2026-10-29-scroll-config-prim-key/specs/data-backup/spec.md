## MODIFIED Requirements

### Requirement: Export JSON complet
Depuis la page d'administration, le système SHALL permettre de télécharger un fichier JSON contenant tout le contenu de la base (profils, embeddings, avatars, configurations, clé PRIM et données de toutes les applications), à l'exclusion des sessions. Le fichier contenant la clé PRIM en clair, l'avertissement de confidentialité de la sauvegarde SHALL la mentionner.

#### Scenario: Téléchargement
- **WHEN** l'administrateur clique sur « Télécharger la sauvegarde »
- **THEN** un fichier JSON horodaté contenant la version du format et les lignes de chaque table est téléchargé

#### Scenario: Nouvelle application
- **WHEN** une nouvelle application avec ses propres entités est ajoutée
- **THEN** ses données sont incluses dans l'export sans modification du code de sauvegarde

#### Scenario: Clé PRIM exportée
- **WHEN** une clé PRIM est enregistrée et que l'administrateur exporte une sauvegarde
- **THEN** la table `prim_setting` figure dans le fichier avec la clé

### Requirement: Restauration depuis un fichier
La page d'administration SHALL permettre de restaurer la base depuis un fichier d'export après confirmation explicite. La restauration remplace tout le contenu existant en une seule transaction. Un fichier produit avant l'introduction de la table `prim_setting` SHALL rester restaurable : la table est alors considérée vide et la clé PRIM existante est supprimée.

#### Scenario: Restauration réussie
- **WHEN** l'administrateur envoie un fichier valide et confirme
- **THEN** le contenu actuel est remplacé par celui du fichier et toutes les sessions sont invalidées

#### Scenario: Restauration de la clé PRIM
- **WHEN** le fichier contient une clé PRIM
- **THEN** elle remplace la clé actuelle et est utilisée dès l'appel PRIM suivant

#### Scenario: Ancien fichier sans table prim_setting
- **WHEN** le fichier est valide mais ne contient pas la table `prim_setting`
- **THEN** la restauration réussit et aucune clé PRIM n'est configurée ensuite

#### Scenario: Fichier invalide
- **WHEN** le fichier n'est pas un JSON conforme, a une version inconnue ou une structure incompatible
- **THEN** la restauration est refusée avant toute écriture et la base reste inchangée

#### Scenario: Échec en cours de restauration
- **WHEN** une erreur survient pendant l'insertion
- **THEN** la transaction est annulée et la base est strictement dans son état précédent

#### Scenario: Fichier trop volumineux
- **WHEN** le fichier dépasse `ASSISTANT_BACKUP_MAX_UPLOAD_MB`
- **THEN** il est rejeté (413)
