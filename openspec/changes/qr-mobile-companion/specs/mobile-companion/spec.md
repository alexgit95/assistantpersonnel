## ADDED Requirements

### Requirement: QR menu toujours affiché
Le Pi SHALL afficher en permanence un QR menu, hors des cinq emplacements de widgets. En mode invité, il encode `<base>/mobile?s=<pairingToken>` ; en mode connecté, il encode `<base>/handoff?t=<jeton FULL>`. Pendant `COUNTDOWN` et `CAPTURING`, il MUST être remplacé par le panneau de guidage plein écran, puis réaffiché.

#### Scenario: Invité
- **WHEN** aucun profil n'est connecté
- **THEN** le QR menu invité est affiché

#### Scenario: Connecté
- **WHEN** un profil est connecté
- **THEN** le QR menu connecté est affiché et remplace le QR invité

#### Scenario: Pendant la capture
- **WHEN** une session de capture est en `COUNTDOWN` ou `CAPTURING`
- **THEN** le panneau de guidage remplace le QR, qui revient ensuite

### Requirement: Jeton de transfert à usage unique
Le système SHALL émettre, via `POST /api/session/handoff?scope=FULL|WEIGHT` (appel réservé au Pi, pour le profil de sa propre session uniquement), un jeton aléatoire de 256 bits stocké haché en mémoire avec `profileId`, `scope`, `qrId` et une durée de vie de `assistant.handoff.token-ttl-seconds` (défaut 120). `GET /handoff?t=` MUST consommer le jeton de façon atomique, créer une nouvelle session pour le même profil sans fermer la session du Pi, poser le cookie de session et rediriger vers `/mobile` (`FULL`) ou `/mobile/weight` (`WEIGHT`), avec `Cache-Control: no-store` et `Referrer-Policy: no-referrer`. La query string MUST NOT être journalisée. Un jeton expiré, déjà utilisé ou inconnu SHALL produire une page « QR expiré, scannez à nouveau » sans cookie. Le nombre de jetons actifs est plafonné.

#### Scenario: Échange valide
- **WHEN** un smartphone ouvre `/handoff?t=` avec un jeton `FULL` valide
- **THEN** il reçoit une session `FULL` du profil et arrive sur `/mobile`

#### Scenario: Usage unique
- **WHEN** le même jeton est présenté une seconde fois
- **THEN** la page « QR expiré, scannez à nouveau » est affichée et aucune session n'est créée

#### Scenario: Deux scans simultanés
- **WHEN** deux requêtes présentent le même jeton en même temps
- **THEN** une seule obtient une session

#### Scenario: Profil imposé par le serveur
- **WHEN** le Pi demande un jeton
- **THEN** il est lié au profil de la session du Pi, sans identifiant de profil fourni par le client

### Requirement: Régénération immédiate du QR
Le Pi SHALL transmettre les `qrId` affichés dans `GET /api/kiosk/state` ; la réponse indique `ACTIVE`, `CONSUMED` ou `EXPIRED` pour chacun. Dès `CONSUMED` ou `EXPIRED`, le Pi MUST demander un nouveau jeton et redessiner le QR. Le Pi SHALL aussi en demander un nouveau 15 secondes avant l'expiration, l'ancien restant valide jusqu'à sa consommation ou son expiration (au plus deux jetons actifs par type).

#### Scenario: Après un scan
- **WHEN** un jeton est consommé
- **THEN** un nouveau QR est affiché au plus après un intervalle de polling du Pi

#### Scenario: Renouvellement avant expiration
- **WHEN** il reste 15 secondes avant l'expiration du jeton affiché
- **THEN** un nouveau QR est affiché sans interruption de scan

### Requirement: Sessions transitoires à portée
Le système SHALL stocker pour chaque session une portée (`FULL` par défaut ou `WEIGHT`) et une origine (`FACE` ou `HANDOFF`). Les sessions issues d'un transfert ou d'un enrôlement durent `assistant.handoff.full-session-minutes` (défaut 30, `FULL`) ou `assistant.handoff.weight-session-minutes` (défaut 10, `WEIGHT`), ne sont jamais prolongées (le heartbeat est réservé au Pi) et survivent à la déconnexion du Pi jusqu'à leur expiration. Une session `WEIGHT` MUST n'être valide que sur `/api/weight/**`, `/mobile/weight` et ses ressources statiques, `/api/auth/session` et `/api/auth/logout` ; toute autre route répond 403.

#### Scenario: Durée fixe
- **WHEN** une session `FULL` issue d'un transfert atteint 30 minutes
- **THEN** elle expire, quelle que soit l'activité du smartphone

#### Scenario: Portée WEIGHT
- **WHEN** un smartphone avec une session `WEIGHT` appelle `/api/apps` ou `/configuration`
- **THEN** la réponse est 403

#### Scenario: Pas de prolongation
- **WHEN** un smartphone appelle `POST /api/auth/heartbeat`
- **THEN** la requête est refusée car l'appel est réservé au Pi

#### Scenario: Colonnes sur base existante
- **WHEN** l'application démarre sur une base contenant déjà des sessions
- **THEN** les sessions existantes prennent la portée `FULL` et l'origine `FACE` sans échec

### Requirement: Menu mobile selon l'état
La page `/mobile` SHALL s'adapter à l'état : avec un `pairingToken` valide (`?s=`), elle propose « Créer mon profil » et « Administration » ; avec une session `FULL`, elle propose « Configuration », « Poids », « Refaire mes captures », « Administration » et « Se déconnecter » ; sans jeton ni session, elle affiche « Scannez le QR affiché sur l'écran » et « Administration ». Le `pairingToken` MUST être conservé côté navigateur puis retiré de l'URL.

#### Scenario: Menu invité
- **WHEN** un smartphone ouvre `/mobile?s=` avec un jeton valide
- **THEN** il voit « Créer mon profil » et « Administration »

#### Scenario: Menu connecté
- **WHEN** un smartphone avec une session `FULL` ouvre `/mobile`
- **THEN** il voit la configuration, le poids, la recapture, l'administration et la déconnexion

#### Scenario: Sans jeton
- **WHEN** un smartphone sans jeton ni session ouvre `/mobile`
- **THEN** il voit un message l'invitant à scanner le QR et l'accès à l'administration

#### Scenario: Session expirée
- **WHEN** la session du smartphone expire (401)
- **THEN** un message « Scannez à nouveau le QR sur l'écran » est affiché

### Requirement: Poids sur le mobile
La page `/mobile/weight` SHALL permettre de consulter la liste paginée des mesures, d'ajouter une mesure (date du jour, valeur pré-remplie avec le dernier relevé, clavier décimal, même validation que le backend), de la modifier et de la supprimer, en s'appuyant sur `/api/weight`. Elle MUST fonctionner avec une session `WEIGHT` comme avec une session `FULL`.

#### Scenario: Ajout
- **WHEN** l'utilisateur saisit 72,5 et valide sur le smartphone
- **THEN** la mesure est enregistrée sur son profil et apparaît dans le graphique du Pi au rafraîchissement suivant

#### Scenario: Correction
- **WHEN** l'utilisateur modifie ou supprime une de ses mesures
- **THEN** la liste est mise à jour

#### Scenario: Valeur invalide
- **WHEN** la valeur est hors bornes ou comporte plus d'une décimale
- **THEN** la saisie est refusée avec un message explicite

### Requirement: QR poids dans le widget poids
Le QR poids SHALL n'exister que dans le widget poids du Pi, pour un profil connecté ; il encode un jeton `WEIGHT` à usage unique et se régénère comme le QR menu. Le widget MUST NOT afficher de formulaire de saisie ni de face arrière.

#### Scenario: Widget actif
- **WHEN** le profil connecté a le widget poids placé sur son tableau de bord
- **THEN** le widget affiche le graphique et le QR poids

#### Scenario: Pas de widget
- **WHEN** le widget poids n'est pas placé
- **THEN** aucun QR poids n'est affiché

#### Scenario: Invité
- **WHEN** aucun profil n'est connecté
- **THEN** aucun QR poids n'existe

### Requirement: URL publique des QR
Le système SHALL construire toutes les URL de QR côté serveur à partir de `assistant.public-base-url` (variable `ASSISTANT_PUBLIC_BASE_URL`, par exemple `http://192.168.1.50:8080`, sans slash final) et renvoyer l'URL complète aux clients. Lorsque la propriété est vide et que le mode développement est inactif, les QR MUST être désactivés avec un avertissement dans les logs et sur l'écran d'administration. L'URL MUST NOT être déduite de la page du Pi.

#### Scenario: URL configurée
- **WHEN** `ASSISTANT_PUBLIC_BASE_URL` vaut `http://192.168.1.50:8080`
- **THEN** les QR encodent cette base

#### Scenario: Propriété vide en production
- **WHEN** la propriété est vide et le mode développement inactif
- **THEN** aucun QR n'est affiché et un avertissement est journalisé

#### Scenario: Format invalide
- **WHEN** la valeur n'est pas une URL `http` ou `https` valide ou se termine par un slash
- **THEN** l'application refuse de démarrer avec un message explicite

### Requirement: Mode développement des QR
La propriété `assistant.qr.dev-mode` (variable `ASSISTANT_QR_DEV_MODE`, faux par défaut, vrai dans `application-local.properties`) SHALL activer trois aides : les QR deviennent des liens cliquables (`target="_blank"`, `rel="noopener"`, info-bulle « Simuler le scan »), l'URL est affichée en toutes lettres sous le QR et, si `public-base-url` est vide, l'URL est dérivée de l'hôte de la requête en inversant `localhost` et `127.0.0.1` (schéma et port conservés). Seuls ces deux hôtes MUST être inversés ; tout autre hôte n'est pas reflété. `public-base-url` renseignée MUST toujours être prioritaire. En production, aucun lien ni URL n'est affiché.

#### Scenario: Bascule d'hôte
- **WHEN** le mode développement est actif, `public-base-url` vide et la page du Pi ouverte sur `http://localhost:8080`
- **THEN** les QR encodent `http://127.0.0.1:8080/...`, et inversement

#### Scenario: Cookies séparés
- **WHEN** l'utilisateur clique sur un QR dans ce mode
- **THEN** l'onglet ouvert a ses propres cookies et la session de la page du Pi n'est pas écrasée

#### Scenario: Hôte quelconque
- **WHEN** la requête arrive avec un hôte autre que `localhost` ou `127.0.0.1`
- **THEN** l'hôte n'est pas reflété dans l'URL du QR

#### Scenario: Production
- **WHEN** le mode développement est inactif
- **THEN** les QR ne sont pas cliquables et aucune URL n'est affichée

### Requirement: Révocation des états de transfert
Les jetons de transfert et les sessions de capture en mémoire SHALL être invalidés à la suppression d'un profil, à l'invalidation de toutes les sessions et à la restauration d'une sauvegarde ; les jetons non consommés du profil MUST être révoqués à la déconnexion du Pi et à la bascule de profil.

#### Scenario: Bascule de profil
- **WHEN** un autre profil est reconnu sur le Pi
- **THEN** l'ancien QR connecté est invalidé et remplacé

#### Scenario: Suppression de profil
- **WHEN** un profil est supprimé
- **THEN** ses jetons de transfert et sessions de capture en cours sont supprimés

#### Scenario: Déconnexion du Pi
- **WHEN** le Pi se déconnecte
- **THEN** les QR connecté et poids disparaissent et leurs jetons non consommés sont révoqués
