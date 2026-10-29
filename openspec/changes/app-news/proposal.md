## Why

Les utilisateurs n'ont aucun moyen de suivre l'actualité depuis l'assistant. Un widget « Actualités » alimenté par des flux RSS, organisés en catégories gérées par l'administrateur, comble ce manque tout en gardant le style machine à écrire de l'application.

## What Changes

- Nouveau widget « Actualités » (clé `news`), activable et affectable à un emplacement comme les autres widgets.
- Administration : création, modification, suppression et ordonnancement de catégories (nom + URL de flux RSS), avec avertissement avant suppression d'une catégorie.
- Configuration par profil : cases à cocher pour choisir les catégories à afficher.
- Affichage : une catégorie à la fois, rotation automatique toutes les 15 s sans action, flèches précédent/suivant bouclantes, 10 entrées maximum par catégorie.
- Chaque entrée affiche son titre (effet machine à écrire) ; un clic déplie la description en texte brut ; l'entrée se replie seule après 20 s et la rotation est suspendue tant qu'elle est dépliée.
- Récupération des flux côté serveur avec la bibliothèque Rome, via le client HTTP sortant (proxy), HTTPS uniquement, IP locales/privées bloquées, cache de 10 minutes par catégorie.
- Mode test avec données factices, comme le widget Transports.
- Nouvelle table `news_category` ; mise à jour de `docs/database.puml`.
- Nouvelle dépendance Maven : Rome.

## Capabilities

### New Capabilities
- `app-news`: widget Actualités (sélection de catégories par profil, rotation, flèches, entrées dépliables, récupération et cache des flux RSS, mode test).

### Modified Capabilities
- `admin-management`: gestion des catégories d'actualités (CRUD, ordre d'affichage, validation de l'URL, avertissement à la suppression).
- `data-backup`: la restauration reste possible pour un fichier sans table `news_category`.

## Impact

- Code : nouveau package `com.assistant.news` (entité, repository, service, client RSS, contrôleur), `NewsAssistantApp`, `AdminController`, `BackupService` (restauration des anciens fichiers), `configuration.js`, `admin.js`, `api.js`, nouveau `widgets/news.js` et fragment Thymeleaf, CSS.
- API : endpoints admin de gestion des catégories ; endpoint profil pour lister les catégories et lire le contenu des catégories choisies.
- Dépendances : `rome` (et JDOM en transitif) ; impact à vérifier sur la taille de l'image Docker.
- Base de données : table `news_category` créée par `ddl-auto`, compatible SQLite et PostgreSQL.
- Sécurité : validation SSRF à chaque récupération, limite de taille et timeout sur les réponses.
