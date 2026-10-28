## Why

Les profils veulent voir en un coup d'oeil les alertes trafic des transports en commun d'Île-de-France sur leurs trajets habituels. L'API PRIM d'Île-de-France Mobilités fournit ces informations, mais son quota (800 appels par jour) impose une consommation maîtrisée.

## What Changes

- Nouveau widget « Transports » : chaque profil sélectionne jusqu'à **4 lignes favorites**, sans configuration de gare, et voit les alertes de ces lignes.
- Catalogue local des lignes lu depuis `docs/referentiel-des-lignes.json`, embarqué dans le JAR et utilisé sans appel réseau.
- Client PRIM pour l'endpoint global « Messages Info Trafic » (clé API en variable d'environnement), réponse complète actualisée toutes les **10 min** et filtrée localement par identifiant de ligne; date de dernière actualisation affichée dans le widget.
- Mode TEST activable depuis l'administration : les appels météo et PRIM sont remplacés par des réponses locales, dont la réponse PRIM de `docs/response_info-traffic.json`; aucun appel externe ni compteur de quota n'est consommé.
- Garde-fou de quota : budget journalier **persisté en base** (jamais dépassé, réservation atomique avant l'appel), repli sur la dernière valeur connue marquée avec son heure.
- Pause d'appels PRIM de **22 h à 5 h** (heure de Paris), configurable : aucun appel PRIM, avec un message de pause. Le catalogue local reste consultable.
- Proxy HTTP/HTTPS optionnel appliqué à **tous les appels HTTP sortants** de l'application (PRIM et Open-Meteo), configuré **uniquement depuis l'interface d'administration** (aucune variable d'environnement), avec un bouton « Tester la connexion » ; réglages stockés en base (mot de passe en clair, réseau LAN), modifiables à chaud et exclus de la sauvegarde.
- Nouvelles tables `external_api_quota`, `proxy_setting` et `external_api_mode` (mode TEST), compatibles SQLite et PostgreSQL et exclues de la sauvegarde admin.
- Les alertes de chaque ligne favorite sont réécrites à intervalle aléatoire de **15 à 60 s**, avec un curseur clignotant en fin de texte, comme les annonces.

## Capabilities

### New Capabilities
- `app-transit`: widget transports IDFM : lignes favorites (4 max), alertes trafic globales filtrées localement, catalogue local, mode TEST, cache, quota persisté et pause nocturne.
- `outbound-http-proxy`: proxy HTTP/HTTPS pour tous les appels sortants, configuré depuis l'administration, test de connexion, exclu de la sauvegarde.

### Modified Capabilities

## Impact

- Backend : `TransitAssistantApp`, `TransitController`, `TransitService`, `TransitGateway`, `PrimClient`, routage LIVE/TEST des gateways météo et PRIM, `AssistantProperties`, proxy dynamique, `ExternalApiQuota`, `ExternalApiMode`, intégration de la réponse PRIM et du catalogue local, exclusions de sauvegarde.
- Frontend : fragment `fragments/widgets/transit.html`, `static/js/widgets/transit.js`, styles, configuration des lignes par profil et contrôles proxy/mode TEST dans l'administration.
- Configuration : variables `ASSISTANT_PRIM_API_KEY` et `ASSISTANT_PRIM_*` dans `application*.properties` et `docker-compose.yml` (valeurs injectées par Portainer/Arcane). Le proxy n'utilise aucune variable d'environnement.
- Packaging : le build Docker et Maven embarquent les fichiers canoniques `docs/referentiel-des-lignes.json` et `docs/response_info-traffic.json`, sans en créer de copies maintenues séparément.
- Base de données : trois nouvelles tables, à documenter dans `docs/database.puml` ; création par `ddl-auto`, sans outil de migration.
- Sauvegarde : `ExternalApiQuota`, `ProxySetting` et `ExternalApiMode` sont exclues de l'export et de l'import.
- Tests : JUnit 5 + Mockito (service, budget, pause, client, contrôleur, proxy) et tests JS.
- Documentation : `README.md`, `CHANGELOG.md`, Swagger sur les nouveaux endpoints.
- Aucune modification des pipelines CI/CD.
