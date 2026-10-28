## Context

Le projet suit un gabarit pour les widgets externes : `AssistantApp` + `Gateway` + `Service` avec cache + `Controller`, config JSON par profil dans `profile_app` (voir la météo : `WeatherService`, `OpenMeteoClient`). L'API PRIM d'Île-de-France Mobilités impose une clé (en-tête `apikey`) et un quota de 800 appels par jour. Cible : Raspberry Pi 4 (4 Go), SQLite en local, PostgreSQL en production. La documentation PRIM n'a pas pu être lue pendant l'exploration : le format exact des réponses (Navitia `line_reports` ou SIRI Lite) est à confirmer avec la clé existante.

## Goals / Non-Goals

**Goals:**
- Alertes par favori gare + ligne (3 max par profil) avec une requête par ligne, cache 15 min, budget persisté, pause 22 h à 5 h.
- Proxy optionnel pour tous les appels HTTP sortants.
- Réutiliser les patterns existants (gateway, cache LRU borné, config JSON par profil, `retype.js`).

**Non-Goals:**
- Calcul d'itinéraire ou horaires en temps réel.
- Alertes planifiées futures (seules les perturbations en cours sont affichées).
- Utilisation de l'API bulk de perturbations.
- Modification des pipelines CI/CD.

## Decisions

- **Interface `TransitGateway` + `PrimClient`** : comme `WeatherGateway`, ce qui permet de tester sans réseau avec des réponses enregistrées. Le client n'expose que `searchPlaces(query)` et `lineAlerts(lineId)` et traduit les erreurs en `TransitUnavailableException`.
- **Favoris dans `profile_app.config_json`** : `{"favorites":[{"stopId","stopName","lineId","lineName"}]}`. Aucune table de favoris : validation côté serveur (max 3, champs obligatoires, pas de doublon) avant écriture.
- **Cache des alertes par `lineId`** : LRU borné (comme `WeatherService`) avec TTL de 15 min et conservation de la dernière valeur avec son horodatage pour le repli. Fusion des appels concurrents par ligne (verrou par clé ou `CompletableFuture` en cours).
- **Filtrage par gare côté serveur** : la réponse d'une ligne est filtrée pour garder les perturbations qui touchent la gare du favori ou toute la ligne. La règle exacte dépend du format PRIM (voir questions ouvertes).
- **Budget persisté dans `external_api_quota`** : colonnes `api_key` (varchar), `quota_day` (date de Paris), `calls` (integer), unicité `(api_key, quota_day)`. Réservation atomique par `UPDATE ... SET calls = calls + 1 WHERE api_key = ? AND quota_day = ? AND calls < ?`, avec insertion initiale si la ligne n'existe pas ; SQL standard compatible SQLite et PostgreSQL. Alternative écartée : compteur en mémoire seul, perdu au redémarrage. Le budget configurable par défaut est 750 (marge de 50 sous le quota).
- **Pause nocturne** : composant `QuietHours` basé sur une `Clock` injectable et le fuseau `Europe/Paris`, fenêtre qui chevauche minuit. Pendant la pause : aucun appel, recherche refusée avec un message, valeur en cache servie. Avec la pause active, le jour de quota coupe dans la fenêtre de pause, donc le décalage éventuel avec le jour de reset de PRIM est sans effet.
- **Recherche** : mêmes règles que les alertes (pause, budget), cache 24 h borné, longueur de requête limitée.
- **Proxy global configuré depuis l'administration** : un `ProxySelector` et un `Authenticator` par défaut sont installés au démarrage et lisent à chaque requête la configuration courante, tenue dans une référence volatile rafraîchie à chaque enregistrement. Un `HttpClient` appelle `select(uri)` à chaque requête, donc une modification s'applique sans redémarrage ni reconstruction des clients. Il faut lever `jdk.http.auth.tunneling.disabledSchemes` pour autoriser l'authentification Basic sur HTTPS. Aucune variable d'environnement n'alimente le proxy, il n'y a donc pas de valeur de départ. Alternative écartée : configurer le proxy dans chaque client, source d'oubli pour les futurs clients.
- **Entité `ProxySetting` à ligne unique** : colonnes `id`, `enabled`, `url`, `username`, `password` (stocké en clair, usage LAN), `no_proxy` (liste d'hôtes). Table dédiée plutôt qu'une table clé/valeur générique, pour rester lisible et simple à exclure de la sauvegarde.
- **Formulaire admin** : section « Proxy » dans `/admin` (POST `/admin/proxy`, protection CSRF et authentification admin existantes). Le mot de passe n'est jamais réécrit dans la page ; un champ vide conserve la valeur. L'URL est validée à l'enregistrement (schéma http ou https, hôte, port) et une valeur invalide est refusée sans toucher à la configuration active.
- **Test de connexion** : POST `/admin/proxy/test` exécute un appel de test via la configuration saisie, sans l'enregistrer, vers un point Open-Meteo public (pas de consommation du budget PRIM), avec délais bornés. Le résultat est affiché sans divulguer les identifiants. Le test utilise un `HttpClient` dédié construit avec un `ProxySelector` et un `Authenticator` propres à cet appel, pour ne pas dépendre de la configuration active.
- **Exclusion de la sauvegarde** : `BackupService` parcourt aujourd'hui toutes les entités JPA sauf `AppSession`. L'exclusion devient une liste (`AppSession`, `ProxySetting`, `ExternalApiQuota`), appliquée à l'export comme à l'import ; l'import ne touche pas ces tables.
- **Configuration** : seul le bloc `prim` (clé, budget, TTL, pause) est ajouté à `AssistantProperties`, avec validation et valeurs par défaut dans `application.properties`, variables exposées dans `docker-compose.yml` sans valeur secrète.
- **Frontend** : `transit.js` basé sur `retype.js` ; les données viennent du cache serveur, donc aucun surcoût PRIM. Affichage : pastille de ligne, alertes sévères d'abord, « Trafic normal », heure de dernière mise à jour, message de pause.
- **API REST** : `GET /api/transit/search`, `PUT /api/transit/favorites`, `GET /api/transit/alerts`, tous authentifiés par session, avec annotations Swagger (200, 400, 404, 500).

## Risks / Trade-offs

- [Format PRIM inconnu] → isoler dans `PrimClient`, tests sur réponses enregistrées, valider avec la clé réelle avant l'implémentation.
- [Dépassement de quota en cas de bug] → réservation atomique persistée et budget inférieur au quota.
- [Alertes datant de 15 min] → affichage de l'heure de mise à jour.
- [Proxy global invasif] → validation stricte à l'enregistrement, exclusions, délais explicites, test de connexion avant usage, tests unitaires.
- [Mauvais proxy qui coupe météo et transports] → la configuration peut être désactivée depuis l'administration, qui n'utilise pas de trafic sortant.
- [Mot de passe du proxy en clair en base] → accepté (réseau local), jamais réaffiché ni journalisé, exclu de la sauvegarde.
- [Table de quota qui grossit] → purge des jours de plus de 7 jours à chaque nouvelle journée.

## Open Questions

- Quelle API PRIM exacte est souscrite, et quel est son format de réponse (pour le filtrage par gare) ?
- PRIM compte-t-il le quota en UTC ou en heure de Paris ? Sans effet avec la pause nocturne par défaut, à vérifier si elle est désactivée.
