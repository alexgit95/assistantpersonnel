## Why

Quand personne n'est reconnu, l'écran d'accueil est vide : les widgets dépendent d'une session faciale. Un écran allumé en permanence dans la pièce devrait pourtant afficher des informations non personnelles (météo, actualités). L'administrateur doit pouvoir les choisir, sans qu'aucune donnée privée (poids, trajets) ne puisse apparaître.

## What Changes

- Nouveau **profil invité global**, unique, créé automatiquement au démarrage (et après une restauration de sauvegarde), repéré par une table `guest_setting`. Il est exclu de la reconnaissance faciale, de la liste d'administration, de la purge et de `ASSISTANT_MAX_PROFILES`, et aucune session ne peut être ouverte à son nom.
- **Écran invité** : dès qu'il n'y a pas de session valide (chargement, déconnexion, session expirée, pause de reconnaissance), l'accueil affiche les widgets de l'invité. Dès qu'un visage est reconnu, ils sont remplacés par ceux du profil.
- Par défaut : Météo en haut à gauche (emplacement 1) et Actualités en haut à droite (emplacement 3), mêmes 5 emplacements que les profils. Aucune ville par défaut : les widgets invitent l'administrateur à configurer. Actualités : toutes les catégories tant que l'administrateur n'a rien enregistré.
- **Liste blanche côté serveur** `guestAllowed()` sur `AssistantApp` (faux par défaut ; vrai pour Météo et Actualités). Le poids et les autres applications ne sont jamais servis à l'invité, même si activées par défaut.
- **Lecture seule sans session** : annotation `@AllowGuest` sur les seules lectures (liste des applications, fragments, prévision météo, flux d'actualités). Les écritures, `/configuration` et la connexion restent liées à une vraie session.
- **Formulaire dédié dans `/admin`** (`/admin/guest`, rôle ADMIN, CSRF) : emplacements parmi les applications autorisées, ville météo (recherche puis choix dans la page), catégories d'actualités.
- Widgets Météo et Actualités : message « à configurer par l'administrateur » en mode invité.
- « Créer un profil » reste visible, l'horloge et la webcam sont inchangées.

## Capabilities

### New Capabilities
- `guest-dashboard`: profil invité global, affichage sans session, liste blanche, lecture seule, formulaire d'administration, exclusions.

### Modified Capabilities
- `app-framework`: l'écran d'accueil affiche les widgets de l'invité sans session ; contrat `guestAllowed()` du registre d'applications.
- `admin-management`: formulaire `/admin/guest` ; l'invité n'apparaît pas dans la liste ni la purge.
- `profile-enrollment`: l'invité ne compte pas dans la limite de profils ni dans la reconnaissance.
- `data-backup`: l'invité est sauvegardé ; un dump sans `guest_setting` reste restaurable et l'invité est recréé après restauration.
- `app-weather`: message de configuration pour l'invité.
- `app-news`: toutes les catégories par défaut pour l'invité et message de configuration.

## Impact

- Code : `auth` (`SessionInterceptor`, `AuthenticatedSession`, `AuthController.login`), `apps` (`AssistantApp`, `ProfileAppService`, contrôleurs `GET`), `profile` (`ProfileService`), `admin` (nouveau contrôleur et gabarit, `BackupService`), `news` (`NewsService`), `weather`, nouveau paquet `guest`, `static/js/home.js` et widgets.
- Base : nouvelle table `guest_setting` (`docs/database.puml`), compatible SQLite et PostgreSQL via `ddl-auto`.
- Aucun changement de pipeline CI/CD. Aucune nouvelle dépendance.
- Sécurité : lectures anonymes limitées à des données non personnelles, réponses invitées en `Cache-Control: no-store`.
- Documentation : README, CHANGELOG, tests JUnit 5 / Mockito et Node.
