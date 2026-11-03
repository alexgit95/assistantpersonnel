## MODIFIED Requirements

### Requirement: URL publique des QR
Le système SHALL construire toutes les URL de QR côté serveur et renvoyer l'URL complète aux clients. La base SHALL être déterminée dans cet ordre : (1) `assistant.public-base-url` (variable `ASSISTANT_PUBLIC_BASE_URL`, par exemple `http://192.168.1.50:8080`, sans slash final), prioritaire en toutes circonstances ; (2) en mode `dev`, la requête dont l'hôte `localhost` ou `127.0.0.1` est inversé (voir « Mode développement des QR ») ; (3) en mode `remote`, pour une requête dont l'hôte n'est ni `localhost` ni `127.0.0.1`, `http://<hôte de la requête>:<assistant.http.public-port>` (port public égal à `assistant.http.port` par défaut) ; (4) sinon les QR MUST être désactivés avec un avertissement dans les logs et sur l'écran d'administration. En mode `onprem`, la propriété est obligatoire car le navigateur du kiosque est en `localhost`. La dérivation de l'hôte (étape 3) MUST s'appliquer uniquement aux appels réservés au kiosque authentifiés par la clé, l'hôte reflété MUST être validé (nom DNS, adresse IPv4 ou IPv6 entre crochets) et le schéma des QR dérivés MUST être `http`.

#### Scenario: URL configurée
- **WHEN** `ASSISTANT_PUBLIC_BASE_URL` vaut `http://192.168.1.50:8080`
- **THEN** les QR encodent cette base, même si le kiosque est en HTTPS

#### Scenario: Dérivation en mode remote
- **WHEN** le mode est `remote`, la propriété vide et le kiosque ouvert sur `https://serveur.example:8443`
- **THEN** les QR encodent `http://serveur.example:8080`

#### Scenario: Port public différent
- **WHEN** le mode est `remote`, `assistant.http.public-port` vaut 18080 et le kiosque est ouvert sur `https://10.0.0.5:8443`
- **THEN** les QR encodent `http://10.0.0.5:18080`

#### Scenario: Mode onprem sans URL
- **WHEN** le mode est `onprem`, la propriété vide et le kiosque ouvert sur `http://localhost:8080`
- **THEN** aucun QR n'est affiché et un avertissement est journalisé

#### Scenario: Hôte invalide
- **WHEN** l'hôte de la requête ne respecte pas le format validé
- **THEN** il n'est pas reflété, les QR sont désactivés et un avertissement est journalisé

#### Scenario: Appel sans clé de kiosque
- **WHEN** un client sans clé valide appelle un endpoint qui produit une URL de QR
- **THEN** la réponse est 403 et aucune URL n'est dérivée de son hôte

#### Scenario: Format invalide
- **WHEN** la valeur configurée n'est pas une URL `http` ou `https` valide ou se termine par un slash
- **THEN** l'application refuse de démarrer avec un message explicite

### Requirement: Mode développement des QR
La propriété `assistant.qr.dev-mode` (variable `ASSISTANT_QR_DEV_MODE`, faux par défaut, vrai dans le mode `dev`) SHALL activer trois aides : les QR deviennent des liens cliquables (`target="_blank"`, `rel="noopener"`, info-bulle « Simuler le scan »), l'URL est affichée en toutes lettres sous le QR et, si `public-base-url` est vide, l'URL est dérivée de l'hôte de la requête en inversant `localhost` et `127.0.0.1` (schéma et port conservés). Seuls ces deux hôtes MUST être inversés ; tout autre hôte n'est pas reflété par cette aide. `public-base-url` renseignée MUST toujours être prioritaire. Hors mode développement, aucun lien ni URL n'est affiché.

#### Scenario: Bascule d'hôte
- **WHEN** le mode développement est actif, `public-base-url` vide et la page du Pi ouverte sur `http://localhost:8080`
- **THEN** les QR encodent `http://127.0.0.1:8080/...`, et inversement

#### Scenario: Cookies séparés
- **WHEN** l'utilisateur clique sur un QR dans ce mode
- **THEN** l'onglet ouvert a ses propres cookies et la session de la page du Pi n'est pas écrasée

#### Scenario: Hôte quelconque
- **WHEN** la requête arrive avec un hôte autre que `localhost` ou `127.0.0.1` et que le mode `remote` est inactif
- **THEN** l'hôte n'est pas reflété dans l'URL du QR

#### Scenario: Hors mode développement
- **WHEN** le mode développement est inactif
- **THEN** les QR ne sont pas cliquables et aucune URL n'est affichée
