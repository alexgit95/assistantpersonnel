## 1. Configuration et proxy

- [x] 1.1 Ajouter le bloc `prim` à `AssistantProperties` (validation, valeurs par défaut) et aux fichiers `application*.properties`
- [x] 1.2 Exposer `ASSISTANT_PRIM_*` dans `docker-compose.yml` sans valeur secrète
- [x] 1.3 Créer l'entité `ProxySetting` (ligne unique) et son dépôt
- [x] 1.4 Créer `ProxySettingsService` : validation d'URL, mot de passe conservé si champ vide, configuration courante en référence volatile
- [x] 1.5 Installer le `ProxySelector` et l'`Authenticator` par défaut dynamiques (exclusions, identifiants, authentification Basic en HTTPS)
- [x] 1.6 Vérifier que `OpenMeteoClient` utilise le proxy par défaut et conserve ses délais
- [x] 1.7 Ajouter dans `AdminController` le formulaire `/admin/proxy` et le test `/admin/proxy/test` (client de test dédié, sans budget PRIM)
- [x] 1.8 Ajouter la section « Proxy » dans `templates/admin/index.html` (mot de passe jamais réaffiché, bouton « Tester la connexion »)
- [x] 1.9 Exclure `ProxySetting` et `ExternalApiQuota` de l'export et de l'import dans `BackupService`
- [x] 1.10 Tests JUnit : validation d'URL, mot de passe conservé, application à chaud, exclusion, test de connexion, accès admin, sauvegarde sans proxy

## 2. Budget et pause

- [x] 2.1 Créer l'entité `ExternalApiQuota` et son dépôt avec réservation atomique (SQL compatible SQLite/PostgreSQL)
- [x] 2.2 Mettre à jour `docs/database.puml` (tables `external_api_quota` et `proxy_setting`)
- [x] 2.3 Créer `QuietHours` (fenêtre chevauchant minuit, heure de Paris, `Clock` injectable)
- [x] 2.4 Purger les jours de quota de plus de 7 jours
- [x] 2.5 Tests : budget atomique et concurrent, persistance, remise à zéro du jour, pause, changement d'heure

## 3. Client et service PRIM

- [x] 3.1 Confirmer l'API PRIM et fournir le Swagger ainsi qu'une réponse exemple
- [x] 3.2 Créer `TransitGateway`, `PrimClient` pour l'endpoint global (en-tête `apikey`, délais, erreurs traduites) et `TransitUnavailableException`
- [x] 3.3 Créer `TransitService` : actualisation planifiée de la réponse globale toutes les 10 min entre 5 h et 22 h, fusion des appels concurrents, valeur de repli horodatée, filtrage par ligne et tri par sévérité
- [x] 3.4 Charger le catalogue local `docs/referentiel-des-lignes.json`, exposer les lignes actives et l'embarquer dans le JAR et l'image Docker
- [x] 3.5 Valider et enregistrer jusqu'à 4 lignes actives sans doublon dans la configuration du profil
- [x] 3.6 Ajouter le mode TEST persistant dans l'administration : servir `docs/response_info-traffic.json` pour PRIM et des données météo locales, sans appeler les API externes ni réserver de quota
- [x] 3.7 Ajouter un indicateur MODE TEST sur les widgets; exclure le réglage de la sauvegarde et mettre à jour le PlantUML
- [x] 3.8 Tests JUnit/Mockito : vérifier que le mode TEST sert `docs/response_info-traffic.json` sans appel PRIM ni réservation de quota, ainsi que le mode live, la pause, l'actualisation globale à 10 min, l'horodatage, le filtrage de ligne, le titre de repli et le champ `message`

## 4. API et application

- [x] 4.1 Créer `TransitAssistantApp` (clé `transit`, fragment, module JS)
- [x] 4.2 Créer `TransitController` (`lines`, `favorites`, `alerts`) avec annotations Swagger et sécurité par session
- [x] 4.3 Tests du contrôleur : codes 200, 400, 404, 500 et isolation par profil

## 5. Interface

- [x] 5.1 Créer `fragments/widgets/transit.html` et les styles (pastille de ligne, alertes mises en avant selon sévérité, détails `message`, pause, date de mise à jour, curseur clignotant)
- [x] 5.2 Créer `static/js/widgets/transit.js` avec `startRetypeLoop` de `retype.js` (un cycle indépendant par ligne favorite), réécriture du titre, du résumé et des détails par `typeText(..., { restart: true })`, et style partagé du curseur des annonces; respecter `prefers-reduced-motion` et le réglage d'écriture
- [x] 5.3 Afficher dans le widget la date et l'heure de la dernière actualisation PRIM réussie, en heure de Paris, en conservant l'horodatage des données de repli
- [x] 5.4 Ajouter la configuration des lignes favorites depuis le catalogue local (recherche, limite de 4)
- [x] 5.5 Ajouter l'appel `api.transit*` dans `api.js` et les tests JS correspondants (rendu des détails, réécriture, curseur, horodatage)

## 6. Documentation et validation

- [x] 6.1 Mettre à jour `README.md` (fonctionnement, variables PRIM, quota, pause, mode TEST, catalogue et proxy admin)
- [x] 6.2 Augmenter la version du projet dans `pom.xml` et ajouter simultanément l'entrée correspondante dans `CHANGELOG.md` (fait manuellement)
- [x] 6.3 Exécuter les tests Maven avec `C:\USINE_LOGICIELLE\apache-maven\bin` et les tests JS
- [x] 6.4 Valider la change OpenSpec
