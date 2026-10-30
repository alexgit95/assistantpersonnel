# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).

## [0.0.13]

### Changed
- Widget « Prochains passages » : le nombre de passages affichés par arrêt dépend du nombre d'arrêts enregistrés par le profil (6 avec 1 ou 2 arrêts, 4 avec 3 arrêts, soit 12 lignes au maximum). La limite est appliquée côté serveur à l'affichage (`NextPassagesService`) ; l'appel PRIM, le cache et le budget sont inchangés. Un arrêt indisponible compte dans le nombre d'arrêts.
- Mode TEST : `MockPassageGateway` simule 7 visites pour pouvoir remplir 6 lignes sans régénérer le cache à chaque appel.
- Libellé Swagger de `GET /api/transit-passages/next` mis à jour.
- Version du projet portée à 0.0.13 (`pom.xml`).

### Added
- Tests JUnit 5 / Mockito : 1 arrêt, 2 arrêts, 3 arrêts et arrêt indisponible compté.

## [0.0.12]

### Added
- Écran invité : sans session faciale valide (chargement, déconnexion, expiration, pause de reconnaissance), l'accueil affiche les widgets d'un profil invité global ; dès qu'un visage est reconnu ils sont remplacés par ceux du profil. Par défaut Météo en haut à gauche (emplacement 1) et Actualités en haut à droite (emplacement 3), mêmes 5 emplacements que les profils.
- Profil invité (« Invité ») représé par la table `guest_setting`, créé au démarrage et recréé après chaque restauration de sauvegarde (`GuestService`, `GuestInitializer`). Il est exclu de la reconnaissance faciale, de la liste d'administration, de la purge et de `ASSISTANT_MAX_PROFILES`, et aucune session ne peut être ouverte à son nom.
- Liste blanche serveur `AssistantApp.guestAllowed()` (faux par défaut ; vrai pour Météo et Actualités) et annotation `@AllowGuest` sur les seules lectures `GET /api/apps`, `GET /apps/{key}/fragment`, `GET /api/weather` et `GET /api/news/feed`. Le poids, même activé par défaut, n'est jamais servi sans session ; les réponses invité portent `Cache-Control: no-store`.
- Formulaire `/admin/guest` (rôle ADMIN, CSRF) : application de chacun des 5 emplacements parmi les applications autorisées, ville de la météo (recherche puis choix) et catégories d'actualités. Lien depuis `/admin`.
- Actualités de l'invité : toutes les catégories tant que l'administrateur n'a rien enregistré (clé `categoryIds` absente), puis la sélection enregistrée telle quelle. Les widgets Météo et Actualités affichent « À configurer par l'administrateur. » en mode invité sans ville ou sans catégorie.
- Tests JUnit 5 / Mockito (service, intercepteur, liste blanche, connexion refusée, actualités), tests d'intégration (lectures invité, formulaire admin, purge refusée) et test Node du message invité.

### Changed
- `home.js` recharge les applications sans session (au chargement, après déconnexion ou expiration) pour afficher l'écran invité. Une demande de fragment sans session pour une application non autorisée répond désormais 404 au lieu de 401.
- `ProfileRepository.findAllWithEmbeddings` exclut l'invité ; `ProfileService` utilise `findAllWithoutGuest` et `countWithoutGuest`.
- Version du projet portée à 0.0.12 (`pom.xml`).

## [0.0.11]

### Added
- Widget « Prochains passages » (clé `transit-passages`, distinct de Transports) : jusqu'à 3 arrêts par profil, 4 prochains passages par arrêt affichés en « dans X min » avec ligne, destination et date de mise à jour. Message invitant à renseigner la clé PRIM si elle est absente.
- API `/api/transit-passages` (`stops/search`, `stops`, `next`, session faciale requise, documentée Swagger) : recherche d'arrêts à la demande sur l'API publique IDFM `arrets` (3 à 100 caractères, 20 résultats, échappement du filtre, cache 5 min) sans téléchargement ni table locale ; appel PRIM `stop-monitoring` avec la clé existante, cache de 55 s par arrêt partagé entre profils et appels concurrents fusionnés, budget journalier et pause nocturne PRIM appliqués, dernières valeurs conservées, « Arrêt indisponible » sur HTTP 400.
- Mode TEST : `docs/reponse-prochain-passage.json` (embarqué via `pom.xml`) rejoué avec des horaires aléatoires dans le futur, sans clé, appel externe ni budget ; la recherche d'arrêts est simulée (jeu local de 5 arrêts, même validation de la saisie).
- Tests JUnit 5 / Mockito (recherche, parseur, clients, service, contrôleur) et test Node du widget.

### Changed
- Version du projet portée à 0.0.11 (`pom.xml`).

## [0.0.10]

### Added
- Widget Météo (bloc central uniquement) : courbe des températures horaires de maintenant − 3 h à maintenant + 24 h (28 points au plus), sous les jours suivants. Un cercle par heure, étiquettes sur le minimum, le maximum et l'heure courante, axe X gradué toutes les 6 h et axe Y avec cinq valeurs en °C et guides horizontaux, même style et mêmes effets de tracé et de retracé que le graphique du poids ; sans animation avec `prefers-reduced-motion`. Entre 0 h et 3 h la courbe commence à 0 h (pas de `past_days`).
- `GET /api/weather` expose les températures horaires (`hourly` : heure locale ISO et température, nulle si absente) et l'heure courante des données (`currentTime`) ; aucun appel Open-Meteo supplémentaire. En mode TEST, la fenêtre est ancrée sur l'heure du fixture.

### Changed
- `ModeAwareWeatherGateway` et `TestWeatherGateway` conservent désormais le décalage UTC, les températures horaires et l'heure courante en reconstruisant la prévision (le décalage était perdu).
- Version du projet portée à 0.0.10 (`pom.xml`).

## [0.0.9]

### Changed
- Widget Transports : le navigateur relit le serveur toutes les 60 s (au lieu de 10 min) pour que l'heure de dernière actualisation suive celle du serveur sans recharger la page. La réponse vient du cache serveur : aucun appel PRIM de plus, le rythme reste de 10 minutes au plus.
- Le widget ne met à jour son affichage (heure, lignes, animation d'écriture) que si l'horodatage, le message d'état ou l'état de pause ont changé ; en cas d'échec d'une relecture, l'affichage et l'heure précédents sont conservés.
- Version du projet portée à 0.0.9 (`pom.xml`).

### Fixed
- `TransitService` : l'actualisation planifiée toutes les 10 minutes était souvent ignorée, car le flux précédent, horodaté après la réponse PRIM, avait quelques centaines de millisecondes de moins que l'intervalle. Les gardes d'âge tolèrent désormais 5 secondes d'avance (`SCHEDULE_SLACK`) ; le budget journalier et la pause nocturne s'appliquent toujours.

## [0.0.8]

### Added
- Widget Actualités (clé `news`) : catégories de flux RSS/Atom gérées depuis `/admin` (nom, URL https, position, suppression avec confirmation), choisies par profil depuis `/configuration`. Affichage d'une catégorie à la fois (10 entrées), titres en machine à écrire, description en texte brut dépliable au clic, rotation toutes les 15 s sans action, flèches en boucle, repli automatique d'une entrée après 20 s.
- API `/api/news/categories`, `/api/news/selection` (GET/PUT) et `/api/news/feed`, documentées Swagger.
- Table `news_category` (`docs/database.puml`), incluse dans la sauvegarde ; un ancien dump sans cette table reste restaurable.
- Dépendance Maven `com.rometools:rome` 2.1.0.
- Mode TEST : actualités factices sans appel réseau.
- Avatar du profil en hexagone régulier à côté plat en haut (classe `.avatar-hex` dans `theme.css`) : accueil, administration et choix de l'avatar à l'enrôlement ; la silhouette de secours suit la même forme. Le contour est un filtre du parent car `clip-path` coupe les bordures.

### Changed
- Widget Transports : les lignes dont le trafic n'est pas normal sont placées en tête (tri stable dans `TransitService.buildLineAlerts`), l'ordre des favoris est conservé dans chaque groupe.
- Widget Annonces : une annonce sans compte à rebours affiche « titre · jj/mm/aaaa à hh:mm » (date de fin) sans le mot « fin le ».
- Version du projet portée à 0.0.8 (`pom.xml`).
- Impact technique : récupération des flux via `OutboundHttp` (proxy), `https` uniquement, sans redirection, 1 Mo maximum, adresses locales/privées refusées à chaque appel, cache mémoire de 10 minutes par catégorie avec données périmées servies en cas d'échec.
- Le test d'intégration du registre des widgets cherche désormais les applications par clé au lieu de leur index.

### Fixed
- Menu du profil : l'attribut `hidden` était neutralisé par `display: flex` sur les liens et boutons du menu, de sorte que « Configuration », « Refaire mes captures » et « Déconnexion » s'affichaient sans profil connecté. La règle `.profile-menu-panel [hidden]` les masque à nouveau ; « Administration » reste visible et `/configuration` répond toujours 401 hors session.

## [0.0.7]

### Added
- Widget Météo repensé : carré pleine largeur pour aujourd'hui (ville, « Maintenant » avec température et ressenti, matin et après-midi avec température et picto, risque de pluie, phrase de conseil) et trois lignes pour les trois jours suivants ; un emplacement secondaire n'affiche que le carré du jour. Le matin (6 h à 12 h, minimum) et l'après-midi (12 h à 18 h, maximum) viennent des prévisions horaires Open-Meteo, avec le code météo le plus sévère de la plage.
- Pictos SVG monochromes (`/img/weather-icons.svg`) et picto vent pour des rafales de plus de 50 km/h.
- Phrase de conseil générée côté serveur (`WeatherAdvice`) : parapluie, veste, grand froid, rafales, chaleur, orage, neige.
- `docs/response-open-meteo.json` étoffé d'un bloc `hourly` sur 4 jours pour le mode TEST.

### Changed
- **BREAKING** : la durée des prévisions est figée à 4 jours ; `ASSISTANT_WEATHER_FORECAST_DAYS`, `assistant.weather.forecast-days` et le paramètre `forecastDays` des passerelles sont supprimés.
- `GET /api/weather` : `days[]` expose `morning` et `afternoon` (température, code WMO, description), `rainChancePercent`, `maxGustKmh`, `windAlert` et `advice` ; `current` ne contient plus humidité ni vent ; ajout de `utcOffsetSeconds`.
- Un cache de prévision dont le premier jour est passé (heure locale du lieu) est abandonné pour ne pas afficher un « aujourd'hui » périmé.
- Version du projet portée à 0.0.7 (`pom.xml`).

### Fixed
- `ApiQuotaService` : la création concurrente de la ligne du jour utilisait `saveAndFlush` (un `merge` JPA, car l'identifiant est assigné) qui pouvait remettre le compteur à 0 et laisser dépasser le budget (10 réservations accordées pour un budget de 5 en CI). Remplacée par un `insert … on conflict do nothing` atomique (SQLite et PostgreSQL).

## [0.0.6]

### Added
- Clé API PRIM saisie depuis `/admin` (enregistrer, remplacer, supprimer) : stockée en clair dans la nouvelle table `prim_setting`, jamais réaffichée, prise en compte sans redémarrage. Elle est incluse dans la sauvegarde ; un ancien dump sans cette table se restaure et vide la clé.

### Changed
- **BREAKING** : la variable `ASSISTANT_PRIM_API_KEY` et la propriété `assistant.prim.api-key` sont supprimées ; la clé doit être ressaisie dans `/admin` après mise à jour.
- Aucun appel PRIM ni consommation du quota journalier tant qu'aucune clé n'est enregistrée (requêtes à la demande et actualisation planifiée).
- Le verrouillage du défilement est limité à l'accueil ; `/configuration`, `/admin` et `/enroll` défilent.
- Version du projet portée à 0.0.6 (`pom.xml`).

### Fixed
- `ApiQuotaService.tryReserve` : lors de la première réservation concurrente du jour, un appel pouvait être refusé à tort si un autre créait la ligne du jour entre-temps (budget sous-consommé, test `concurrentReservationsNeverExceedTheBudget` aléatoire en CI). La réservation est retentée avant refus ; le test démarre désormais tous les threads simultanément avec un délai maximal.
- CSS corrompu de `home.css` (bloc `button` non fermé, règles `.icon-button` éclatées) qui privait le bouton de retour de `/configuration` de sa taille et de sa bordure.

## [0.0.5]

### Added
- Fixture Open-Meteo `docs/response-open-meteo.json` pour le mode TEST : la recherche renvoie toujours Paris et les prévisions utilisent les données correspondantes, sans appel externe. La ville du profil n'est pas modifiée par le seul basculement du mode.

### Changed
- Version du projet portée à 0.0.5 (`pom.xml`).

## [0.0.4]

### Added
- Proxy HTTP/HTTPS sortant configurable depuis `/admin` (URL, identifiants optionnels, hôtes exclus, activation) et appliqué à chaud à tous les appels sortants ; bouton « Tester la connexion » sans enregistrement ni consommation du budget PRIM. Le mot de passe est stocké en clair (LAN), jamais réaffiché, et les réglages sont exclus de la sauvegarde (nouvelle table `proxy_setting`).
- Fondations PRIM : budget journalier d'appels persisté (table `external_api_quota`, réservation atomique, jour de Paris, exclue de la sauvegarde), fenêtre de pause 22 h à 5 h configurable (`ASSISTANT_PRIM_*`).
- Widget Transports : jusqu'à quatre lignes IDFM favorites par profil, catalogue local embarqué, alertes PRIM globales filtrées par ligne et actualisées toutes les 10 minutes entre 5 h et 22 h, avec horodatage et budget persistant. Les alertes sont mises en avant selon leur sévérité et leur champ `message` est consultable sans exécuter le HTML fournisseur. Le mode TEST admin sert les fixtures locales pour PRIM et la météo sans appel externe; titre, résumé et détails se réécrivent aléatoirement toutes les 15 à 60 s avec un curseur clignotant.

- Rafraîchissement périodique des widgets météo et poids : module partagé `retype.js` (cycle à délai aléatoire de 15 à 60 s par widget, pause, arrêt). Fonctionnellement, les données sont rechargées et les textes réécrits avec l'animation d'écriture, le tracé du graphique de poids est rejoué et le cycle du poids est suspendu pendant la saisie.

### Changed
- Version du projet portée à 0.0.3 (`pom.xml`).
- `nextRetypeDelay` est désormais défini dans `retype.js` (ré-exporté par `announcements.js`).

## [0.0.2]

### Added
- Widget « Annonces » : annonces communes à tous les profils, gérées depuis `/admin` (création, modification, suppression manuelle, statut à venir / en cours / expirée), avec dates de début et de fin saisies en heure de Paris. `GET /api/announcements` ne renvoie que les annonces en cours, triées par date de fin croissante, avec l'instant serveur. Les annonces marquées compte à rebours affichent mois, jours, heures, minutes et secondes (unités de tête nulles masquées) décomptés en temps réel sur l'horloge serveur, et disparaissent à l'échéance. Les titres sont réécrits avec l'animation d'écriture à un intervalle aléatoire de 15 à 60 s propre à chaque annonce ; les annonces gardent un curseur clignotant en fin de texte et celles sans compte à rebours affichent leur date et heure de fin. Les annonces avec compte à rebours affichent aussi une barre de progression fine (80 % de la largeur, balayage animé) entre leur début et leur fin (`startsAt` ajouté à l'API). La table `announcement` est incluse dans la sauvegarde.

### Changed
- `typeText` accepte l'option `restart` pour rejouer l'écriture d'un texte inchangé.

## [0.0.1]

### Added
- Sauvegarde admin JSON versionnée et restaurable : export streamé/paginé de toutes les entités JPA hors sessions, validation complète avant import transactionnel, limite d'upload configurable, invalidation des sessions faciales et réalignement des séquences PostgreSQL. L'interface rappelle la sensibilité biométrique du fichier ; les tests SQLite couvrent le round-trip, les rejets et le rollback.
- Déploiement TLS interne documenté : `/root.crt` sert le certificat Caddy en pièce jointe depuis le volume monté en lecture seule ; le port HTTP n'expose que ce fichier pour le bootstrap, et `/certificat` fournit le guide Windows/Android/iOS. La documentation Swagger couvre le endpoint.
- Accueil sans défilement à cinq emplacements fixes, horloge et aperçu webcam « W », menu de configuration et d'administration sur l'avatar, et page dédiée avec affectation des widgets par glisser-déposer/sélecteur. Le poids occupe le slot principal par défaut : graphique seul au repos, carte retournée sur trois mesures et saisie, retour au graphique en cliquant une mesure. La ville météo se configure depuis cette page.
- Applications initiales « Suivi du poids » et « Météo » : API de poids paginée en dixièmes de kg (20,0–400,0), saisie point/virgule, modification/suppression isolées par profil, étiquettes de valeurs sur le graphique et tracé animé respectant `prefers-reduced-motion` ; géocodage Open-Meteo, choix des villes ambiguës, météo actuelle/prévisions avec cache borné de 30 minutes et gestion d'indisponibilité. Les widgets s'initialisent via le module JavaScript déclaré par leur application.
- Ecran d'accueil Thymeleaf (`/`) avec miniature webcam, etat de reconnaissance, temps de session restant et bouton Deconnexion. Fonctionnellement : le visage reconnu ouvre la session, le scan ralentit une fois connecte sans s'arreter, un autre profil confirme `ASSISTANT_SWITCH_CONFIRMATIONS` fois declenche la bascule, la deconnexion suspend la reconnaissance, la session survit a un rechargement de page.
- Modules JavaScript sans dependance : `matcher`, `session-machine`, `camera`, `recognizer`, `api`, `home`, `enrollment`, `admin`, `typewriter`, `profile-menu`, configuration par slots et graphique du poids. Tests `node --test` dans `src/test/js` (49 tests).
- Mode de calibration `ASSISTANT_RECOGNITION_DEBUG` (distance, ecart, temps de traitement, moteur).
- Chargement des assets de reconnaissance configurable : CDN jsDelivr versionné en `local`, paquets face-api 1.7.15 et tfjs-backend-wasm 4.22.0 téléchargés et embarqués au build Docker en profil `prod`. Fonctionnellement : le serveur Docker n'exige pas Internet à l'exécution et le navigateur affiche une progression de chargement.
- Préchargement des manifestes, shards et WASM via cache HTTP du navigateur ; assets locaux `/vendor/**` avec cache de 30 jours. Les images webcam ne sont pas transmises au CDN.
- Script `scripts/install-face-api.sh` conservé pour préinstaller les assets localement hors profil CDN.
- Authentification administrateur indépendante via Spring Security, mot de passe BCrypt obligatoire et verrouillage temporaire après échecs répétés. Fonctionnellement : accès admin distinct de l'identité faciale, session web à durée bornée.
- Page `/admin` : liste paginée nom/avatar seulement, purge confirmée supprimant profil, empreintes et sessions ; l'administrateur ne consulte pas les données applicatives.
- Thème OLED centralisé (`theme.css`) appliqué à l'accueil, l'enrôlement et l'administration ; textes affichés progressivement par un module accessible respectant `prefers-reduced-motion` et une durée maximale.
- Parcours Thymeleaf `/enroll` : captures guidées (5 à 8), rejet des visages absents/trop petits/doublons/ambigus, choix d'avatar 128x128, création puis connexion automatique. Fonctionnellement : images temporaires traitées en mémoire navigateur, seul l'avatar choisi est envoyé.
- Ré-enrôlement depuis l'accueil avec session valide : choix d'ajouter ou remplacer les empreintes, mise à jour de l'avatar, vérification que les nouvelles captures ne correspondent pas à un autre profil.
- Liens d'accueil pour créer un profil si non reconnu et refaire les captures si connecté ; tests unitaires de la politique de capture et test d'intégration de la page.
- Contrat `AssistantApp` découvert automatiquement et table `profile_app` en JSON texte ; API `/api/apps` limitée à la session du profil, réglage activation/ordre/configuration, fragments Thymeleaf sous `/apps/{key}/fragment` vérifiés contre les apps activées. Fonctionnellement : les profils choisissent leurs widgets sans accès croisé.

### Changed
- Les profils nouvellement crees sur un autre appareil sont pris en compte sous 10 s quand un visage est inconnu (sinon rafraichissement toutes les 60 s).
- Le typewriter réutilise le texte source mémorisé par élément au lieu de relire sa sortie DOM ; les re-rendus périodiques ne relancent plus l'écriture d'un texte inchangé.

### Added (socle)
- Socle Spring Boot 4.1 (Java 25) : Thymeleaf, JPA, validation, springdoc/Swagger, Maven Wrapper, JaCoCo, Dockerfile `linux/arm64` compatible avec la CI existante. Impact : projet buildable et deployable ; aucune fonctionnalite visible encore.
- Profils Spring `local` (SQLite) et `prod` (PostgreSQL) avec `ddl-auto`, sans outil de migration.
- Reglages par variables d'environnement (`ASSISTANT_*`) valides au demarrage ; endpoint `GET /api/config` pour le navigateur.
- Profils et empreintes faciales : `GET /api/faces`, creation (`POST /api/profiles`), ajout/remplacement d'empreintes, avatar, suppression. Fonctionnellement : les captures sont validees (nombre, format), un visage deja connu est refuse, et seul le profil connecte peut modifier ou supprimer le sien. Nombre de profils illimite par defaut (`ASSISTANT_MAX_PROFILES`).
- Session faciale glissante : `POST /api/auth/login`, `/heartbeat`, `/logout`, `GET /api/auth/session`. Fonctionnellement : cookie HttpOnly SameSite=Strict, expiration 10 min (configurable) apres la derniere detection ; jeton stocke uniquement sous forme de hash ; purge planifiee des sessions expirees ; bascule de profil par nouveau login.
- Annotation `@RequireSession` et injection de `AuthenticatedSession` pour les futurs endpoints d'applications (isolation par profil de la session).
- Evenement `ProfileDeletedEvent` pour nettoyer sessions et futures donnees d'applications.
- `docs/database.puml` : modele de donnees.
