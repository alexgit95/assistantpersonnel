## Context

Le widget Transports reçoit une ligne par favori depuis `TransitService.buildLineAlerts`, dans l'ordre de la configuration. Les annonces sont formatées par `announcementLabel` (`announcements.js`). L'avatar du profil est rond (`border-radius: 50%`) à quatre endroits : `#avatar` et `.profile-avatar-button` (`home.css`), `.profile-cell img` et `.avatar-placeholder` (`admin.css`), `.avatar-choice` (`enrollment.css`). Dans le menu du profil, `profile-menu.js` pose `hidden` sur les entrées de session, mais `.profile-menu-panel a, .profile-menu-panel button { display: flex }` l'emporte sur le `display: none` par défaut de `[hidden]`. `/configuration` est déjà protégé côté serveur (`@RequireSession`, intercepteur de session).

## Goals / Non-Goals

**Goals:**
- Mettre les lignes perturbées en tête sans bouleverser l'ordre choisi par le profil.
- Simplifier le libellé des annonces simples.
- Uniformiser l'avatar du profil en hexagone à côté plat en haut.
- Rendre effectif le masquage des entrées de session du menu.

**Non-Goals:**
- Changer l'API, le schéma de base ou la sévérité des alertes.
- Rendre hexagonaux d'autres éléments ronds (boutons d'icône, barres de progression).
- Rediriger `/configuration` hors session (la réponse 401 actuelle est conservée).

## Decisions

- **Tri côté serveur** dans `buildLineAlerts` : un tri stable place d'abord les lignes ayant au moins une alerte, puis les autres, en conservant l'ordre des favoris dans chaque groupe. Alternative écartée : tri dans `transit.js`, qui laisserait le contrat de l'API incohérent avec l'affichage et serait plus difficile à tester. Une ligne sans donnée PRIM compte comme normale. Entre lignes perturbées, aucun tri par sévérité : l'ordre des favoris prévaut.
- **Libellé des annonces** : `titre · jj/mm/aaaa à hh:mm` (date de fin, heure de Paris). Seul le mot « fin le » est retiré ; les formateurs existants sont conservés.
- **Hexagone en CSS** : `clip-path: polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%)` (côté plat en haut). Comme `clip-path` coupe la bordure, le contour fin est obtenu par un hexagone extérieur gris (fond ou pseudo-élément) et un hexagone intérieur légèrement réduit portant l'image. Les images restent carrées avec `object-fit: cover`. La silhouette de secours suit la même forme. Alternative écartée : SVG avec `<polygon>`, qui obligerait à modifier plusieurs gabarits HTML. Le focus clavier du bouton du menu reste visible (contour sur l'élément hexagonal).
- **Menu** : ajouter `.profile-menu-panel [hidden] { display: none }` pour que l'attribut `hidden` redevienne effectif pour tous les liens et boutons. La logique de `profile-menu.js` ne change pas. « Administration » n'a pas d'attribut `hidden` et reste visible ; elle est protégée par l'authentification administrateur.
- **Version** : `pom.xml` passe à 0.0.8 ; la section `[Unreleased]` du changelog (widget Actualités) devient `[0.0.8]` et reçoit ces changements.

## Risks / Trade-offs

- [Le contour hexagonal est moins net qu'une bordure CSS] → double `clip-path` à valider visuellement sur les trois écrans.
- [Le tri déplace des lignes à chaque actualisation du trafic] → l'ordre ne change que quand l'état d'une ligne change ; les boucles de réécriture sont indexées par identifiant de ligne et ne sont pas perturbées.
- [Les entrées « Refaire mes captures » et « Déconnexion » deviennent masquées hors session alors qu'elles étaient visibles à tort] → comportement voulu, couvert par un test.
