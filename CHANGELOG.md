# Changelog

Format : [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/).

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
