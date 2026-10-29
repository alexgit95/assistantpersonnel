## 1. Transports

- [x] 1.1 Trier `buildLineAlerts` de façon stable : lignes avec alertes d'abord, puis les autres, ordre des favoris conservé dans chaque groupe

## 2. Annonces

- [x] 2.1 Retirer « fin le » de `announcementLabel` dans `announcements.js` (format « titre · jj/mm/aaaa à hh:mm »)

## 3. Avatar hexagonal

- [x] 3.1 Appliquer l'hexagone à côté plat en haut (double `clip-path` pour le contour) à `#avatar`, `.profile-avatar-button` et `.avatar-fallback` dans `home.css`
- [x] 3.2 Appliquer la même forme à `.profile-cell img` et `.avatar-placeholder` dans `admin.css`
- [x] 3.3 Appliquer la même forme à `.avatar-choice` dans `enrollment.css`
- [x] 3.4 Vérifier le focus clavier visible et le rendu sur l'accueil, `/configuration`, `/admin` et `/enroll`

## 4. Menu du profil

- [x] 4.1 Ajouter `.profile-menu-panel [hidden] { display: none }` pour rendre `hidden` effectif sur les liens et boutons du menu
- [x] 4.2 Vérifier que « Administration » reste visible et que `/configuration` sans session répond 401

## 5. Version, tests et documentation

- [x] 5.1 Passer `pom.xml` à la version 0.0.8
- [x] 5.2 Tests : ordre des lignes dans `TransitServiceTest`, libellé dans `announcements.test.mjs`, entrées du menu hors session (test d'intégration ou JS)
- [x] 5.3 Lancer la suite complète (Maven : `C:\USINE_LOGICIELLE\apache-maven\bin` ; JS : `node.exe --test`)
- [x] 5.4 Mettre à jour `README.md` et `CHANGELOG.md` (renommer `[Unreleased]` en `[0.0.8]`, y ajouter ces changements et « Version du projet portée à 0.0.8 »)
