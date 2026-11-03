## 1. Version de l'application (serveur)

- [x] 1.1 `pom.xml` : ajouter le goal `build-info` à `spring-boot-maven-plugin` et le plugin `git-commit-id-maven-plugin` (génération de `git.properties`) avec `failOnNoGitDirectory=false` et `failOnUnableToExtractRepoInfo=false` ; vérifier que le build Docker (sans `.git`) reste possible
- [x] 1.2 Créer le composant `AppVersion` : version issue de `BuildProperties` (repli `0`), SHA issu de la variable `GIT_COMMIT`, à défaut de `GitProperties`, à défaut de `dev`, réduit à 7 caractères ; chaîne composée `version-commit`
- [x] 1.3 `ClientConfigController` : ajouter le champ `appVersion` à `ClientConfig` et à la documentation Swagger
- [x] 1.4 `HomeController` : ajouter la version au modèle de la vue `index`
- [x] 1.5 `WebConfig` : servir `/js/**` et `/css/**` avec `Cache-Control: no-cache` sans modifier le cache de `/vendor/**`

## 2. Tests Java de la version

- [x] 2.1 Tests unitaires de `AppVersion` : SHA de `GIT_COMMIT` tronqué à 7 caractères, repli sur `git.properties`, repli sur `dev`, version de repli sans `BuildProperties`
- [x] 2.2 `ClientConfigController` : `appVersion` présent dans `/api/config` ; test d'intégration ou MockMvc des en-têtes `Cache-Control` de `/js/**`, `/css/**` et `/vendor/**`
- [x] 2.3 Test MockMvc de la page d'accueil : la version est présente dans la page rendue
- [x] 2.4 Point de contrôle : `mvn clean test` vert (Maven de `C:\USINE_LOGICIELLE\apache-maven\bin`)

## 3. Affichage de la version (navigateur)

- [x] 3.1 `templates/index.html` : ajouter `<meta name="assistant-version">` et le texte `#app-version` en bas à droite
- [x] 3.2 `home.css` : style `.app-version` (position fixe, petit, grisé, `pointer-events: none`, sous l'écran de capture, au-dessus de la grille)

## 4. Vérification périodique du kiosque

- [x] 4.1 Créer `kiosk-refresh.js` (module pur, testable avec `node --test`) : `tick()` qui applique les conditions d'exclusion, compare la version, recharge si personne n'est connecté, compare la signature des applications et ré-affiche si elle diffère ; garde-fou `sessionStorage` `assistant.reloaded-for`
- [x] 4.2 `home.js` : mémoriser la signature de la liste affichée dans `loadProfileApps`, exposer l'état « connexion ou déconnexion en cours » et l'état d'enrôlement (`COUNTDOWN`, `CAPTURING`, `REVIEW`) à `kiosk-refresh.js`, lire la version de la page dans `<meta name="assistant-version">`, démarrer le `setInterval` de 60 secondes (constante fixe)
- [x] 4.3 `kiosk.js` : exposer l'état de la session d'enrôlement courante pour que `WAITING` ne bloque pas et que `COUNTDOWN`, `CAPTURING` et `REVIEW` bloquent
- [x] 4.4 Tests JS `kiosk-refresh.test.mjs` : configuration inchangée (aucun ré-affichage), changée (ré-affichage), échec de lecture (affichage conservé), onglet masqué, enrôlement actif, enrôlement `WAITING`, connexion en cours, version différente avec personne connecté (rechargement), version différente avec un profil connecté (différé), versions identiques ou vide (rien), pas de second rechargement pour la même version ; lancer `node --test src/test/js/*.test.mjs`

## 5. Documentation et finalisation

- [x] 5.1 Mettre à jour le `README.md` (version affichée en bas à droite, sources du SHA par mode, rafraîchissement automatique de la configuration chaque minute, rechargement sur nouvelle version quand personne n'est connecté, exclusion de l'enrôlement)
- [x] 5.2 Mettre à jour le `CHANGELOG.md` (`[Added]`, `[Changed]`, `[Fixed]`) avec l'impact technique et fonctionnel
- [x] 5.3 Vérifier à l'écran : version visible sans masquer un widget de façon gênante, modification de configuration prise en compte en moins d'une minute, enrôlement non perturbé, rechargement après une nouvelle version seulement quand personne n'est connecté
- [x] 5.4 Vérifier qu'aucune modification de `docs/database.puml` n'est nécessaire et lancer `openspec validate kiosque-version-et-rafraichissement`
