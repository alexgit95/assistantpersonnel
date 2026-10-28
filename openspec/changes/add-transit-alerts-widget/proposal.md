## Why

Les profils veulent voir en un coup d'oeil les alertes trafic des transports en commun d'Île-de-France sur leurs trajets habituels. L'API PRIM d'Île-de-France Mobilités fournit ces informations, mais son quota (800 appels par jour) impose une consommation maîtrisée.

## What Changes

- Nouveau widget « Transports » : chaque profil enregistre jusqu'à **3 favoris**, chacun étant un couple **gare + ligne**, et voit les alertes trafic de la ligne qui concernent la gare.
- Nouveau client PRIM (clé API en variable d'environnement), une requête par ligne distincte, cache serveur de **15 min** par ligne, partagé entre profils.
- Garde-fou de quota : budget journalier **persisté en base** (jamais dépassé, réservation atomique avant l'appel), repli sur la dernière valeur connue marquée avec son heure.
- Pause d'appels de **22 h à 5 h** (heure de Paris), configurable : aucun appel PRIM, recherche bloquée avec un message de pause.
- Recherche de gares et de lignes avec cache de 24 h.
- Proxy HTTP/HTTPS optionnel appliqué à **tous les appels HTTP sortants** de l'application (PRIM et Open-Meteo), configuré **uniquement depuis l'interface d'administration** (aucune variable d'environnement), avec un bouton « Tester la connexion » ; réglages stockés en base (mot de passe en clair, réseau LAN), modifiables à chaud et exclus de la sauvegarde.
- Nouvelles tables `external_api_quota` (compteur d'appels) et `proxy_setting` (ligne unique), compatibles SQLite et PostgreSQL, toutes deux exclues de la sauvegarde admin.
- Le widget réutilise le cycle de réécriture et de rechargement aléatoire 15 à 60 s (`retype.js`).

## Capabilities

### New Capabilities
- `app-transit`: widget transports en commun IDFM : favoris gare + ligne, alertes trafic, cache, budget de quota persisté, pause nocturne.
- `outbound-http-proxy`: proxy HTTP/HTTPS pour tous les appels sortants, configuré depuis l'administration, test de connexion, exclu de la sauvegarde.

### Modified Capabilities

## Impact

- Backend : package `com.assistant.apps` (`TransitAssistantApp`, `TransitController`, `TransitService`, `TransitGateway`, `PrimClient`), `AssistantProperties` (bloc `prim`), réglages de proxy (entité `ProxySetting`, service, `ProxySelector` dynamique), page et formulaire dans `AdminController`, adaptation de `OpenMeteoClient`, entité `ExternalApiQuota` et son dépôt, exclusion des deux nouvelles entités dans `BackupService`.
- Frontend : fragment `fragments/widgets/transit.html`, `static/js/widgets/transit.js`, styles, formulaire proxy dans `templates/admin/index.html`.
- Configuration : variables `ASSISTANT_PRIM_API_KEY` et `ASSISTANT_PRIM_*` dans `application*.properties` et `docker-compose.yml` (valeurs injectées par Portainer/Arcane). Le proxy n'utilise aucune variable d'environnement.
- Base de données : deux nouvelles tables, à documenter dans `docs/database.puml` ; création par `ddl-auto`, sans outil de migration.
- Sauvegarde : `ExternalApiQuota` et `ProxySetting` sont exclues de l'export et de l'import.
- Tests : JUnit 5 + Mockito (service, budget, pause, client, contrôleur, proxy) et tests JS.
- Documentation : `README.md`, `CHANGELOG.md`, Swagger sur les nouveaux endpoints.
- Aucune modification des pipelines CI/CD.
