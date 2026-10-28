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

- [ ] 3.1 Confirmer l'API PRIM souscrite et enregistrer des réponses d'exemple pour les tests
- [ ] 3.2 Créer `TransitGateway`, `PrimClient` (en-tête `apikey`, délais, erreurs traduites) et `TransitUnavailableException`
- [ ] 3.3 Créer `TransitService` : cache LRU borné de 15 min par ligne, fusion des appels concurrents, valeur de repli horodatée, filtrage par gare, tri par sévérité
- [ ] 3.4 Ajouter la recherche de gares et lignes avec cache de 24 h, limite de longueur, refus pendant la pause
- [ ] 3.5 Valider et enregistrer les favoris (3 max, champs obligatoires, doublons) dans la configuration du profil
- [ ] 3.6 Tests JUnit/Mockito du service et du client (clé absente, indisponibilité, budget épuisé, pause, filtrage)

## 4. API et application

- [ ] 4.1 Créer `TransitAssistantApp` (clé `transit`, fragment, module JS)
- [ ] 4.2 Créer `TransitController` (`search`, `favorites`, `alerts`) avec annotations Swagger et sécurité par session
- [ ] 4.3 Tests du contrôleur : codes 200, 400, 404, 500 et isolation par profil

## 5. Interface

- [ ] 5.1 Créer `fragments/widgets/transit.html` et les styles (pastille de ligne, sévérité, message de pause)
- [ ] 5.2 Créer `static/js/widgets/transit.js` avec boucle `retype.js` et affichage de la dernière mise à jour
- [ ] 5.3 Ajouter la configuration des favoris sur la page de configuration (recherche, limite de 3)
- [ ] 5.4 Ajouter l'appel `api.transit*` dans `api.js` et les tests JS correspondants

## 6. Documentation et validation

- [ ] 6.1 Mettre à jour `README.md` (fonctionnement, variables PRIM, quota, pause, configuration du proxy dans l'administration) et `CHANGELOG.md`
- [ ] 6.2 Exécuter les tests Maven avec `C:\USINE_LOGICIELLE\apache-maven\bin` et les tests JS
- [ ] 6.3 Valider la change OpenSpec
