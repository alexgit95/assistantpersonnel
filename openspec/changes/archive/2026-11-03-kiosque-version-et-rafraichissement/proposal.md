## Why

En production, personne ne peut recharger manuellement la page du kiosque : une modification faite dans l'onglet de configuration (emplacement, réglage d'un widget) n'apparaît pas à l'écran, et après un déploiement le kiosque continue d'exécuter l'ancien JavaScript face au nouveau serveur. De plus, rien n'indique à l'écran quelle version de l'application tourne, ce qui complique le support.

## What Changes

- L'écran du kiosque affiche en bas à droite, en texte discret superposé, la version de l'application au format `version_du_pom-sha` (SHA court de 7 caractères, par exemple `1.1.1-a1b2c3d`) ; sans SHA connu, le texte devient `1.1.1-dev`.
- La version est exposée par `GET /api/config` (nouveau champ `appVersion`) et intégrée à la page d'accueil servie. La version du pom est fournie par `build-info` ; le SHA vient de la variable `GIT_COMMIT` (image Docker), à défaut de `git.properties` généré au build (modes `dev` et `onprem`).
- Une fois par minute (intervalle fixe), le kiosque relit `GET /api/apps` et, si la liste diffère de celle affichée, ré-affiche ses widgets : disposition, réglages de chaque widget et écran invité sont pris en compte sans rechargement manuel.
- Une fois par minute également, le kiosque compare la version du serveur à celle qu'il a chargée ; si elle diffère, il recharge la page, mais seulement quand personne n'est connecté.
- Ni le ré-affichage de la configuration ni le rechargement de page ne s'exécutent pendant une capture d'enrôlement, pendant une connexion ou une déconnexion en cours, ni quand l'onglet est masqué ; ils sont retentés à la minute suivante.
- Les fichiers `/js/**` et `/css/**` sont servis avec `Cache-Control: no-cache` (revalidation à chaque chargement) pour qu'un rechargement après déploiement récupère bien le nouveau code.
- Garde-fou contre une boucle de rechargements : une version inconnue ou identique des deux côtés ne déclenche rien, et la page n'est rechargée qu'une seule fois pour une version serveur donnée.

## Capabilities

### New Capabilities

- `app-version`: version de l'application (version du pom et SHA du commit) exposée par `/api/config` et affichée en bas à droite de l'écran du kiosque.
- `kiosk-auto-refresh`: vérification minute par minute de la configuration des widgets et de la version du serveur par le kiosque, avec ré-affichage ou rechargement de page, conditions d'exclusion et garde-fou anti-boucle.

### Modified Capabilities

Aucune : les exigences existantes ne changent pas, les comportements sont ajoutés par de nouvelles capacités.

## Impact

- Code serveur : `pom.xml` (goal `build-info`, génération de `git.properties`), `ClientConfigController` (champ `appVersion`), composant de résolution de la version et du SHA (variable `GIT_COMMIT`, `git.properties`, repli `dev`), `HomeController` (version dans la page), `WebConfig` (revalidation de `/js/**` et `/css/**`).
- Code navigateur : `home.js` (vérification d'une minute, ré-affichage, rechargement), `kiosk.js` (état de capture), `templates/index.html` et `home.css` (texte de version superposé), `api.js` si besoin.
- API : `GET /api/config` gagne un champ `appVersion` (changement compatible).
- Build et déploiement : le `Dockerfile` et la CI sont inchangés ; l'image utilise déjà `GIT_COMMIT`.
- Tests : tests Java de l'exposition de la version et de la résolution du SHA, tests JS de la logique de vérification (extraite en module pur).
- Documentation : `README.md`, `CHANGELOG.md`. Aucun impact sur la base de données ni sur `docs/database.puml`.
