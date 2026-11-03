## ADDED Requirements

### Requirement: Connecteurs HTTP et HTTPS simultanés
En mode `remote`, l'application SHALL écouter en HTTPS sur `assistant.https.port` (variable `ASSISTANT_HTTPS_PORT`, 8443 par défaut) et en HTTP sur `assistant.http.port` (variable `ASSISTANT_HTTP_PORT`, 8080 par défaut) en même temps. Dans les modes `dev` et `onprem`, l'application MUST écouter en HTTP seul sur `assistant.http.port`. HTTPS SHALL être activé par `assistant.https.enabled` et le connecteur HTTP additionnel par `assistant.http.enabled`.

#### Scenario: Mode remote
- **WHEN** l'application démarre en mode `remote`
- **THEN** `https://hôte:8443` et `http://hôte:8080` répondent tous les deux

#### Scenario: Mode dev ou onprem
- **WHEN** l'application démarre en mode `dev` ou `onprem`
- **THEN** seul `http://hôte:8080` répond et aucun port HTTPS n'est ouvert

#### Scenario: Ports configurés
- **WHEN** `ASSISTANT_HTTPS_PORT=9443` et `ASSISTANT_HTTP_PORT=9080` sont définis en mode `remote`
- **THEN** l'application écoute sur 9443 en HTTPS et 9080 en HTTP

### Requirement: Certificat HTTPS généré au premier démarrage
L'image Docker SHALL inclure un script d'entrypoint qui, en mode `remote`, génère un certificat auto-signé (keystore PKCS12) si aucun n'existe dans le répertoire des certificats (`ASSISTANT_HTTPS_CERT_DIR`, `/certs`, monté en volume), puis démarre l'application. Le certificat MUST contenir comme noms alternatifs `localhost`, `127.0.0.1` et les noms ou adresses de `ASSISTANT_HTTPS_HOSTNAMES` (liste séparée par des virgules), avoir une durée de validité `ASSISTANT_HTTPS_VALIDITY_DAYS` (825 jours par défaut) et être unique par déploiement. Un certificat existant MUST NOT être régénéré ni écrasé. Le mot de passe du keystore SHALL venir de `ASSISTANT_HTTPS_KEYSTORE_PASSWORD` ou, à défaut, être généré aléatoirement et stocké avec des droits restreints ; il MUST NOT être journalisé.

#### Scenario: Premier démarrage
- **WHEN** le conteneur démarre en mode `remote` sans keystore dans `/certs`
- **THEN** un certificat est généré avec les noms de `ASSISTANT_HTTPS_HOSTNAMES` et l'application écoute en HTTPS

#### Scenario: Redémarrage
- **WHEN** le conteneur redémarre avec le volume `/certs` conservé
- **THEN** le certificat existant est réutilisé sans régénération

#### Scenario: Aucun volume
- **WHEN** aucun volume n'est monté sur `/certs`
- **THEN** le certificat est régénéré à chaque démarrage et un avertissement est journalisé

#### Scenario: Image publique
- **WHEN** deux déploiements utilisent la même image
- **THEN** chacun possède son propre certificat et sa propre clé privée

### Requirement: Certificat fourni
Le système SHALL accepter un keystore fourni par l'exploitant via `ASSISTANT_HTTPS_KEYSTORE_PATH`, `ASSISTANT_HTTPS_KEYSTORE_TYPE` (PKCS12 par défaut) et `ASSISTANT_HTTPS_KEYSTORE_PASSWORD`. Lorsqu'il est renseigné, aucun certificat MUST être généré.

#### Scenario: Keystore fourni
- **WHEN** `ASSISTANT_HTTPS_KEYSTORE_PATH` désigne un keystore valide
- **THEN** l'application l'utilise pour HTTPS et n'en génère pas

#### Scenario: Keystore illisible
- **WHEN** le chemin ou le mot de passe est invalide
- **THEN** l'application refuse de démarrer avec un message explicite

### Requirement: Compatibilité du mobile en HTTP
En mode `remote`, le cookie de session MUST rester non `Secure` par défaut et l'en-tête HSTS MUST NOT être émis, afin que les smartphones puissent utiliser le port HTTP même lorsque le kiosque est ouvert en HTTPS sur le même nom d'hôte.

#### Scenario: Pas de HSTS
- **WHEN** le kiosque charge une page en HTTPS
- **THEN** la réponse ne contient pas d'en-tête `Strict-Transport-Security`

#### Scenario: Smartphone après le kiosque
- **WHEN** un smartphone ouvre `http://hôte:8080/mobile` après qu'un navigateur a visité `https://hôte:8443`
- **THEN** la page se charge en HTTP sans redirection vers HTTPS

#### Scenario: Connexion d'administration en HTTP
- **WHEN** l'administrateur se connecte en HTTP depuis un smartphone
- **THEN** son cookie de session est accepté par le navigateur

### Requirement: Accès webcam du kiosque
Le kiosque MUST accéder à la webcam dans un contexte sécurisé : `http://localhost:8080` en modes `dev` et `onprem`, `https://<hôte>:8443` en mode `remote`. Le README SHALL décrire l'acceptation unique du certificat auto-signé dans le navigateur du kiosque ou l'utilisation d'un certificat fourni.

#### Scenario: Kiosque distant
- **WHEN** le navigateur du miroir ouvre `https://<hôte>:8443/?kiosk=<clé>` et que le certificat est accepté
- **THEN** la caméra est autorisée et la reconnaissance démarre

#### Scenario: Kiosque local
- **WHEN** le navigateur du miroir ouvre `http://localhost:8080/?kiosk=<clé>` en mode `onprem`
- **THEN** la caméra est autorisée sans certificat
