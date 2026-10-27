## Why

Aucun moyen de diffuser une information commune à tous les utilisateurs (événement, échéance). Un widget « Annonces » alimenté par l'administrateur comble ce manque, avec un compte à rebours en temps réel pour les échéances.

## What Changes

- Nouveau widget `announcements`, activable ou non par chaque profil via le système de slots existant.
- Nouvelles données **globales** (non liées à un profil) : une annonce a un titre, une date de début, une date de fin et un indicateur « compte à rebours ».
- Le widget liste les annonces en cours (début <= maintenant < fin), triées par date de fin croissante.
- Les annonces marquées compte à rebours affichent un décompte en mois, jours, heures, minutes, secondes, mis à jour chaque seconde. Les unités de tête à zéro sont masquées.
- Le décompte s'appuie sur l'horloge serveur (`serverNow` fourni avec les données). Une annonce qui expire disparaît en temps réel.
- Panneau Administration : création, modification et suppression manuelle des annonces, avec statut affiché (à venir, en cours, expirée). Aucune purge automatique.
- Toutes les dates sont saisies et affichées en fuseau `Europe/Paris`, et stockées en UTC.
- Chaque annonce voit son titre intégralement réécrit avec l'animation d'écriture à intervalle aléatoire propre, entre 15 s et 60 s (valeurs fixes, non paramétrables).
- Toute annonce affiche, après son texte, un curseur clignotant persistant comme si la frappe allait reprendre.
- Une annonce sans compte à rebours affiche sa date et son heure de fin (heure de Paris) à la suite de son titre.
- La première annonce est séparée du titre du widget comme les annonces le sont entre elles.
- Version du projet incrémentée dans `pom.xml` (0.0.2) et entrée correspondante dans `CHANGELOG.md`.
- Les annonces sont incluses dans la sauvegarde/restauration (le service de backup est piloté par le métamodèle JPA : aucun changement de spec `data-backup`).

## Capabilities

### New Capabilities
- `app-announcements`: widget d'annonces globales, API de lecture des annonces en cours, compte à rebours temps réel.

### Modified Capabilities
- `admin-management`: gestion des annonces depuis le panneau Administration.
- `ui-theme`: exception à « Textes déjà affichés » pour la réécriture aléatoire explicite du widget Annonces.

## Impact

- Code : nouvelle entité/repository/service d'annonces, `AnnouncementsAssistantApp`, contrôleur de lecture, extension de `AdminController` et de `admin/index.html`, fragment `fragments/widgets/announcements.html`, module `js/widgets/announcements.js`.
- Base : nouvelle table `announcement` (schéma Hibernate, SQLite/PostgreSQL), à ajouter à `docs/database.puml`.
- Backup : table incluse automatiquement par le métamodèle.
- `typewriter.js` : option de relance forcée de l'écriture ; `theme.css` : curseur clignotant persistant.
- `pom.xml` et `CHANGELOG.md` : version 0.0.2.
- Dépendances : aucune.
