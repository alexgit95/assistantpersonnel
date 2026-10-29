## 1. Défilement et retour

- [x] 1.1 Ajouter une classe `home-body` (et son pendant sur `html`) dans `index.html` et limiter `height:100dvh; overflow:hidden` à cette classe dans `home.css`
- [x] 1.2 Retirer `overflow:hidden` et la hauteur imposée de `.configuration-page` et adapter `.configuration-grid` pour un défilement vertical
- [x] 1.3 Vérifier que `/admin` défile et que l'accueil reste verrouillé, sur desktop et mobile
- [x] 1.4 Vérifier la visibilité et l'accessibilité du bouton icône de retour de `configuration.html`

## 2. Clé PRIM : stockage et service

- [x] 2.1 Créer l'entité `PrimSetting` (id fixe 1, `api_key` nullable) et `PrimSettingRepository`
- [x] 2.2 Créer `PrimKeyService` : `isConfigured()`, `currentKey()`, `save(String)`, `clear()`, sans cache
- [x] 2.3 Retirer `apiKey` et `configured()` de `AssistantProperties.Prim` et supprimer `assistant.prim.api-key` des `application*.properties`
- [x] 2.4 Brancher `PrimClient` sur `PrimKeyService` (exception `TransitUnavailableException` sans clé)
- [x] 2.5 Brancher `TransitService` sur `PrimKeyService` : garde avant tout appel et avant la réservation du quota, y compris l'actualisation planifiée

## 3. Administration

- [x] 3.1 Ajouter les endpoints `/admin/prim` et `/admin/prim/delete` dans `AdminController` avec CSRF, annotations Swagger et message `notice`
- [x] 3.2 Ajouter le formulaire « Clé PRIM » dans `admin/index.html` avec statut configurée / non configurée, sans réafficher la clé
- [x] 3.3 Mettre à jour le texte de confidentialité de la sauvegarde pour mentionner la clé PRIM

## 4. Sauvegarde

- [x] 4.1 Vérifier que `PrimSetting` n'est pas dans `EXCLUDED_FROM_BACKUP` et que l'export la contient
- [x] 4.2 Autoriser dans `BackupService.validate` l'absence de la table `prim_setting` dans le fichier (traitée comme vide) sans assouplir les autres contrôles

## 5. Tests

- [x] 5.1 Tests de `PrimKeyService` : enregistrement, remplacement, champ vide conserve, suppression
- [x] 5.2 Tests de `PrimClient` et `TransitService` : aucun appel et aucun quota consommé sans clé, appel avec clé, prise en compte à chaud
- [x] 5.3 Tests de `AdminController` : accès sans session admin, CSRF, message de statut sans fuite de la clé
- [x] 5.4 Tests de `BackupService` : export contenant `prim_setting`, restauration de la clé, ancien dump sans la table, rejet d'une autre table manquante
- [x] 5.5 Mettre à jour les tests existants qui construisent `AssistantProperties.Prim` ou lisent la clé
- [x] 5.6 Lancer `mvn clean test` avec le Maven de `C:\USINE_LOGICIELLE\apache-maven\bin`

## 6. Documentation et déploiement

- [x] 6.1 Retirer `ASSISTANT_PRIM_API_KEY` de `docker-compose.yml` et du tableau du README
- [x] 6.2 Documenter dans le README la saisie de la clé depuis `/admin`, son inclusion dans la sauvegarde et le défilement des pages
- [x] 6.3 Mettre à jour `docs/database.puml` avec `prim_setting`
- [x] 6.4 Mettre à jour `CHANGELOG.md` (Added, Changed, Fixed, mention BREAKING de la variable supprimée)
