# Assistant personnel

Assistant web familial : la webcam identifie la personne (reconnaissance faciale **dans le navigateur**), puis affiche ses applications (poids, météo, ...). Réseau local uniquement. Spécification : `openspec/changes/initial-assistant/`.

## Développement local

Prérequis : Java 25 et Maven (`C:\USINE_LOGICIELLE\apache-maven\bin`). Le profil `local` (SQLite, fichier `assistant-local.db`) est actif par défaut.

```bash
SPRING_PROFILES_ACTIVE=local ASSISTANT_ADMIN_PASSWORD=local-admin-password ASSISTANT_RECOGNITION_DEBUG=true /c/USINE_LOGICIELLE/apache-maven/bin/mvn spring-boot:run
```

Dans Git Bash, lance cette commande depuis la racine du projet. `SPRING_PROFILES_ACTIVE=local` sélectionne SQLite au lieu de PostgreSQL. `ASSISTANT_ADMIN_PASSWORD` est requis pour démarrer et définit le mot de passe local de `/admin/login` (choisis un secret à toi). `ASSISTANT_RECOGNITION_DEBUG=true` affiche dans la page le moteur utilisé, les distances de reconnaissance et le temps de détection pour aider à calibrer les seuils ; retire cette variable pour un affichage normal. Ouvre ensuite `http://localhost:8080` sur le même appareil que le serveur : `localhost` autorise l'accès à la webcam sans HTTPS.

Pour lancer les tests et générer le rapport JaCoCo :

```bash
/c/USINE_LOGICIELLE/apache-maven/bin/mvn clean test
```

- Swagger : `http://localhost:8080/swagger-ui.html` (spécification JSON : `/v3/api-docs`).
- Réglages exposés au navigateur : `GET /api/config`.
- Le build Docker (`Dockerfile`) compile avec le Maven Wrapper ; l'image est publiée par la CI existante.

## Reconnaissance faciale (navigateur)

La détection et le calcul des empreintes tournent dans le navigateur avec `@vladmandic/face-api` ; ni le flux caméra ni ses images ne sont envoyés au CDN.

- **Local (`local`) :** bibliothèque, modèles et WASM chargés depuis jsDelivr avec versions épinglées. Internet est requis au premier chargement.
- **Docker (`prod`) :** les mêmes paquets sont récupérés pendant le build Docker et embarqués sous `/vendor/face-api`. Aucun accès CDN n'est requis à l'exécution.
- Une barre indique le chargement des scripts, manifestes, poids et WASM. Le navigateur réutilise son cache HTTP ; les URL CDN sont versionnées et les assets locaux sont servis avec un cache de 30 jours.
- Les URL sont surchargeables par `ASSISTANT_FACE_API_URL`, `ASSISTANT_FACE_MODELS_URL`, `ASSISTANT_FACE_WASM_SCRIPT_URL` et `ASSISTANT_FACE_WASM_URL`.

Le script `scripts/install-face-api.sh` reste disponible pour préinstaller manuellement les assets locaux. En cas d'échec, la page indique la source en erreur.

Comportement de l'écran d'accueil (`/`) :

- Scan rapide tant que personne n'est connecté, puis scan ralenti une fois connecté (le scan ne s'arrête jamais).
- Le visage reconnu ouvre la session ; le profil connecté revu prolonge la session (au plus un envoi par `ASSISTANT_HEARTBEAT_MIN_INTERVAL_SECONDS`).
- Un autre profil reconnu `ASSISTANT_SWITCH_CONFIRMATIONS` fois de suite remplace automatiquement la session.
- Le plus grand visage est seul pris en compte ; trop petit (`ASSISTANT_MIN_FACE_SIZE_PX`) ou ambigu (`ASSISTANT_MATCH_MARGIN`), il est ignoré.
- « Déconnexion » ferme la session et suspend la reconnaissance `ASSISTANT_LOGOUT_PAUSE_SECONDS` secondes.
- Le scan est suspendu quand l'onglet est masqué.
- `ASSISTANT_RECOGNITION_DEBUG=true` affiche moteur, distance, écart et temps de traitement.

Tests de la logique du navigateur (sans dépendance, hors CI) : `node --test src/test/js/*.test.mjs`.

## Déploiement avec Portainer

1. Portainer > Stacks > Add stack > Web editor, coller [docker-compose.yml](docker-compose.yml).
2. Renseigner les variables d'environnement (section Environment variables), au minimum :

```env
POSTGRES_PASSWORD=un-mot-de-passe-solide
ASSISTANT_ADMIN_PASSWORD=un-autre-mot-de-passe-solide
APP_IMAGE=<utilisateur-dockerhub>/assistant:latest
ASSISTANT_DOMAIN=assistant.lan
```

3. Déployer. Ouvrir `https://assistant.lan` (le nom doit résoudre vers le Raspberry Pi : DNS du routeur ou fichier `hosts`).

Le Caddyfile est inclus dans le compose (`configs`), aucun fichier externe n'est nécessaire. Caddy sert directement `/root.crt` depuis son volume de données en HTTP et HTTPS, sans le faire lire par l'application ; les autres requêtes HTTP sont redirigées vers HTTPS. Le build Docker récupère face-api 1.7.15 et tfjs-backend-wasm 4.22.0 et les embarque dans l'image ; les navigateurs du LAN n'ont donc pas besoin d'accéder au CDN.

## HTTPS et certificat

La caméra exige HTTPS. Caddy génère une autorité interne (`tls internal`). Avant la première connexion HTTPS, télécharge le certificat depuis `http://assistant.lan/root.crt` (remplace le nom par `ASSISTANT_DOMAIN`) et installe-le **une fois par appareil**. Après installation, la page d'aide est accessible sur `https://assistant.lan/certificat` et son bouton télécharge le même fichier.

- Windows : double-clic sur `root.crt` > Installer le certificat > Ordinateur local > placer dans « Autorités de certification racines de confiance ».
- Android : Paramètres > Sécurité (ou Sécurité et confidentialité) > Installer un certificat > Certificat CA ; les menus varient selon le fabricant.
- iPhone et iPad : ouvrir `root.crt`, installer le profil, puis Réglages > Général > Informations > Réglages du certificat et activer la confiance totale pour cette autorité.

## Réglages par variables d'environnement

Toutes les variables sont facultatives (valeur par défaut) sauf `POSTGRES_PASSWORD` et `ASSISTANT_ADMIN_PASSWORD`.

| Variable | Défaut | Rôle |
|---|---|---|
| `ASSISTANT_SESSION_DURATION_MINUTES` | 10 | Durée de la session, prolongée à chaque détection du visage |
| `ASSISTANT_SCAN_INTERVAL_DISCONNECTED_MS` | 1000 | Intervalle de scan avant connexion |
| `ASSISTANT_SCAN_INTERVAL_CONNECTED_MS` | 5000 | Intervalle de scan une fois connecté |
| `ASSISTANT_LOGOUT_PAUSE_SECONDS` | 60 | Pause de la reconnaissance après déconnexion |
| `ASSISTANT_MATCH_THRESHOLD` | 0.55 | Distance max de reconnaissance (plus bas = plus strict) |
| `ASSISTANT_MATCH_MARGIN` | 0.05 | Écart minimal avec le 2e meilleur profil, sinon refus (0 = désactivé) |
| `ASSISTANT_DETECTOR_INPUT_SIZE` | 224 | Taille d'entrée du détecteur (128 à 416, multiples de 32). Plus bas = plus rapide, moins précis |
| `ASSISTANT_DETECTOR_MIN_CONFIDENCE` | 0.5 | Confiance minimale pour accepter un visage |
| `ASSISTANT_MIN_FACE_SIZE_PX` | 80 | Taille minimale du visage dans l'image |
| `ASSISTANT_CAMERA_WIDTH` | 640 | Résolution demandée à la caméra |
| `ASSISTANT_RECOGNITION_BACKEND` | auto | Moteur de calcul : `auto`, `webgl`, `wasm`, `cpu` |
| `ASSISTANT_RECOGNITION_DEBUG` | false | Affiche distances et temps de traitement pour calibrer |
| `ASSISTANT_FACE_ASSETS_MODE` | `cdn` en local, `local` dans Docker | Source de la bibliothèque et des modèles |
| `ASSISTANT_FACE_API_URL` | URL jsDelivr en local, `/vendor/face-api/face-api.js` dans Docker | Fichier de la bibliothèque face-api |
| `ASSISTANT_FACE_MODELS_URL` | URL jsDelivr en local, `/vendor/face-api/model` dans Docker | Répertoire des manifestes et poids faciaux |
| `ASSISTANT_FACE_WASM_SCRIPT_URL` | URL jsDelivr en local, `/vendor/face-api/wasm/tf-backend-wasm.js` dans Docker | Script du backend WASM |
| `ASSISTANT_FACE_WASM_URL` | URL jsDelivr en local, `/vendor/face-api/wasm/` dans Docker | Répertoire des binaires WASM |
| `ASSISTANT_BACKUP_MAX_UPLOAD_MB` | 50 | Taille max d'un fichier de restauration |
| `ASSISTANT_SWITCH_CONFIRMATIONS` | 2 | Détections consécutives pour basculer vers un autre profil |
| `ASSISTANT_HEARTBEAT_MIN_INTERVAL_SECONDS` | 30 | Délai minimal entre deux prolongations de session |
| `ASSISTANT_MAX_PROFILES` | 0 | Nombre max de profils (0 = illimité) |
| `ASSISTANT_ENROLLMENT_MIN_PHOTOS` | 5 | Captures minimales à la création |
| `ASSISTANT_ENROLLMENT_MAX_PHOTOS` | 8 | Captures maximales |
| `ASSISTANT_WEATHER_CACHE_MINUTES` | 30 | Durée du cache météo |
| `ASSISTANT_PRIM_DAILY_BUDGET` | 750 | Budget journalier PRIM persisté, inférieur au quota de 800 |
| `ASSISTANT_PRIM_CACHE_MINUTES` | 10 | Intervalle d'actualisation du flux global PRIM |
| `ASSISTANT_PRIM_PAUSE_FROM` | 22:00 | Début de la pause PRIM, heure de Paris |
| `ASSISTANT_PRIM_PAUSE_TO` | 05:00 | Fin de la pause PRIM, heure de Paris |
| `ASSISTANT_UI_TYPING_ENABLED` | true | Active l'effet d'écriture en direct |
| `ASSISTANT_UI_TYPING_SPEED_MS` | 25 | Délai entre deux caractères affichés |
| `ASSISTANT_ADMIN_USERNAME` | admin | Login de l'administrateur (`/admin`) |
| `ASSISTANT_ADMIN_PASSWORD` | (obligatoire, 12 caractères minimum) | Mot de passe BCrypt de l'administrateur |
| `ASSISTANT_ADMIN_MAX_FAILED_ATTEMPTS` | 5 | Échecs de connexion avant blocage temporaire |
| `ASSISTANT_ADMIN_LOCKOUT_MINUTES` | 15 | Durée du blocage après trop d'échecs |
| `ASSISTANT_ADMIN_SESSION_TIMEOUT` | 15m | Durée de la session web administrateur |
| `SERVER_SERVLET_SESSION_COOKIE_SECURE` | false en local, true dans Docker | Exige HTTPS pour envoyer le cookie de session admin |
| `ASSISTANT_DOMAIN` | assistant.lan | Nom d'hôte servi par Caddy |
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | assistant / assistant / (obligatoire) | Base PostgreSQL |
| `SPRING_PROFILES_ACTIVE` | prod | `local` = SQLite pour le développement |
| `JAVA_TOOL_OPTIONS` | `-XX:+UseSerialGC -Xmx384m -Xss512k` | Réglages JVM adaptés au Raspberry Pi 4 |

Exemple pour un Raspberry Pi utilisé comme poste avec navigateur (GPU faible) :

```env
ASSISTANT_DETECTOR_INPUT_SIZE=160
ASSISTANT_RECOGNITION_BACKEND=wasm
ASSISTANT_SCAN_INTERVAL_CONNECTED_MS=10000
ASSISTANT_CAMERA_WIDTH=480
```

Exemple pour calibrer le seuil (affiche les distances à l'écran) :

```env
ASSISTANT_RECOGNITION_DEBUG=true
```

Exemple pour une reconnaissance plus tolérante et une session plus longue :

```env
ASSISTANT_MATCH_THRESHOLD=0.6
ASSISTANT_SESSION_DURATION_MINUTES=20
ASSISTANT_SCAN_INTERVAL_CONNECTED_MS=10000
ASSISTANT_MAX_PROFILES=5
```

Exemple en développement local (SQLite) :

```bash
SPRING_PROFILES_ACTIVE=local ASSISTANT_SESSION_DURATION_MINUTES=2 mvn spring-boot:run
```

## Sauvegarde et restauration

Depuis `/admin`, l'administrateur peut télécharger un dump JSON horodaté ou restaurer un fichier après confirmation explicite. L'export est paginé et inclut automatiquement les entités JPA, leurs données et relations, sauf les sessions, dont la clé PRIM (stockée en clair). Un ancien dump sans la table `prim_setting` reste restaurable : la clé PRIM est alors vidée. Le format versionné (`version`, `exportedAt`, `tables`) est indépendant de SQLite et PostgreSQL.

Avant toute écriture, le fichier est vérifié (version, tables, colonnes, types, identifiants et références). La restauration vide puis réinsère les données dans l'ordre des clés étrangères, dans une seule transaction ; une erreur annule tout. Les sessions faciales sont invalidées et les séquences PostgreSQL sont réalignées. La taille maximale est réglable par `ASSISTANT_BACKUP_MAX_UPLOAD_MB` (50 Mo par défaut). Le fichier contient des données personnelles et biométriques ainsi que la clé PRIM en clair : conservez-le en lieu sûr et ne le transmettez pas.

## Image Docker

La CI GitHub publie une image `linux/arm64` sur Docker Hub : `<utilisateur>/assistant:latest` (branche main), `:<branche>` (autres branches), `:<version>` (tags git).

## Fonctionnement

- Création de profil (`/enroll`) : nom puis 5 à 8 captures guidées. Une capture sans visage, trop petit, déjà connu ou ambigu est refusée. L'utilisateur choisit l'avatar parmi les captures ; seules les empreintes et cette image réduite à 128x128 sont envoyées et conservées.
- Après création, le profil est connecté automatiquement. Depuis la session, « Refaire mes captures » permet d'ajouter des empreintes ou de remplacer les anciennes et de choisir un nouvel avatar.
- Si la personne n'est plus reconnue, l'accueil propose de créer un profil ou de contacter l'administrateur pour purger l'ancien ; un profil existant ne peut pas être modifié sans sa session.
- Interface : thème commun `theme.css` (fond noir OLED, texte blanc, police, espacements), appliqué à l'accueil, l'enrôlement et l'administration. Les textes apparaissent progressivement ; le texte complet reste accessible, l'animation respecte `prefers-reduced-motion` et s'arrête pour les chaînes longues.
- Administration : `/admin/login`, identifiant `ASSISTANT_ADMIN_USERNAME` et mot de passe obligatoire (12 caractères minimum) `ASSISTANT_ADMIN_PASSWORD`. Spring Security hache le mot de passe avec BCrypt ; la session admin distincte de la reconnaissance faciale utilise un cookie HttpOnly/SameSite Strict, expire selon `ASSISTANT_ADMIN_SESSION_TIMEOUT` et exige Secure dans Docker. Après `ASSISTANT_ADMIN_MAX_FAILED_ATTEMPTS` erreurs, la connexion est bloquée pendant `ASSISTANT_ADMIN_LOCKOUT_MINUTES`. L'administrateur ne voit que noms/avatars et peut purger un profil avec confirmation ; la purge supprime aussi ses empreintes, sessions et données.
- Clé PRIM : elle se saisit uniquement depuis `/admin` (enregistrer, remplacer, supprimer ; jamais réaffichée, stockée en clair en base) et s'applique sans redémarrage. La variable `ASSISTANT_PRIM_API_KEY` n'existe plus : après mise à jour, ressaisis la clé. Tant qu'aucune clé n'est enregistrée, aucun appel PRIM n'est émis et le quota n'est pas consommé.
- Défilement : seul l'accueil est verrouillé ; `/configuration` (avec son icône de retour à l'accueil), `/admin` et `/enroll` défilent si leur contenu dépasse la fenêtre.
- Accueil sans défilement : l'horloge et l'aperçu webcam « W » restent en haut à gauche, le menu du profil en haut à droite, avec un widget principal et quatre widgets secondaires dans des emplacements fixes. Le poids est la vue principale par défaut ; le graphique affiche les valeurs en kg près des points et se trace progressivement, sauf si `prefers-reduced-motion` est actif. Le clic retourne sa carte pour montrer trois mesures et l'ajout. Les affectations et réglages s'ouvrent depuis le menu de l'avatar, sur `/configuration` ; les widgets se déplacent par glisser-déposer ou avec le sélecteur d'emplacement, puis sont enregistrés pour le profil.
- Applications : « Suivi du poids » accepte un poids de 20,0 à 400,0 kg avec un chiffre après la virgule ou le point, et permet de modifier/supprimer ses propres mesures via l'API. « Météo » recherche une ville depuis `/configuration` et affiche, en emplacement principal, un carré pour aujourd'hui (ville, « Maintenant » avec température et ressenti, matin et après-midi avec température et picto, risque de pluie, phrase de conseil au tutoiement) puis trois lignes pour les trois jours suivants ; en emplacement secondaire, seul le carré du jour est affiché. Le matin est le minimum de 6 h à 12 h et l'après-midi le maximum de 12 h à 18 h, avec le code météo le plus sévère de la plage. Les pictos sont des SVG monochromes (`/img/weather-icons.svg`) et un picto vent apparaît quand des rafales de plus de 50 km/h sont attendues. La durée est figée à 4 jours (la variable `ASSISTANT_WEATHER_FORECAST_DAYS` n'existe plus). Le widget « Transports » permet de choisir jusqu'à quatre lignes favorites IDFM par profil depuis le référentiel local `docs/referentiel-des-lignes.json`; le flux PRIM global est filtré par identifiant de ligne et partagé entre profils. Il est actualisé toutes les 10 minutes entre 5 h et 22 h (heure de Paris), avec affichage de l'heure de dernière actualisation. Les alertes sévères sont mises en avant; leur `message` est consultable en détail comme texte sans exécuter le HTML reçu. Le titre, le résumé et les détails sont réécrits toutes les 15 à 60 secondes. Le compteur journalier PRIM est persisté et borné par `ASSISTANT_PRIM_DAILY_BUDGET`.
- Mode TEST : l'administrateur peut le basculer depuis `/admin`. Les widgets météo et Transports utilisent alors des réponses locales, notamment `docs/response_info-traffic.json` et `docs/response-open-meteo.json`, sans appel externe ni consommation du quota PRIM. En météo, la recherche renvoie toujours Paris, quelle que soit la saisie; la prévision reprend Paris et les données de son fixture. Le simple basculement n'écrase pas la ville enregistrée du profil. Les widgets affichent un badge MODE TEST. Le proxy HTTP/HTTPS sortant se configure aussi depuis `/admin`; ses réglages et le mode TEST ne sont pas inclus dans les sauvegardes.
- Les lignes et les alertes affichées par le widget Transports sont réécrites indépendamment toutes les 15 à 60 secondes avec un curseur clignotant. Cette animation respecte le réglage d'écriture et `prefers-reduced-motion`; elle n'ajoute pas d'appel PRIM.
- Courbe de température du widget Météo : dans le bloc central, sous les jours suivants, une courbe horaire de maintenant − 3 h à maintenant + 24 h (un cercle par heure, étiquettes sur le minimum, le maximum et l'heure courante, axe X gradué toutes les 6 h et axe Y avec cinq valeurs en °C et lignes guides), tracée et retracée comme le graphique du poids. Les températures horaires viennent de la même requête Open-Meteo que le reste (`hourly` et `currentTime` dans `/api/weather`) ; entre 0 h et 3 h elle commence à 0 h, et en mode TEST elle est centrée sur l'heure du fixture. Elle n'apparaît pas dans les emplacements secondaires.
- Actualisation du widget Transports : le serveur n'interroge PRIM qu'une fois toutes les 10 minutes au plus (tolérance de 5 s sur l'échéance pour ne pas sauter une actualisation planifiée). Le navigateur relit le serveur toutes les 60 s, sans appel PRIM supplémentaire, et ne met l'affichage à jour (heure, lignes, animation) que si l'horodatage, le message d'état ou l'état de pause ont changé ; en cas d'échec, l'affichage précédent est conservé.
- Widget « Prochains passages » (clé `transit-passages`, distinct de Transports) : depuis `/configuration`, le profil recherche des arrêts par nom (3 caractères minimum, 20 résultats, API publique Île-de-France Mobilités `arrets`, sans clé ni référentiel local) et en enregistre jusqu'à 3 dans sa configuration (identifiant `arrid`, nom, ville, type). Pour chaque arrêt, le widget affiche les 4 prochains passages en « dans X min » (« À l'approche » sous la minute) avec ligne, destination et date de mise à jour. Le serveur appelle l'API PRIM `stop-monitoring` (`MonitoringRef = STIF:StopPoint:Q:<arrid>:`) avec la clé PRIM de l'administration ; le navigateur relit le serveur toutes les 60 s uniquement tant que la session faciale est ouverte, et la réponse de chaque arrêt est mise en cache environ 55 s et partagée entre profils. Le budget journalier PRIM et la pause nocturne s'appliquent (dernières valeurs conservées) ; sans clé PRIM, le widget invite à la renseigner et aucun appel n'est émis. En mode TEST, `docs/reponse-prochain-passage.json` est rejoué avec des horaires aléatoires dans le futur et la recherche d'arrêts est simulée (jeu local de 5 arrêts, sans appel externe). Un arrêt que PRIM rejette (400) s'affiche « Arrêt indisponible » et peut être retiré ; les arrêts enregistrés ne sont pas revérifiés. API : `/api/transit-passages` (`stops/search`, `stops`, `next`), documentée dans Swagger.
- Écran invité : quand aucune session faciale n'est valide (chargement, déconnexion, expiration, pause de reconnaissance), l'accueil affiche les widgets d'un profil « Invité » global, créé au démarrage et après chaque restauration de sauvegarde. Par défaut Météo en haut à gauche et Actualités en haut à droite ; l'horloge, la webcam et « Créer un profil » restent inchangés, et dès qu'un visage est reconnu les widgets du profil prennent la place. L'administrateur règle l'invité dans `/admin/guest` (lien depuis `/admin`) : application de chacun des 5 emplacements parmi les applications autorisées (Météo et Actualités seulement, liste blanche `AssistantApp.guestAllowed()` vérifiée côté serveur : le poids n'est jamais servi sans session), ville de la météo (recherche puis choix) et catégories d'actualités. Sans ville ou sans catégorie le widget affiche « À configurer par l'administrateur. » ; tant que l'administrateur n'a rien enregistré pour les actualités, toutes les catégories s'affichent. Sans session, seules les lectures annotées `@AllowGuest` (liste des applications, fragments, `/api/weather`, `/api/news/feed`) répondent, avec `Cache-Control: no-store` ; les écritures, la connexion au profil invité et `/configuration` restent refusées. L'invité n'apparaît ni dans la reconnaissance, ni dans la liste d'administration, ni dans `ASSISTANT_MAX_PROFILES`, et ne peut pas être purgé. Après un départ sans déconnexion l'écran invité revient à l'expiration de la session (`ASSISTANT_SESSION_DURATION_MINUTES`).
- Toutes les dates des widgets sont affichées en français (`fr-FR`) et les données de poids comme la ville météo sont filtrées par le profil de la session.
- Affichage : le widget Transports place en tête les lignes perturbées (ordre des favoris conservé par ailleurs) ; une annonce sans compte à rebours affiche sa date de fin (`jj/mm/aaaa à hh:mm`) ; l'avatar du profil est un hexagone à côté plat en haut sur tous les écrans ; sans profil connecté, le menu du profil n'affiche ni « Configuration », ni « Refaire mes captures », ni « Déconnexion » (« Administration » reste visible).
- Actualités : l'administrateur crée depuis `/admin` des catégories (nom, URL d'un flux RSS ou Atom en `https`, position d'affichage), les modifie ou les supprime après confirmation (les profils qui l'avaient cochée l'ignorent ensuite). Depuis `/configuration`, chaque profil coche les catégories à afficher. Le widget montre une catégorie à la fois (10 entrées au plus) : titres en effet machine à écrire, clic sur un titre pour déplier la description en texte brut (aucun HTML, image ni lien). Avec plusieurs catégories, la suivante s'affiche après 15 s sans action, des flèches permettent de naviguer (en boucle) et un clic sur une flèche ou une entrée remet le compteur à zéro ; tant qu'une entrée est dépliée la rotation est suspendue, et l'entrée se replie seule après 20 s (nouveau compteur de 15 s). Une seule catégorie : ni rotation ni flèches.
- Flux d'actualités : récupérés côté serveur (Rome, DOCTYPE refusés) via le client HTTP sortant (proxy inclus), `https` uniquement, sans suivre les redirections, réponse limitée à 1 Mo, adresses locales/privées refusées à chaque appel (avec un proxy, la résolution DNS peut être faite par le proxy : contrôle au mieux). Cache mémoire de 10 minutes par catégorie partagé par tous les profils ; en cas d'échec, les dernières données connues sont servies avec une notice, nouvel essai après 1 minute. Le mode TEST affiche des actualités factices sans appel réseau. La table `news_category` fait partie de la sauvegarde ; un ancien dump sans cette table reste restaurable (aucune catégorie).
- Rafraîchissement périodique : chaque widget météo et poids recharge ses données puis ré-écrit ses textes avec l'animation d'écriture, à un intervalle aléatoire de 15 à 60 s propre au widget ; le tracé du graphique de poids est rejoué. Le cycle du poids est suspendu quand la carte est retournée ou qu'un champ de saisie a le focus. Un échec de rechargement conserve l'affichage précédent ; sans animation d'écriture, aucun cycle n'est lancé.
- Session glissante, bascule automatique vers un autre profil reconnu, bouton Déconnexion (reconnaissance suspendue le temps configuré).
- Chaque profil ne voit que ses propres données.
