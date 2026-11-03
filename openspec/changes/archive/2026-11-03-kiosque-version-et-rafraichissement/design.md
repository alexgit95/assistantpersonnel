## Context

Le kiosque (`index.html` et `home.js`) charge la configuration au démarrage (`/api/config`), puis ses widgets à la connexion, à la déconnexion et à la restauration de session (`loadProfileApps`, via `GET /api/apps`). Rien ne relit la configuration ensuite : une modification faite depuis `/configuration` ou un smartphone n'apparaît qu'après un rechargement manuel, impossible en production. De même, après un déploiement, la page reste ouverte avec l'ancien JavaScript.

Contraintes relevées dans le code :

- Tous les réglages de widget (disposition, ville météo, lignes, arrêts, actualités) aboutissent dans `profile_app` (`saveApps` ou `ProfileAppService.updateConfiguration`), donc `GET /api/apps` suffit à les observer. Sans session, la même route renvoie la liste de l'invité.
- `capturePaused` dans `home.js` et `kiosk.capturing` signalent une capture d'enrôlement. Une session d'enrôlement peut être à l'état `WAITING` (QR invité affiché en permanence quand personne n'est connecté), `COUNTDOWN`, `CAPTURING` ou `REVIEW` (un smartphone a démarré), puis terminale (`DONE`, `CANCELLED`, `EXPIRED`).
- La clé de kiosque est en `sessionStorage`, qui survit à un `location.reload()`.
- `/js/**` et `/css/**` n'ont aucun `Cache-Control` explicite (seul `/vendor/**` est mis en cache 30 jours) : un navigateur peut les réutiliser par cache heuristique après un déploiement.
- Le `Dockerfile` ne copie pas `.git`, mais la CI passe déjà `GIT_COMMIT` (`github.sha`) comme variable d'environnement de l'image.

## Goals / Non-Goals

**Goals:**
- Afficher `version_du_pom-sha` en bas à droite de l'écran du kiosque.
- Relire la configuration des widgets au plus toutes les minutes et ré-afficher uniquement si elle a changé.
- Recharger la page quand une nouvelle version du serveur est détectée, sans jamais interrompre un enrôlement ni un utilisateur connecté.
- Empêcher toute boucle de rechargements.

**Non-Goals:**
- Rafraîchir ce qui n'est pas dans `profile_app` (catégories d'actualités de l'administrateur, annonces, clé PRIM, mode TEST) : ces widgets gardent leur propre cycle.
- Rendre l'intervalle configurable : il est fixe à 60 secondes.
- Mettre en place un système de notification temps réel (SSE, WebSocket).
- Modifier le `Dockerfile` ou la CI.

## Decisions

**1. Une seule chaîne de version, calculée côté serveur.**
Un composant `AppVersion` produit `version-commit` : la version vient de `BuildProperties` (goal `build-info` de `spring-boot-maven-plugin`), le SHA de la variable `GIT_COMMIT`, à défaut de `git.properties` (`GitProperties`, généré par `git-commit-id-maven-plugin` avec `failOnNoGitDirectory=false` pour ne pas casser le build Docker), à défaut de `dev`. Le SHA est réduit à 7 caractères. Sans `BuildProperties` (IDE, tests), la version de repli est `0`. *Alternative écartée* : filtrer `application.properties` avec `@project.version@` ; le filtrage de ressources du parent est désactivé par les `<resources>` personnalisés du `pom.xml`, `build-info` est plus simple.

**2. Exposition par `/api/config` et par la page rendue.**
`ClientConfig` gagne le champ `appVersion`. `HomeController` ajoute aussi la même chaîne au modèle de `index`, qui la place dans `<meta name="assistant-version">` et dans le texte affiché. La version de la page est donc celle avec laquelle le HTML a été servi, pas celle lue au démarrage du script. *Alternative écartée* : comparer à la valeur lue par `/api/config` au démarrage ; si un cache servait un vieux script mais un `/api/config` neuf, on ne détecterait jamais le décalage.

**3. Affichage superposé.**
Un `<p id="app-version" class="app-version">` dans `index.html`, en `position: fixed` en bas à droite, petit, grisé (`--assistant-muted`), `pointer-events: none`, sous l'écran de capture et au-dessus de la grille, sans réserver de place. Affiché sur l'écran d'accueil uniquement.

**4. Une vérification unique d'une minute dans un module pur.**
Un module `kiosk-refresh.js` (sans DOM, testable avec `node --test`) reçoit ses dépendances (lecture de config, lecture des applications, état « occupé », état « connecté », rechargement, stockage) et expose `tick()`. `home.js` l'appelle toutes les 60 secondes (`setInterval`, constante fixe). Un tick fait, dans l'ordre : sortie immédiate si occupé, comparaison de la version, puis comparaison des applications. Deux appels réseau par minute au maximum, sans nouvelle route serveur.

**5. Comparaison de la configuration par signature.**
`loadProfileApps` mémorise la signature (`JSON.stringify`) de la liste affichée ; le tick relit `GET /api/apps` et, si la signature diffère, rappelle `loadProfileApps`. Les `configJson` sont normalisés à l'écriture (`ProfileAppService.validateConfig`), donc la signature est stable. Une erreur réseau ou serveur conserve l'affichage et est retentée à la minute suivante.

**6. Conditions d'exclusion.**
Le tick ne fait rien (ni ré-affichage ni rechargement) si l'onglet est masqué, si une connexion ou une déconnexion est en cours, ou si une session d'enrôlement est dans l'état `COUNTDOWN`, `CAPTURING` ou `REVIEW`. L'état `WAITING` (QR invité en attente) n'est pas bloquant, sinon l'écran invité n'accepterait jamais de rechargement. Le rechargement de page exige en plus qu'aucun profil ne soit connecté (option B) : l'écran invité ou le moment d'inactivité de la session faciale (expiration après 10 minutes sans visage) suffit.

**7. Garde-fou anti-boucle.**
Une version serveur vide ou égale à celle de la page ne déclenche rien. Avant de recharger, la page écrit la version serveur visée dans `sessionStorage` (`assistant.reloaded-for`) ; si elle y est déjà, elle ne recharge pas une seconde fois pour cette version. `sessionStorage` survit au rechargement, ce qui couvre le cas où un cache servirait encore un ancien HTML.

**8. Revalidation des ressources statiques.**
`WebConfig` sert `/js/**` et `/css/**` avec `Cache-Control: no-cache` (revalidation conditionnelle à chaque chargement, via l'ETag ou `Last-Modified`) pour qu'un rechargement après déploiement récupère le nouveau code. `/vendor/**` garde son cache de 30 jours.

## Risks / Trade-offs

- [Un rechargement interrompt un enrôlement qui vient de démarrer] → l'enrôlement bloque toute action tant que la session n'est pas `DONE`, `CANCELLED` ou `EXPIRED`, et un nouveau tick réessaie ensuite.
- [Faux positif de la comparaison de configuration, ré-affichage inutile] → signature normalisée côté serveur ; au pire un ré-affichage par changement réel ou par perte de session.
- [Perte de session côté serveur : `/api/apps` renvoie la liste de l'invité alors que le kiosque se croit connecté] → la liste diffère, le kiosque ré-affiche l'invité ; la machine d'état bascule au prochain heartbeat 401. Aucun état durable n'est perdu.
- [Ré-affichage des widgets : animation d'écriture rejouée, cycles de rafraîchissement relancés] → seulement quand la configuration a réellement changé ; les anciens widgets se désactivent d'eux-mêmes (`root.isConnected`) et PRIM reste protégé par son cache de 55 s, son budget et sa pause.
- [Version de repli `dev` identique pour deux builds locaux différents] → aucun rechargement entre eux ; sans conséquence en développement, où la page se recharge à la main.
- [`no-cache` augmente les requêtes conditionnelles sur `/js` et `/css`] → négligeable sur un LAN ; réponses 304.
- [Le plugin `git-commit-id-maven-plugin` échoue sans dépôt Git (build Docker)] → `failOnNoGitDirectory=false` et `failOnUnableToExtractRepoInfo=false` ; la variable `GIT_COMMIT` prend alors le relais.

## Migration Plan

Aucune migration de données. Au déploiement, un kiosque déjà ouvert sur l'ancienne version ne contient pas encore la vérification d'une minute : il faut un rechargement manuel unique pour charger le nouveau code. Les déploiements suivants se propagent seuls. Retour arrière : redéployer l'image précédente ; le kiosque, sur une version différente, se rechargera quand personne n'est connecté.

## Open Questions

Aucune.
