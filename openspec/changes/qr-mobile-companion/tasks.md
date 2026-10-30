## 1. Bascule HTTP sur le LAN

- [x] 1.1 Retirer de `docker-compose.yml` le service `caddy`, `configs.caddyfile`, les volumes `caddy_data`/`caddy_config`, `depends_on: caddy`, `HTTP_PORT`, `HTTPS_PORT`, `ASSISTANT_DOMAIN` et `mem_limit: 64m`
- [x] 1.2 Publier le port de l'application (`"${APP_PORT:-8080}:8080"` à la place de `expose`) sans publier PostgreSQL
- [x] 1.3 Passer `SERVER_SERVLET_SESSION_COOKIE_SECURE` à `false` par défaut (compose et `application.properties`) et vérifier la connexion `/admin` depuis un autre poste
- [x] 1.4 Passer `server.forward-headers-strategy` à `none` et vérifier qu'aucun comportement n'en dépend
- [x] 1.5 Supprimer toute référence à Caddy, `ASSISTANT_DOMAIN` et `/root.crt` (README, docs, compose) et contrôler par recherche (`caddy`, `root.crt`)
- [x] 1.6 Point de contrôle : tests Maven verts, application démarrée en profil `local`, commit

## 2. Configuration

- [x] 2.1 Ajouter dans `AssistantProperties` : `assistant.public-base-url` (validation `^$|^https?://[^\s/]+(/\S*)?$`, sans slash final), `assistant.kiosk.*` (`key`, `poll-idle-ms` 3000, `poll-active-ms` 1000), `assistant.enrollment.photos` (1 à 30, défaut 8) et `assistant.enrollment.remote.*` (countdown 5, interval 4, session-ttl-minutes 10, max-consecutive-failures 3)
- [x] 2.2 Ajouter `assistant.handoff.*` (`token-ttl-seconds` 120, `full-session-minutes` 30, `weight-session-minutes` 10) et `assistant.qr.dev-mode` (faux par défaut, vrai dans `application-local.properties`)
- [x] 2.3 Supprimer `assistant.enrollment.min-photos` et `max-photos` (propriétés, validation `isPhotoRangeValid`, compose, `ClientConfig`) et exposer `enrollmentPhotos` dans `ClientConfigController`
- [x] 2.4 Ajouter les variables au `docker-compose.yml` avec valeurs par défaut (`ASSISTANT_KIOSK_KEY` obligatoire via `:?`)
- [x] 2.5 Ajouter le contrôle de démarrage de la clé de kiosque (échec en `prod` si vide, avertissement en `local`) sur le modèle de `AdminStartupConfiguration`
- [x] 2.6 Tests unitaires : validation des propriétés (bornes, format d'URL, clé obligatoire en production)
- [x] 2.7 Point de contrôle : tests Maven verts, démarrage `local` et `prod` simulé, commit

## 3. Clé de kiosque

- [x] 3.1 Créer le composant de contrôle `X-Kiosk-Key` (comparaison en temps constant, 403 sans clé valide, désactivé si clé vide en `local`)
- [x] 3.2 Appliquer le contrôle à `POST /api/auth/login`, `POST /api/auth/heartbeat` et aux futurs endpoints réservés au Pi
- [x] 3.3 Lire `?kiosk=` dans `home.js` (conservation en `sessionStorage`, retrait de l'URL) et l'envoyer sur chaque appel réservé au Pi via `api.js`
- [x] 3.4 Mettre à jour la documentation Swagger (`@ApiResponse` 403) des endpoints concernés
- [x] 3.5 Tests unitaires et MockMvc : accès avec clé, sans clé, clé erronée, mode `local` sans clé, login sans clé refusé
- [x] 3.6 Point de contrôle : reconnaissance et connexion fonctionnelles sur le Pi avec la clé, commit

## 4. Session de capture à distance (serveur)

- [x] 4.1 Créer `EnrollmentSessionService` (une seule session en mémoire, états `WAITING`→`COUNTDOWN`→`CAPTURING`→`REVIEW`→`DONE`/`CANCELLED`/`EXPIRED`, TTL prolongé par interaction, tâche de purge) avec `pairingToken` et `claimToken` hachés
- [x] 4.2 Implémenter `POST /api/enrollment-sessions` (créer ou reprendre, renvoie `id`, `pairingToken`, `qrUrl`) réservé au Pi
- [x] 4.3 Implémenter `GET /api/enrollment-sessions/{id}/state` (Pi ou smartphone, `serverNowMs`, `countdownStartsAtMs`, `nextShotAtMs`, progression, motifs de rejet)
- [x] 4.4 Implémenter `POST .../start` (nom valide, premier smartphone gagnant, `claimToken`) et `POST .../captures` (empreinte + miniature, rejets, abandon après N échecs consécutifs)
- [x] 4.5 Implémenter `GET .../avatars`, `POST .../restart`, `POST .../confirm` (création du profil via `ProfileService.create`, session `FULL` du smartphone) et `DELETE .../{id}`
- [x] 4.6 Implémenter `GET /api/kiosk/state` (qr, capture courante, `serverNowMs`) réservé au Pi
- [x] 4.7 Annuler la session quand un profil existant est reconnu pendant `WAITING` et purger les miniatures à la fin ou à l'expiration
- [x] 4.8 Documenter Swagger (`@Tag`, `@Operation`, `@ApiResponse` 200/201/400/401/403/404/409/500) et les DTO
- [x] 4.9 Tests unitaires et MockMvc : états, premier smartphone gagnant, rejets, abandon, TTL, confirm avec limite de profils, accès sans clé ou sans token
- [x] 4.10 Point de contrôle : tests Maven verts, commit (tests verts ; commit laissé à l'utilisateur)

## 5. Pilotage côté Pi et QR invité

- [x] 5.1 Embarquer une bibliothèque de génération de QR (SVG, MIT) sous `/vendor/` et documenter sa licence
- [x] 5.2 Créer `QrUrlResolver` (priorité de `public-base-url`, sinon bascule `localhost` ⇄ `127.0.0.1` en mode développement, sinon QR désactivés avec avertissement) et ses tests unitaires
- [x] 5.3 Ajouter le panneau QR dans `index.html` et `home.js` : QR menu invité (`/mobile?s=<pairingToken>`) toujours affiché hors des cinq emplacements, mode développement (lien `target="_blank"`, URL sous le QR)
- [x] 5.4 Créer le module `remote-enrollment.js` : poll de `GET /api/kiosk/state` (3 s au repos, 1 s en capture), suspension de la boucle de reconnaissance, compte à rebours depuis `nextShotAtMs`, `recognizer.detect` + `assessEnrollmentCapture`, miniature, dépôt de la capture
- [x] 5.5 Ajouter le panneau de guidage plein écran (compte à rebours, consigne de pose textuelle, cadre du visage, message « Un téléphone est connecté ») qui remplace le QR pendant la capture
- [x] 5.6 Reprendre la reconnaissance et rafraîchir `GET /api/faces` à la fin, à l'annulation et à l'expiration ; libérer timers, images et SVG à chaque changement d'état
- [x] 5.7 Tests JS (`src/test/js`) : génération et régénération du QR, états invité/capture, séquence des consignes, reprise de la reconnaissance
- [ ] 5.8 Point de contrôle : cycle de capture complet simulé (Pi + onglet « smartphone ») avec 8 captures, mesure de la tenue de l'intervalle de 4 s, commit (à faire à la main dans un navigateur ; le scénario serveur Pi + smartphone a été validé par script)

## 6. Jeton de transfert, portée de session et QR connecté

- [x] 6.1 Ajouter les colonnes `scope` (défaut `FULL`) et `source` (`FACE`/`HANDOFF`) à `AppSession` et mettre à jour `docs/database.puml` ; vérifier le démarrage sur une base SQLite et PostgreSQL existantes
- [x] 6.2 Porter `scope` dans `AuthenticatedSession` et appliquer la liste blanche `WEIGHT` dans l'intercepteur (403 hors liste)
- [x] 6.3 Créer `HandoffService` (jeton 256 bits haché, `profileId`, `scope`, `qrId`, TTL, plafond, `redeem` atomique, états `ACTIVE`/`CONSUMED`/`EXPIRED`, purge planifiée)
- [x] 6.4 Ajouter `SessionService.loginFromHandoff` (sans fermer la session du Pi, durée fixe par portée) et refuser le heartbeat des sessions de smartphone
- [x] 6.5 Implémenter `POST /api/session/handoff` (Pi, profil de la session du Pi) et `GET /handoff?t=` (cookie, redirection `/mobile` ou `/mobile/weight`, `no-store`, `no-referrer`, page « QR expiré », query string non journalisée)
- [x] 6.6 Afficher sur le Pi le QR menu connecté, régénéré dès `CONSUMED`/`EXPIRED` et 15 s avant le TTL (délai de grâce, deux jetons actifs au plus)
- [x] 6.7 Révoquer jetons et sessions de capture à la suppression d'un profil, à `invalidateAll`, à la restauration de sauvegarde, à la déconnexion du Pi et à la bascule de profil
- [x] 6.8 Documenter Swagger les nouveaux endpoints
- [x] 6.9 Tests unitaires et MockMvc : usage unique, deux scans simultanés, expiration, plafond, portée `WEIGHT`, révocations, heartbeat refusé, colonnes par défaut
- [ ] 6.10 Point de contrôle : scan simulé (clic en mode développement) avec régénération du QR, commit (à faire à la main dans un navigateur ; le scénario serveur a été validé par script)

## 7. Pages mobiles

- [x] 7.1 Ouvrir `/mobile/**` et `/handoff` dans `AdminSecurityConfig` ; créer `MobileController` et les templates `/mobile`, `/mobile/enroll`, `/mobile/weight` (réutiliser `theme.css`, `api.js`)
- [x] 7.2 Implémenter `/mobile` selon l'état (invité avec `?s=`, connecté `FULL`, sans jeton) avec « Administration » dans les deux premiers cas et « Se déconnecter » ; conserver le `pairingToken` côté navigateur et le retirer de l'URL ; message « Scannez à nouveau le QR » sur 401
- [x] 7.3 Implémenter `/mobile/enroll` : nom, « Lancer la capture », suivi n/N et rejets, miniatures, choix de l'avatar, validation, relance, annulation, redirection vers `/mobile`
- [x] 7.4 Implémenter `/mobile/weight` : liste paginée, ajout pré-rempli au dernier relevé (`inputmode="decimal"`, validation identique au backend), modification, suppression ; accessible avec `FULL` et `WEIGHT`
- [x] 7.5 Tests JS et MockMvc : états du menu, gestion du 401/403, validation de saisie du poids, accès `WEIGHT` à `/mobile/weight` uniquement
- [ ] 7.6 Point de contrôle : parcours d'enrôlement complet de bout en bout (Pi + onglet), commit (à faire à la main dans un navigateur : Pi + onglet smartphone)

## 8. Widget poids

- [x] 8.1 Simplifier `weight.html` : graphique + QR poids (jeton `WEIGHT`, régénération comme le QR menu), état vide avec message « Scannez pour saisir votre premier poids », aucune face arrière
- [x] 8.2 Retirer de `weight.js` le retournement, l'historique court et le formulaire, et supprimer le CSS `weight-card-*` et `weight-form` devenus inutiles
- [x] 8.3 Mettre à jour `WeightAssistantApp` si nécessaire et conserver l'exclusion des invités (`guestAllowed()` faux)
- [x] 8.4 Adapter les tests JS du widget poids (retrait du flip et du formulaire, ajout du QR et de l'état vide)
- [ ] 8.5 Point de contrôle : poids ajouté depuis `/mobile/weight` visible sur le widget du Pi, commit (à faire à la main dans un navigateur)

## 9. Suppression de l'ancien enrôlement

- [x] 9.1 Supprimer `enroll.html`, `enrollment.js`, `enrollment.css`, la route `/enroll` de `HomeController` et `/enroll` des URL ouvertes de `AdminSecurityConfig`
- [x] 9.2 Retirer « Créer un profil » et « Refaire mes captures » de `profile-header.html` et nettoyer `home.js`, `profile-menu.js`, `configuration.js`
- [x] 9.3 Conserver `enrollment-policy.js` et simplifier `canChooseAvatar` / `canSaveEnrollment` vers « exactement N » ; adapter ses tests
- [x] 9.4 Supprimer ou adapter les tests Java et JS de l'ancien enrôlement
- [x] 9.5 Point de contrôle : plus de référence à `/enroll` (recherche), tests verts, commit (tests verts ; commit laissé à l'utilisateur)

## 10. Configuration et administration sur mobile

- [x] 10.1 Ajouter à `/configuration` une vue liste pour petit écran (position et interrupteur par application) en conservant le glisser-déposer sur grand écran, sans dépendance à la caméra ni à face-api
- [x] 10.2 Vérifier les réglages de chaque widget sur petit écran et corriger les formulaires inutilisables
- [x] 10.3 Rendre `/admin/login`, `/admin` et `/admin/guest` utilisables sur smartphone (`admin.css`, `admin.js`)
- [ ] 10.4 Tests JS et vérification manuelle sur iOS Safari et Android Chrome (scan, cookie après redirection, administration) (tests JS faits ; vérification manuelle iOS Safari et Android Chrome à faire)
- [ ] 10.5 Point de contrôle : configuration et administration complètes depuis un smartphone, commit (à faire à la main sur un smartphone)

## 11. Recapture depuis le mobile

- [x] 11.1 Implémenter `POST /api/enrollment-sessions/recapture` (session `FULL`, mode `RECAPTURE`, `claimToken`) et la vérification serveur que le profil de la session du Pi est celui de la recapture à chaque dépôt de capture
- [x] 11.2 Prendre en charge `RECAPTURE` dans `remote-enrollment.js` (découverte par `GET /api/kiosk/state`, même compte à rebours) et dans `/mobile` (bouton « Refaire mes captures », choix Remplacer/Ajouter, sans nom)
- [x] 11.3 Appliquer la validation via `updateEmbeddings` (`replace` ou ajout) et le choix de l'avatar
- [x] 11.4 Tests unitaires et MockMvc : profil du Pi différent refusé, remplacer/ajouter, limites, annulation
- [ ] 11.5 Point de contrôle : recapture complète de bout en bout, commit (à faire à la main : Pi + onglet smartphone)

## 12. Version 1.0.0, documentation et finalisation

- [x] 12.1 Passer `pom.xml` à la version `1.0.0`
- [x] 12.2 Mettre à jour `CHANGELOG.md` (entrée 1.0.0 : `[Added]`, `[Changed]`, `[Fixed]`, `[Removed]`) avec impact technique et fonctionnel
- [x] 12.3 Mettre à jour `README.md` : parcours, tableau des variables (nom, défaut, rôle), lancement du kiosque avec la clé, réseau (IP fixe, isolation Wi-Fi), mode développement (`ASSISTANT_QR_DEV_MODE`, bascule `localhost` ⇄ `127.0.0.1`), mise en garde HTTP/LAN, migration depuis la 0.x
- [x] 12.4 Mettre à jour `docs/database.puml` et ajuster le `Purpose` de la spec `https-deployment` lors de la synchronisation des specs
- [x] 12.5 Vérifier la couverture des tests (JUnit 5 + Mockito, tests JS), Swagger complet, GitHub Action inchangée
- [ ] 12.6 Valider le changement (`openspec validate qr-mobile-companion`) et exécuter la checklist de `docs/parcours-qrcode-smartphone.md`, section 7, sur un Pi réel (`openspec validate` : valide ; checklist sur un Pi réel à faire à la main)
- [ ] 12.7 Poser le tag `v1.0.0` après validation pour produire l'image versionnée (à poser par l'utilisateur)
