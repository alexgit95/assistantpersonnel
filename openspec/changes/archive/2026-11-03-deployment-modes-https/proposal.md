## Why

L'application ne sait servir qu'en HTTP. Or la webcam du kiosque exige un contexte sécurisé : `localhost` suffit quand le navigateur est sur la même machine, mais un serveur distant (intégration, production B) impose HTTPS pour le miroir, alors que les smartphones doivent rester en HTTP simple. Aujourd'hui, les trois cibles de déploiement (poste du développeur, ordinateur du miroir, serveur distant) sont déduites de deux profils (`local`, `prod`) qui mélangent base de données, assets, mode QR et transport.

## What Changes

- Trois modes de déploiement explicites, choisis par le profil Spring `dev` (défaut), `onprem` ou `remote`, via `spring.profiles.active` dans les propriétés, `SPRING_PROFILES_ACTIVE` ou `--spring.profiles.active` en ligne de commande. `local` et `prod` restent des alias (`local` active `dev`, `prod` active `remote`).
- Mode `remote` : l'application écoute en HTTPS (8443, kiosque et webcam) et en HTTP (8080, smartphones) simultanément, via un second connecteur Tomcat.
- Certificat HTTPS auto-signé généré au premier démarrage du conteneur par un script d'entrypoint (`keytool`), stocké dans un volume, avec noms d'hôte et IP configurables ; certificat fourni (PKCS12) possible en surcharge.
- Nouvelles propriétés `assistant.http.*` et `assistant.https.*` (ports, activation, keystore, noms d'hôte du certificat, durée de validité).
- URL des QR : en plus de la configuration explicite et de la bascule `localhost` ⇄ `127.0.0.1` du mode `dev`, dérivation de `http://<hôte de la requête du kiosque>:<port HTTP>` en mode `remote`. L'URL explicite reste prioritaire et reste obligatoire en `onprem`.
- Profils décomposés par axe : `onprem` = SQLite, assets en CDN, HTTP seul, clé de kiosque obligatoire ; `remote` = PostgreSQL, assets embarqués, HTTPS + HTTP, clé de kiosque obligatoire ; `dev` = SQLite, CDN, HTTP seul, QR de développement, clé optionnelle.
- HSTS désactivé et cookie de session non `Secure` pour que le mobile reste utilisable en HTTP quand le kiosque est en HTTPS.
- `Dockerfile`, `docker-compose.yml` : ports 8080 et 8443, volume des certificats, profil `remote` par défaut.
- README : tableau des trois modes, propriétés à valoriser, lancement, mise en garde sur le HTTP côté mobile (mot de passe d'administration en clair sur un serveur distant).
- `pom.xml` et `CHANGELOG.md` passent à la version `1.1.0`.
- **BREAKING** : en profil `prod`/`remote`, l'application écoute désormais aussi en HTTPS (8443) et le volume `certs` doit être monté pour conserver le certificat ; les stacks existantes qui référencent `prod` basculent dans le mode `remote`.

## Capabilities

### New Capabilities
- `deployment-modes`: sélection du mode `dev`/`onprem`/`remote` par profil, alias `local`/`prod`, valeurs par défaut de chaque mode (base, assets, transport, QR, clé de kiosque) et documentation README.
- `https-connector`: connecteur HTTPS en parallèle du connecteur HTTP, génération et remplacement du certificat, propriétés `assistant.http.*` et `assistant.https.*`, désactivation de HSTS.

### Modified Capabilities
- `https-deployment`: le déploiement n'est plus HTTP seul ; en mode `remote` il publie HTTP et HTTPS et un volume de certificats, la version passe à 1.1.0.
- `kiosk-access`: la clé de kiosque est obligatoire en `onprem` et `remote` (au lieu de `prod`) et optionnelle en `dev` (au lieu de `local`) ; le kiosque s'ouvre en `http://localhost:8080` ou `https://<hôte>:8443`.
- `mobile-companion`: l'URL des QR peut être dérivée de la requête du kiosque en mode `remote` ; elle reste obligatoire en `onprem`.
- `face-auth`: les assets de reconnaissance dépendent du mode (CDN en `dev` et `onprem`, embarqués en `remote`) et non plus du profil `local`/`prod`.

## Impact

- Code : nouvelle configuration du second connecteur Tomcat, `KioskStartupCheck`, `QrUrlResolver`, configuration de sécurité (HSTS), `AssistantProperties`, fichiers `application*.properties` (profils `dev`, `onprem`, `remote`, `sqlite`, `postgres`, groupes d'alias).
- Docker : `Dockerfile` (script d'entrypoint, `EXPOSE 8080 8443`, vérification de `keytool` dans l'image JRE), `docker-compose.yml` (ports, volume `certs`, variables), la GitHub Action est inchangée.
- Documentation : `README.md`, `CHANGELOG.md`, `pom.xml` (1.1.0), `.github/copilot-instructions.md` (profils).
- Sécurité : le HTTP côté mobile expose en clair mot de passe d'administration, cookies et jetons sur un serveur distant (risque accepté, documenté). Le certificat auto-signé impose une acceptation unique dans le navigateur du kiosque.
- Dépendances : aucune nouvelle bibliothèque.
