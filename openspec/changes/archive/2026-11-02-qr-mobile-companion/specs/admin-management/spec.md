## ADDED Requirements

### Requirement: Administration adaptée au mobile
Les pages d'administration (`/admin/login`, `/admin`, `/admin/guest`) SHALL être utilisables sur un écran de smartphone (mise en page adaptative, champs et boutons manipulables au doigt, aucun défilement horizontal imposé) tout en restant protégées par l'authentification administrateur indépendante de la session faciale. Le menu mobile, invité comme connecté, SHALL proposer un lien « Administration » vers `/admin/login`.

#### Scenario: Accès depuis le menu mobile
- **WHEN** un utilisateur choisit « Administration » dans le menu mobile
- **THEN** il arrive sur `/admin/login` et reste soumis à l'authentification administrateur

#### Scenario: Utilisation sur smartphone
- **WHEN** l'administrateur ouvre `/admin` sur un écran étroit
- **THEN** toutes les sections (profils, annonces, catégories, clé PRIM, sauvegarde, écran invité) sont lisibles et utilisables

#### Scenario: Cookie d'administration en HTTP
- **WHEN** l'administrateur se connecte depuis un smartphone en HTTP sur le LAN
- **THEN** la session d'administration est conservée car son cookie n'est pas marqué `Secure` par défaut
