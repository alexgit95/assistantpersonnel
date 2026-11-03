## MODIFIED Requirements

### Requirement: Configuration de la clé de kiosque
La clé SHALL être fournie par `ASSISTANT_KIOSK_KEY`. Dans les modes `onprem` et `remote`, l'application MUST refuser de démarrer avec un message explicite si elle est vide ; en mode `dev`, une clé vide MUST désactiver le contrôle avec un avertissement dans les logs. Les alias `local` (mode `dev`) et `prod` (mode `remote`) suivent la règle de leur mode. La clé MUST NOT être journalisée.

#### Scenario: Mode onprem ou remote sans clé
- **WHEN** l'application démarre en mode `onprem` ou `remote` avec `ASSISTANT_KIOSK_KEY` vide
- **THEN** le démarrage échoue avec un message explicite

#### Scenario: Mode dev sans clé
- **WHEN** l'application démarre en mode `dev` avec la clé vide
- **THEN** les appels réservés au Pi sont acceptés et un avertissement est journalisé

#### Scenario: Ancien profil prod sans clé
- **WHEN** l'application démarre avec le profil `prod` et la clé vide
- **THEN** le démarrage échoue comme en mode `remote`

### Requirement: Transmission de la clé par le Pi
Le navigateur du kiosque SHALL être lancé avec `http://localhost:8080/?kiosk=<clé>` en modes `dev` et `onprem`, et avec `https://<hôte>:8443/?kiosk=<clé>` en mode `remote`. La page MUST lire le paramètre, le conserver en `sessionStorage`, le retirer de l'URL (`history.replaceState`) et envoyer `X-Kiosk-Key` sur chaque appel réservé au Pi, y compris `login` et `heartbeat`. La clé MUST NOT apparaître dans les pages servies aux smartphones.

#### Scenario: Lancement du kiosque
- **WHEN** le kiosque ouvre l'URL avec `?kiosk=<clé>`
- **THEN** la clé est mémorisée, retirée de la barre d'adresse et utilisée pour les appels suivants

#### Scenario: Smartphone
- **WHEN** un smartphone ouvre une page `/mobile/**`
- **THEN** aucune clé de kiosque n'est fournie ni nécessaire
