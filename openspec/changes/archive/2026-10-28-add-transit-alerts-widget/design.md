## Context

Le projet suit un gabarit pour les widgets externes : `AssistantApp` + `Gateway` + `Service` avec cache + `Controller`, configuration JSON par profil dans `profile_app` (météo : `WeatherService`, `OpenMeteoClient`). Le Swagger fourni identifie l'endpoint global `GET /marketplace/disruptions_bulk/disruptions/v2`, avec clé en en-tête `apikey`; l'exemple `docs/response_info-traffic.json` contient un tableau `disruptions` dont certaines entrées ont `impactedSections[].lineId`. Le référentiel `docs/referentiel-des-lignes.json` contient les identifiants correspondants. Cible : Raspberry Pi 4 (4 Go), SQLite en local, PostgreSQL en production.

## Goals / Non-Goals

**Goals:**
- Jusqu'à 4 lignes favorites par profil, réponse PRIM globale actualisée toutes les 10 min, budget persisté et pause 22 h à 5 h.
- Mode TEST admin persistant qui substitue des fixtures aux appels de données météo et PRIM.
- Catalogue local issu de `docs/referentiel-des-lignes.json`, embarqué sans copie maintenue séparément.
- Proxy optionnel pour tous les appels HTTP sortants.
- Réutiliser les patterns existants (gateway, cache LRU borné, config JSON par profil, `retype.js`).

**Non-Goals:**
- Calcul d'itinéraire ou horaires en temps réel.
- Alertes planifiées futures (seules les perturbations en cours sont affichées).
- Recherche de gares ou filtrage par gare.
- Modification des pipelines CI/CD.

## Decisions

- **Gateway globale PRIM** : `TransitGateway` expose la réponse globale de perturbations et `PrimClient` appelle `/marketplace/disruptions_bulk/disruptions/v2`. Les erreurs sont traduites en `TransitUnavailableException`.
- **Mode TEST** : une option d'administration persistée choisit entre gateways réelles et simulées pour PRIM et météo. Les fixtures chargées depuis les fichiers canoniques de `docs/` sont transformées une seule fois en DTO compacts; le mode TEST n'effectue ni appel externe ni réservation de quota. L'état TEST est exclu des sauvegardes.
- **Favoris dans `profile_app.config_json`** : `{"favoriteLineIds":["C01739"]}`. Aucune table de favoris : validation côté serveur (max 4, identifiants actifs du référentiel, pas de doublon) avant écriture.
- **Catalogue de lignes** : parser `docs/referentiel-des-lignes.json`, retenir les lignes actives et les champs utiles (`id_line`, noms, mode et couleurs). Le build Docker copie ce fichier et Maven l'embarque comme ressource, sans dupliquer sa source canonique. Le navigateur peut rechercher localement dans le catalogue; aucun appel externe n'est nécessaire.
- **Actualisation PRIM globale** : une seule requête planifiée toutes les 10 min pendant la plage 5 h–22 h (heure de Paris), avec une première récupération à la demande si aucun instantané n'existe. Le budget persistant autorise jusqu'à 102 appels planifiés par jour, très inférieur au plafond configuré de 750. Un instantané global compact est partagé par tous les profils et conserve `fetchedAt` pour l'affichage et le repli; les requêtes simultanées sont fusionnées. Les alertes sont filtrées localement par égalité exacte de `impactedSections[].lineId`; si `impactedSections` est absent, seul un préfixe exact du titre peut servir de repli, jamais une recherche libre dans le HTML.
- **Budget persisté dans `external_api_quota`** : clé primaire naturelle `id = "<api_name>:<date ISO de Paris>"`, plus `api_name`, `quota_day` et `calls`. L'identifiant unique fonctionne sous SQLite et PostgreSQL et évite les doublons en concurrence. Réservation atomique par `UPDATE ... SET calls = calls + 1 WHERE api_name = ? AND quota_day = ? AND calls < ?`, avec création initiale à zéro si la ligne du jour n'existe pas. Le budget configurable par défaut est 750 (marge de 50 sous le quota).
- **Pause nocturne** : composant `QuietHours` basé sur une `Clock` injectable et `Europe/Paris`. Pendant la pause : aucun appel PRIM et message avec dernière valeur connue; le catalogue local reste disponible.
- **Proxy global configuré depuis l'administration** : un `ProxySelector` et un `Authenticator` par défaut sont installés au démarrage et lisent à chaque requête la configuration courante, tenue dans une référence volatile rafraîchie à chaque enregistrement. Un `HttpClient` appelle `select(uri)` à chaque requête, donc une modification s'applique sans redémarrage ni reconstruction des clients. Il faut lever `jdk.http.auth.tunneling.disabledSchemes` pour autoriser l'authentification Basic sur HTTPS. Aucune variable d'environnement n'alimente le proxy, il n'y a donc pas de valeur de départ. Alternative écartée : configurer le proxy dans chaque client, source d'oubli pour les futurs clients.
- **Entité `ProxySetting` à ligne unique** : colonnes `id`, `enabled`, `url`, `username`, `password` (stocké en clair, usage LAN), `no_proxy` (liste d'hôtes). Table dédiée plutôt qu'une table clé/valeur générique, pour rester lisible et simple à exclure de la sauvegarde.
- **Formulaire admin** : section « Proxy » dans `/admin` (POST `/admin/proxy`, protection CSRF et authentification admin existantes). Le mot de passe n'est jamais réécrit dans la page ; un champ vide conserve la valeur. L'URL est validée à l'enregistrement (schéma http ou https, hôte, port) et une valeur invalide est refusée sans toucher à la configuration active.
- **Test de connexion** : POST `/admin/proxy/test` exécute un appel de test via la configuration saisie, sans l'enregistrer, vers un point Open-Meteo public (pas de consommation du budget PRIM), avec délais bornés. Le résultat est affiché sans divulguer les identifiants. Le test utilise un `HttpClient` dédié construit avec un `ProxySelector` et un `Authenticator` propres à cet appel, pour ne pas dépendre de la configuration active.
- **Exclusion de la sauvegarde** : `BackupService` exclut `AppSession`, `ProxySetting`, `ExternalApiQuota` et `ExternalApiMode` de l'export et de l'import ; une restauration ne remplace donc pas ces réglages globaux.
- **Persistance du mode TEST** : table singleton `external_api_mode`, exclue avec les réglages de proxy et le quota de la sauvegarde; état par défaut LIVE.
- **Configuration** : seul le bloc `prim` (clé, budget, TTL, pause) est ajouté à `AssistantProperties`, avec validation et valeurs par défaut dans `application.properties`, variables exposées dans `docker-compose.yml` sans valeur secrète.
- **Frontend** : chaque ligne favorite possède son propre timer aléatoire de réécriture entre 15 et 60 s, calqué sur les annonces. Le titre, le résumé et les détails développés du champ `message` sont réécrits ensemble avec un curseur clignotant et respectent `prefers-reduced-motion`/le réglage d'écriture. Le HTML PRIM est converti en texte par `DOMParser` puis inséré comme texte, jamais comme HTML exécutable. Les lignes perturbées sont mises en avant selon la sévérité. Ces réécritures ne déclenchent pas d'appel PRIM supplémentaire.
- **API REST** : `GET /api/transit/lines`, `GET/PUT /api/transit/favorites`, `GET /api/transit/alerts`, tous protégés par session, avec annotations Swagger (200, 400, 404, 500).

## Risks / Trade-offs

- [Certaines perturbations n'ont pas de `impactedSections`] → correspondance principale par identifiant structuré, repli limité au préfixe exact du titre.
- [Fixtures et catalogue volumineux] → charger/transformer une fois et ne conserver que des DTO compacts; le fichier source reste la référence versionnée.
- [Dépassement de quota en cas de bug] → réservation atomique persistée et budget inférieur au quota.
- [Données d'alerte jusqu'à 10 min d'âge] → affichage de la date et de l'heure exactes de dernière actualisation.
- [Proxy global invasif] → validation stricte à l'enregistrement, exclusions, délais explicites, test de connexion avant usage, tests unitaires.
- [Mauvais proxy qui coupe météo et transports] → la configuration peut être désactivée depuis l'administration, qui n'utilise pas de trafic sortant.
- [Mot de passe du proxy en clair en base] → accepté (réseau local), jamais réaffiché ni journalisé, exclu de la sauvegarde.
- [Table de quota qui grossit] → purge des jours de plus de 7 jours à chaque nouvelle journée.

## Open Questions

- PRIM compte-t-il le quota en UTC ou en heure de Paris ? Sans effet avec la pause nocturne par défaut, à vérifier si elle est désactivée.
