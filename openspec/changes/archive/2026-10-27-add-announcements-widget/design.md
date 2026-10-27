## Context

Les widgets existants (`weather`, `weight`) sont des beans `AssistantApp` rendus via `/apps/{key}/fragment` et un module JS. Leurs données sont par profil. Les annonces sont les premières données globales. L'administration est du Thymeleaf serveur (`AdminController`, `admin/index.html`). `BackupService` découvre les tables par le métamodèle JPA, donc une nouvelle entité est sauvegardée sans modification.

## Goals / Non-Goals

**Goals:**
- Widget d'annonces globales, gérées depuis l'admin, avec compte à rebours temps réel.
- Réutiliser le registre d'applications, l'admin existante et le backup.

**Non-Goals:**
- Contenu riche (titre seul), notifications, purge automatique, ciblage par profil.

## Decisions

- **Entité `Announcement`** (`announcement` : id, title, starts_at_ms, ends_at_ms, countdown). Dates en epoch millisecondes (UTC) comme `profile.created_at_ms` : portable SQLite/PostgreSQL et sans ambiguïté de fuseau. Alternative `timestamp` écartée (comportement divergent selon SGBD).
- **Fuseau** : constante `Europe/Paris`. L'admin saisit des `datetime-local` convertis via `ZonedDateTime.of(..., ZoneId.of("Europe/Paris"))` ; les heures inexistantes/ambiguës du changement d'heure sont résolues par Java. L'affichage admin reconvertit en heure de Paris.
- **API de lecture** : `GET /api/announcements` (`@RequireSession`) renvoie `{ serverNow, announcements: [{ id, title, endsAt, countdown }] }`, en epoch ms, filtrée `starts <= now < ends` et triée par `ends` croissant. Pas de filtrage par profil (donnée globale, exception assumée à l'« isolation des données » qui concerne les données par profil).
- **Fragment** : `AnnouncementsAssistantApp` (`key=announcements`, fragment `fragments/widgets/announcements`, module `/js/widgets/announcements.js`) ; le fragment est un squelette, les données viennent de l'API.
- **Horloge** : le client calcule `offset = serverNow - Date.now()` à la réception, puis `now = Date.now() + offset`. Un seul `setInterval(1s)` met à jour tous les compteurs et retire les annonces expirées ; arrêt du timer quand le widget est détaché. Un rechargement périodique (ex. 60 s) intègre les annonces nouvelles ou modifiées et resynchronise l'offset.
- **Décompte calendaire** : calcul fait côté client en fuseau Paris : mois entiers entre `now` et `end` (en heure de Paris), puis jours/h/min/s restants. Implémenté comme fonction pure exportée, testable (`src/test/js`). Unités de tête nulles masquées. Alternative : calcul serveur, écartée car il faudrait un aller-retour par seconde.
- **Réécriture aléatoire par annonce** : constantes JS fixes `RETYPE_MIN_MS = 15000` et `RETYPE_MAX_MS = 60000` dans le module du widget, sans propriété ni variable d'environnement. Chaque annonce a son propre `setTimeout` ; le délai est tiré uniformément dans [min, max] par une fonction pure `nextRetypeDelay(random = Math.random)` (testable), puis retiré après chaque réécriture. Les timers sont annulés quand l'annonce disparaît de la liste ou que le widget est détaché. `typewriter.js` gagne une option `restart: true` qui contourne le court-circuit « texte identique » de `typeText`. Seuls les titres sont réécrits ; les chiffres du compte à rebours changent chaque seconde et ne sont pas animés (réécrire des chiffres qui bougent est illisible). Le timer est lié au cycle de vie du widget. Alternative écartée : forcer la réécriture au rechargement des données, car elle serait couplée au fetch.
- **Curseur persistant** : classe CSS `announcement-idle-caret` sur le titre de toutes les annonces (avec ou sans compte à rebours), réutilisant `@keyframes typewriter-caret` de `theme.css`. Elle s'applique au `.typewriter-visual` uniquement hors frappe (le caret `is-typing` existant prend le relais pendant l'écriture, sans doublon). En `prefers-reduced-motion`, le curseur est fixe.
- **Version** : `pom.xml` passe à 0.0.2 et `CHANGELOG.md` reçoit une section `[0.0.2]`.
- **Admin** : section « Annonces » dans `admin/index.html`, POST + redirect (PRG) comme le backup : créer, modifier, supprimer (avec confirmation). Statut calculé avec l'instant serveur. Validation serveur : titre non vide et borné, fin > début.
- **Backup** : aucune modification ; le test de sauvegarde vérifie que la table est incluse.

## Risks / Trade-offs

- [Calcul des mois en JS avec fuseau Paris] → utiliser `Intl.DateTimeFormat` avec `timeZone: 'Europe/Paris'` pour extraire les champs civils, couvrir le changement d'heure par des tests.
- [Annonce créée pendant que le widget est ouvert] → rechargement périodique ; pas de push temps réel.
- [Dérive du timer navigateur en onglet inactif] → chaque tick recalcule depuis `Date.now() + offset`, pas de décrément.
- [XSS via titre] → insertion par `textContent` uniquement.
