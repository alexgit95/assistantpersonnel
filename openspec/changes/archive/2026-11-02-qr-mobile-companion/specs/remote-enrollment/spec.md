## ADDED Requirements

### Requirement: Session de capture à distance unique
Le système SHALL gérer au plus une session de capture à distance active, en mémoire, créée ou reprise par le Pi via `POST /api/enrollment-sessions` (appel réservé au Pi). La session suit les états `WAITING`, `COUNTDOWN`, `CAPTURING`, `REVIEW`, `DONE`, `CANCELLED` et `EXPIRED`, et expire après `assistant.enrollment.remote.session-ttl-minutes` (défaut 10) sans interaction. La réponse MUST contenir l'identifiant de la session, le `pairingToken` et l'URL complète du QR construite par le serveur.

#### Scenario: Création
- **WHEN** le Pi appelle `POST /api/enrollment-sessions` sans session active
- **THEN** une session `WAITING` est créée et la réponse contient `id`, `pairingToken` et `qrUrl`

#### Scenario: Reprise après rechargement du Pi
- **WHEN** le Pi appelle `POST /api/enrollment-sessions` alors qu'une session non expirée existe
- **THEN** la session existante est renvoyée sans en créer une seconde

#### Scenario: Expiration
- **WHEN** aucune interaction n'a lieu pendant la durée de vie configurée
- **THEN** la session passe à `EXPIRED`, ses captures et miniatures sont supprimées et le Pi en crée une nouvelle

### Requirement: Lancement par le smartphone
Le smartphone SHALL lancer la capture via `POST /api/enrollment-sessions/{id}/start` en présentant le `pairingToken` et, en mode `CREATE`, un nom valide (mêmes règles que `ProfileService`). Le premier smartphone qui lance reçoit un `claimToken` exigé pour `restart`, `confirm`, `avatars` et l'annulation ; tout autre smartphone MUST être refusé avec un message « Enrôlement déjà en cours ».

#### Scenario: Premier smartphone
- **WHEN** un smartphone appelle `/start` avec un nom valide sur une session `WAITING`
- **THEN** la session passe à `COUNTDOWN` et la réponse contient un `claimToken`

#### Scenario: Second smartphone
- **WHEN** un autre smartphone tente de lancer la même session après le démarrage
- **THEN** la requête est refusée avec un message « Enrôlement déjà en cours »

#### Scenario: Nom invalide
- **WHEN** le nom est vide ou invalide en mode `CREATE`
- **THEN** la requête est refusée (400) et la session reste `WAITING`

### Requirement: Captures prises par la caméra du Pi
Le Pi SHALL prendre les captures avec sa propre caméra, en réutilisant le flux ouvert par la page d'accueil : un compte à rebours initial de `assistant.enrollment.remote.countdown-seconds` (défaut 5) puis une capture toutes les `assistant.enrollment.remote.interval-seconds` (défaut 4) jusqu'à obtenir `assistant.enrollment.photos` captures valides (défaut 8). Un compte à rebours visible MUST précéder chaque prise. L'empreinte et la miniature d'avatar de chaque capture valide sont envoyées via `POST /api/enrollment-sessions/{id}/captures` (appel réservé au Pi). Aucune image ni flux vidéo MUST être traité par le serveur en dehors de la miniature d'avatar.

#### Scenario: Séquence de captures
- **WHEN** la session passe à `COUNTDOWN`
- **THEN** le Pi affiche un compte à rebours de 5 s puis capture toutes les 4 s jusqu'à 8 captures valides

#### Scenario: Nombre configurable
- **WHEN** `ASSISTANT_ENROLLMENT_PHOTOS` vaut 6
- **THEN** la session se termine après 6 captures valides

#### Scenario: Aucune image côté serveur
- **WHEN** une capture est déposée
- **THEN** le serveur reçoit uniquement une empreinte et une miniature d'avatar, et conserve ces données en mémoire

### Requirement: Guidage de pose textuel
Pendant la capture, le Pi SHALL afficher à chaque prise une consigne de pose textuelle prise dans une séquence fixe (par exemple de face, légèrement à gauche, légèrement à droite, menton un peu haut, menton un peu bas), répétée lorsque le nombre de photos dépasse la longueur de la liste. Aucune détection d'orientation du visage MUST être exigée.

#### Scenario: Consigne affichée
- **WHEN** une prise est annoncée
- **THEN** la consigne correspondant au numéro de la capture est affichée sur le Pi

#### Scenario: Plus de captures que de consignes
- **WHEN** le nombre de photos dépasse la longueur de la séquence
- **THEN** la séquence recommence depuis le début

### Requirement: Rejet des captures et abandon
Le Pi SHALL contrôler chaque capture avec `assessEnrollmentCapture` et la rejeter si aucun visage n'est détecté, si le visage est trop petit, si plusieurs visages sont présents ou si le visage correspond à un autre profil (doublon). Une capture rejetée MUST être reprise automatiquement au compte à rebours suivant, avec un message sur le Pi et le smartphone. Après `assistant.enrollment.remote.max-consecutive-failures` (défaut 3) rejets consécutifs, la session MUST repasser à `WAITING`.

#### Scenario: Visage absent
- **WHEN** aucune détection n'est possible à l'instant de la prise
- **THEN** la prise est rejetée, un message l'indique et la capture est retentée au compte à rebours suivant

#### Scenario: Doublon
- **WHEN** l'empreinte correspond à un profil existant
- **THEN** la capture est rejetée avec un message explicite

#### Scenario: Échecs consécutifs
- **WHEN** trois rejets consécutifs surviennent
- **THEN** la session repasse à `WAITING` et le smartphone peut relancer

### Requirement: Reconnaissance suspendue pendant la capture
Pendant `COUNTDOWN` et `CAPTURING`, le Pi SHALL suspendre la boucle de reconnaissance et de connexion ; elle MUST reprendre à la fin, y compris en cas d'annulation, d'expiration ou de perte du smartphone. À la fin, le Pi MUST rafraîchir immédiatement `GET /api/faces`.

#### Scenario: Pas de connexion parasite
- **WHEN** un visage est visible pendant la capture
- **THEN** aucune connexion ni bascule de profil n'a lieu

#### Scenario: Reprise
- **WHEN** la session passe à `REVIEW`, `DONE`, `CANCELLED` ou `EXPIRED`
- **THEN** la reconnaissance reprend et le catalogue des visages est rechargé

### Requirement: Visage connu pendant l'attente
Si un profil existant est reconnu pendant `WAITING`, le Pi SHALL ouvrir sa session normalement et la session de capture MUST être annulée.

#### Scenario: Utilisateur déjà enrôlé
- **WHEN** un profil existant est reconnu alors que la session est `WAITING`
- **THEN** il est connecté, la session de capture est annulée et le QR connecté remplace le QR invité

### Requirement: Revue et validation sur le smartphone
En `REVIEW`, le smartphone SHALL pouvoir récupérer les miniatures (`GET /api/enrollment-sessions/{id}/avatars`, séparé de `/state`), choisir l'avatar, relancer la capture (`restart` : captures effacées, retour à `COUNTDOWN`) ou valider (`confirm`). À la validation en mode `CREATE`, le serveur MUST créer le profil via `ProfileService.create` (embeddings de toutes les captures, avatar choisi réduit à 128x128 maximum) et ouvrir une session `FULL` pour le smartphone ; les autres miniatures MUST être jetées. Le smartphone est ensuite redirigé vers `/mobile`.

#### Scenario: Validation
- **WHEN** le smartphone confirme avec un index d'avatar valide
- **THEN** le profil est créé avec tous les embeddings et l'avatar choisi, une session `FULL` est ouverte pour le smartphone et la session de capture passe à `DONE`

#### Scenario: Relance
- **WHEN** le smartphone demande « Relancer » en `REVIEW`
- **THEN** les captures sont effacées et un nouveau compte à rebours démarre

#### Scenario: Limite de profils
- **WHEN** la limite `ASSISTANT_MAX_PROFILES` est atteinte
- **THEN** la validation est refusée avec un message explicite sur le smartphone

### Requirement: Synchronisation des écrans
Chaque réponse d'état SHALL contenir `serverNowMs` ; le Pi et le smartphone MUST calculer leurs comptes à rebours à partir de `countdownStartsAtMs` et `nextShotAtMs` fixés par le serveur. Le Pi interroge `GET /api/kiosk/state` et le smartphone `GET /api/enrollment-sessions/{id}/state` ; les réponses restent légères (miniatures exclues).

#### Scenario: Compte à rebours synchrone
- **WHEN** le smartphone lance la capture
- **THEN** le Pi et le smartphone affichent le même compte à rebours à moins d'un intervalle de polling près

#### Scenario: Progression
- **WHEN** une capture valide est déposée
- **THEN** le smartphone affiche la progression « n sur N »

### Requirement: Recapture du profil connecté
Un smartphone disposant d'une session `FULL` SHALL pouvoir demander la recapture de son profil via `POST /api/enrollment-sessions/recapture`, qui crée une session `RECAPTURE` liée au profil de la session et renvoie un `claimToken`. Le Pi MUST n'accepter les captures que si le profil de **sa propre** session est celui de la session de recapture, ce que le serveur vérifie à chaque dépôt. En `REVIEW`, le smartphone choisit « Remplacer » ou « Ajouter » (équivalent du paramètre `replace` de la mise à jour des embeddings) puis valide ; aucun nom n'est demandé.

#### Scenario: Recapture par le propriétaire
- **WHEN** le profil P, connecté sur le Pi et sur le smartphone, demande la recapture
- **THEN** le Pi déroule le compte à rebours et les prises, et le smartphone choisit remplacer ou ajouter

#### Scenario: Autre profil sur le Pi
- **WHEN** le profil connecté sur le Pi n'est pas celui de la session de recapture
- **THEN** les captures sont refusées par le serveur et la recapture ne démarre pas

### Requirement: Annulation
Le Pi (avec la clé de kiosque) ou le smartphone (avec son `claimToken`) SHALL pouvoir annuler la session via `DELETE /api/enrollment-sessions/{id}` ; le Pi revient alors à l'état invité avec un nouveau QR et les données de la session sont supprimées.

#### Scenario: Annulation par le smartphone
- **WHEN** le smartphone annule en cours de capture
- **THEN** la session passe à `CANCELLED`, la reconnaissance du Pi reprend et un nouveau QR invité apparaît

### Requirement: Nombre de photos unique
Le nombre de captures d'enrôlement SHALL être défini par la seule propriété `assistant.enrollment.photos` (variable `ASSISTANT_ENROLLMENT_PHOTOS`, défaut 8, bornes 1 à 30). Les propriétés `assistant.enrollment.min-photos` et `assistant.enrollment.max-photos` MUST NOT subsister.

#### Scenario: Valeur hors bornes
- **WHEN** `ASSISTANT_ENROLLMENT_PHOTOS` vaut 0 ou 31
- **THEN** l'application refuse de démarrer avec un message explicite

#### Scenario: Défaut
- **WHEN** aucune valeur n'est fournie
- **THEN** huit captures valides sont demandées
