## 1. Données

- [x] 1.1 Créer l'entité `Announcement` (id, title, startsAtMs, endsAtMs, countdown) et `AnnouncementRepository`
- [x] 1.2 Créer `AnnouncementService` : liste des annonces en cours triées par fin croissante, CRUD, validation (titre non vide, fin > début), conversion `Europe/Paris` <-> epoch ms
- [x] 1.3 Mettre à jour `docs/database.puml` avec la table `announcement`

## 2. Widget et API

- [x] 2.1 Créer `AnnouncementsAssistantApp` (`key=announcements`, fragment, module JS)
- [x] 2.2 Créer `GET /api/announcements` (`@RequireSession`) renvoyant `serverNow` et les annonces en cours
- [x] 2.3 Créer `fragments/widgets/announcements.html`
- [x] 2.4 Créer `js/widgets/announcements.js` : offset d'horloge serveur, tick 1 s, décompte calendaire Paris avec unités de tête masquées, retrait en direct des annonces expirées, rechargement périodique, rendu en `textContent`
- [x] 2.5 Ajouter les styles du widget

- [x] 2.6 Ajouter `nextRetypeDelay` (tirage uniforme entre 15 s et 60 s, constantes fixes) dans le widget
- [x] 2.7 Ajouter l'option `restart` à `typeText` dans `typewriter.js` et l'utiliser pour réécrire chaque titre avec un timer propre à l'annonce, redémarré à chaque réécriture (timers annulés à la disparition de l'annonce ou du widget)
- [x] 2.8 Ajouter le curseur clignotant persistant (CSS) à tous les titres d'annonce, fixe en `prefers-reduced-motion`
- [x] 2.9 Afficher la date de fin (heure de Paris) après le titre des annonces sans compte à rebours et aligner l'espacement titre du widget / première annonce sur celui entre annonces
- [x] 2.10 Ajouter `startsAt` à l'API et une barre de progression fine noir et blanc pour les annonces avec compte à rebours (spec + tests)

## 3. Administration

- [x] 3.1 Ajouter dans `AdminController` les endpoints créer / modifier / supprimer (POST + redirect, confirmation de suppression)
- [x] 3.2 Ajouter la section « Annonces » dans `admin/index.html` (liste avec statut à venir / en cours / expirée, formulaire en heure de Paris)

## 4. Tests

- [x] 4.1 Tests de `AnnouncementService` : filtrage, tri, validation, conversion de fuseau (dont changement d'heure)
- [x] 4.2 Test du endpoint `/api/announcements` (401 sans session, contenu, `serverNow`)
- [x] 4.3 Tests JS du calcul de décompte (mois civils, unités masquées, expiration)
- [x] 4.4 Tests admin (accès réservé, création, dates incohérentes, suppression) et inclusion de la table dans le backup
- [x] 4.5 Tests JS : `typeText` avec `restart` rejoue l'écriture d'un texte identique ; sans `restart`, comportement inchangé
- [x] 4.6 Tests JS de `nextRetypeDelay` (bornes 15000 et 60000 avec `random` = 0 et proche de 1) et annulation des timers

## 5. Version

- [x] 5.1 Passer la version de `pom.xml` à 0.0.2
- [x] 5.2 Ajouter une section `[0.0.2]` dans `CHANGELOG.md` (Added : widget Annonces, réécriture aléatoire des titres, curseur persistant ; Changed : option `restart` du typewriter)
