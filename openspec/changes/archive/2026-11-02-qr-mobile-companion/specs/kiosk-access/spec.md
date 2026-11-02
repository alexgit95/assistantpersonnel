## ADDED Requirements

### Requirement: Clé de kiosque sur les appels réservés au Pi
Le système SHALL exiger l'en-tête `X-Kiosk-Key` valide (comparaison en temps constant avec `assistant.kiosk.key`) sur les endpoints réservés au Pi : `POST /api/auth/login`, `POST /api/auth/heartbeat`, `POST /api/session/handoff`, `GET /api/kiosk/state`, `POST /api/enrollment-sessions` et `POST /api/enrollment-sessions/{id}/captures`. Sans clé valide, la réponse MUST être 403 et rien n'est modifié.

#### Scenario: Clé valide
- **WHEN** le Pi appelle `POST /api/auth/login` avec la clé valide
- **THEN** la connexion est traitée normalement

#### Scenario: Clé absente ou fausse
- **WHEN** un appareil du LAN appelle l'un de ces endpoints sans la clé ou avec une clé erronée
- **THEN** la réponse est 403

#### Scenario: Connexion par simple identifiant
- **WHEN** un client sans clé envoie `POST /api/auth/login` avec un `profileId`
- **THEN** aucune session n'est créée

### Requirement: Configuration de la clé de kiosque
La clé SHALL être fournie par `ASSISTANT_KIOSK_KEY`. En production, l'application MUST refuser de démarrer avec un message explicite si elle est vide ; en profil `local`, une clé vide MUST désactiver le contrôle avec un avertissement dans les logs. La clé MUST NOT être journalisée.

#### Scenario: Production sans clé
- **WHEN** l'application démarre en profil `prod` avec `ASSISTANT_KIOSK_KEY` vide
- **THEN** le démarrage échoue avec un message explicite

#### Scenario: Développement sans clé
- **WHEN** l'application démarre en profil `local` avec la clé vide
- **THEN** les appels réservés au Pi sont acceptés et un avertissement est journalisé

### Requirement: Transmission de la clé par le Pi
Le navigateur du Pi SHALL être lancé avec `http://localhost:8080/?kiosk=<clé>`. La page MUST lire le paramètre, le conserver en `sessionStorage`, le retirer de l'URL (`history.replaceState`) et envoyer `X-Kiosk-Key` sur chaque appel réservé au Pi, y compris `login` et `heartbeat`. La clé MUST NOT apparaître dans les pages servies aux smartphones.

#### Scenario: Lancement du kiosque
- **WHEN** le Pi ouvre l'URL avec `?kiosk=<clé>`
- **THEN** la clé est mémorisée, retirée de la barre d'adresse et utilisée pour les appels suivants

#### Scenario: Smartphone
- **WHEN** un smartphone ouvre une page `/mobile/**`
- **THEN** aucune clé de kiosque n'est fournie ni nécessaire

### Requirement: État du Pi
`GET /api/kiosk/state` SHALL renvoyer `serverNowMs`, l'état de chaque `qrId` transmis (`ACTIVE`, `CONSUMED`, `EXPIRED`) et la session de capture courante (`id`, état, mode, `countdownStartsAtMs`, `nextShotAtMs`, progression, indice de guidage) ou `null`. Le Pi MUST l'interroger toutes les `assistant.kiosk.poll-idle-ms` (défaut 3000) au repos et toutes les `assistant.kiosk.poll-active-ms` (défaut 1000) pendant une capture. La réponse MUST contenir une recapture demandée depuis un smartphone.

#### Scenario: Au repos
- **WHEN** aucune capture n'est en cours
- **THEN** le Pi interroge l'état toutes les 3 secondes

#### Scenario: Pendant une capture
- **WHEN** une capture est en cours
- **THEN** le Pi interroge l'état toutes les secondes

#### Scenario: Recapture demandée
- **WHEN** un smartphone crée une session de recapture
- **THEN** la session apparaît dans l'état renvoyé au Pi
