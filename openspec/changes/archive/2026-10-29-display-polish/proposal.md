## Why

Plusieurs détails d'affichage nuisent à la lisibilité et à la cohérence : les lignes perturbées du widget Transports se noient parmi les lignes normales, le libellé « fin le » alourdit les annonces simples, l'avatar rond ne suit pas la charte visuelle, et le menu du profil affiche des commandes de session (dont « Configuration ») à un utilisateur non connecté.

## What Changes

- Widget Transports : les lignes dont le trafic n'est pas normal sont placées en tête ; l'ordre des favoris du profil est conservé au sein de chaque groupe.
- Widget Annonces : une annonce sans compte à rebours affiche « titre · jj/mm/aaaa à hh:mm » (date de fin), sans le mot « fin le ».
- Icône du profil : hexagone à côté plat en haut, sur tous les écrans où l'avatar du profil apparaît (accueil, configuration, administration, choix de l'avatar à l'enrôlement). Les autres éléments ronds ne changent pas.
- Menu du profil : « Configuration », « Refaire mes captures » et « Déconnexion » sont réellement masqués sans profil connecté (l'attribut `hidden` est aujourd'hui neutralisé par le CSS) ; « Administration » reste toujours visible. L'accès serveur à `/configuration` reste refusé (401) hors session.
- Version du projet portée à 0.0.8 (`pom.xml`) et changelog daté de cette version.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-transit`: ordre d'affichage des lignes favorites selon l'état du trafic.
- `app-announcements`: format de la date de fin des annonces simples (sans « fin le »).
- `ui-theme`: forme hexagonale de l'avatar du profil ; visibilité des entrées du menu du profil selon la session.

## Impact

- Code : `TransitService.buildLineAlerts` (tri stable), `announcements.js` (`announcementLabel`), `home.css`, `admin.css`, `enrollment.css` (forme de l'avatar), `profile-header.html` (silhouette de secours), règle CSS du menu du profil.
- Tests : `TransitServiceTest`, `announcements.test.mjs`, test d'intégration du menu hors session.
- Documentation : `README.md`, `CHANGELOG.md`, `pom.xml` (0.0.8).
- Aucun changement d'API ni de schéma de base de données ; pas de dépendance nouvelle.
