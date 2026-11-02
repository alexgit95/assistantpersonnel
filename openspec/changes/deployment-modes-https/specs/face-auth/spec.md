## MODIFIED Requirements

### Requirement: Chargement avec progression et cache
Le système SHALL afficher une barre de progression accessible pendant le chargement de la bibliothèque de reconnaissance, des manifestes, des poids et des binaires WASM. Les URL versionnées SHALL bénéficier du cache HTTP du navigateur ; les assets locaux Docker SHALL être servis avec un cache navigateur à durée bornée. Les assets MUST provenir du CDN en modes `dev` et `onprem` et de `/vendor/face-api` (intégrés à l'image) en mode `remote`.

#### Scenario: Premier chargement en CDN
- **WHEN** un navigateur en mode `dev` ou `onprem` ne possède pas encore les assets en cache
- **THEN** la progression et le nom de la ressource chargée sont affichés, puis la reconnaissance démarre une fois tous les assets prêts

#### Scenario: Chargements suivants
- **WHEN** le navigateur possède des assets CDN versionnés dans son cache HTTP
- **THEN** il les réutilise sans les télécharger à nouveau tant qu'ils restent frais

#### Scenario: Docker sans accès CDN à l'exécution
- **WHEN** l'application est exécutée en mode `remote` (ou avec l'alias `prod`)
- **THEN** bibliothèque, modèles et WASM proviennent de `/vendor/face-api` intégrés à l'image, sans requête vers un CDN à l'exécution

#### Scenario: Échec de téléchargement
- **WHEN** un asset est inaccessible ou renvoie une erreur
- **THEN** la progression s'arrête sur un message explicite indiquant la source CDN ou locale et la reconnaissance ne démarre pas
