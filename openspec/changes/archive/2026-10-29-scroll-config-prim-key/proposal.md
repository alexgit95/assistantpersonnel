## Why

La page `/configuration` hérite du verrouillage de défilement de l'accueil et devient inutilisable dès que son contenu dépasse la fenêtre ; `/admin` doit aussi rester défilante. Par ailleurs, la clé PRIM n'est réglable que par variable d'environnement, ce qui impose un redéploiement pour la changer ; elle doit être saisie depuis l'administration, comme le proxy.

## What Changes

- Le verrouillage de défilement (`overflow:hidden`, hauteur `100dvh`) ne s'applique plus qu'à l'accueil, via une classe dédiée sur `body`. `/configuration` et `/admin` défilent normalement.
- La page `/configuration` conserve son bouton de retour à l'accueil (icône flèche).
- Nouveau formulaire « Clé PRIM » dans `/admin` : saisie, remplacement et suppression de la clé, sans jamais la réafficher (statut « configurée / non configurée » seulement).
- La clé est stockée en clair en base (nouvelle entité `PrimSetting`, ligne unique) et lue à chaque appel : prise en compte à chaud, sans redémarrage.
- **BREAKING** : la variable `ASSISTANT_PRIM_API_KEY` et la propriété `assistant.prim.api-key` sont supprimées (code, `application*.properties`, `docker-compose.yml`, README, tests).
- Aucun appel PRIM, et aucune consommation du quota journalier, tant qu'aucune clé n'est enregistrée.
- La table `prim_setting` est incluse dans la sauvegarde et la restauration. Un ancien dump sans cette table reste restaurable : la clé est alors vidée.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `ui-theme`: le caractère « sans défilement » est limité à l'accueil ; `/configuration` et `/admin` défilent.
- `app-framework`: la page de configuration peut défiler et garde son bouton de retour à l'accueil.
- `app-transit`: la clé PRIM vient de l'administration, plus d'une variable d'environnement ; pas d'appel sans clé.
- `admin-management`: nouveau formulaire de gestion de la clé PRIM.
- `data-backup`: la clé PRIM est sauvegardée ; un dump sans `prim_setting` est accepté.

## Impact

- CSS : `home.css` (sélecteurs `html, body`, `.configuration-page`, `.configuration-grid`), `configuration.html`, `index.html`.
- Java : nouveaux `PrimSetting`, `PrimSettingRepository`, `PrimKeyService` ; modifications de `AdminController`, `PrimClient`, `TransitService`, `AssistantProperties.Prim`, `BackupService`.
- Template : `admin/index.html`.
- Configuration et déploiement : `application*.properties`, `docker-compose.yml`, `README.md`, `CHANGELOG.md`, `docs/database.puml`. Aucune modification de la CI.
- Sécurité : la clé est en clair en base et dans le dump de sauvegarde ; l'avertissement de confidentialité de la sauvegarde doit le mentionner.
