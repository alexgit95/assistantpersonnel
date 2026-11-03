## MODIFIED Requirements

### Requirement: Écran invité sans session
Le système SHALL afficher sur l'accueil les widgets de l'invité dès qu'il n'existe pas de session faciale valide : au chargement, après une déconnexion, à l'expiration ou à la perte de session et pendant la pause de reconnaissance. Dès qu'une session s'ouvre, ces widgets MUST être remplacés par ceux du profil connecté. Les emplacements et leur géométrie MUST rester identiques ; le QR menu invité remplace le bouton « Créer un profil » et MUST rester affiché tant qu'aucune session n'est ouverte.

#### Scenario: Chargement sans session
- **WHEN** l'accueil est ouvert sans session valide
- **THEN** la météo s'affiche en haut à gauche, les actualités en haut à droite et le QR menu invité est visible

#### Scenario: Reconnaissance d'un profil
- **WHEN** un visage connu est reconnu
- **THEN** les widgets de ce profil remplacent ceux de l'invité et le QR menu connecté remplace le QR invité

#### Scenario: Déconnexion
- **WHEN** l'utilisateur se déconnecte ou que sa session expire
- **THEN** les widgets de l'invité et le QR menu invité reviennent et les données du profil ne sont plus affichées

#### Scenario: Invité non configuré
- **WHEN** aucun invité n'existe
- **THEN** les emplacements restent vides comme avant
