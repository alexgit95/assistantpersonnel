## Context

L'application est servie en HTTP seul depuis la 1.0.0 (Caddy retiré). La webcam exige un contexte sécurisé : `localhost` convient quand le navigateur du kiosque est sur la machine de l'application, mais pas quand le serveur est distant (intégration, production B). Les smartphones n'utilisent pas la caméra et doivent rester en HTTP simple (pas de certificat à installer).

État actuel :
- `application.properties` fixe `server.port` (8080), `server.forward-headers-strategy=none` et le cookie de session non `Secure`.
- Le profil `local` couple SQLite, assets face-api via CDN et `assistant.qr.dev-mode=true` ; le profil `prod` couple PostgreSQL et assets locaux (`/vendor/face-api`, embarqués par le `Dockerfile`). `KioskStartupCheck` échoue en `prod` si la clé de kiosque est vide.
- `QrUrlResolver` utilise `assistant.public-base-url`, sinon (mode dev) inverse `localhost` et `127.0.0.1`, sinon désactive les QR.
- Le `Dockerfile` termine sur `eclipse-temurin:25-jre`, utilisateur non root `assistant`, `SPRING_PROFILES_ACTIVE=prod` ; le déploiement est une stack Compose sur Raspberry Pi 4 (4 Go).
- Aucun outil de migration de base : Hibernate `ddl-auto=update` ; la base ne change pas dans ce changement.

Trois cibles :

| Mode | Cas | Webcam | QR et smartphone | Base | Docker |
|---|---|---|---|---|---|
| `dev` | Poste du développeur | `http://localhost:8080` | `http://127.0.0.1:8080` | SQLite | non |
| `onprem` | Production A (ordinateur du miroir) | `http://localhost:8080` | `ASSISTANT_PUBLIC_BASE_URL` (IP du LAN) | SQLite | non |
| `remote` | Intégration et production B | `https://hôte:8443` | `http://hôte:8080` | PostgreSQL | oui |

## Goals / Non-Goals

**Goals:**
- Choisir le mode par une seule valeur : `spring.profiles.active` (fichier de propriétés), `SPRING_PROFILES_ACTIVE` ou `--spring.profiles.active`.
- Servir HTTPS (kiosque) et HTTP (smartphones) en parallèle en mode `remote`, avec certificat généré par l'image Docker.
- Conserver `local` et `prod` comme alias, pour ne pas casser les stacks existantes.
- Documenter les modes et leurs propriétés dans le README avec le tableau ci-dessus.
- Incrémenter la version en `1.1.0` (`pom.xml`, `CHANGELOG.md`).

**Non-Goals:**
- Forcer HTTPS pour `/admin` ou la clé de kiosque (décision : tout reste en HTTP côté mobile, risque documenté).
- Redirection HTTP vers HTTPS, reverse proxy, ACME / Let's Encrypt automatique.
- Changement de schéma de base ou migration de données.
- Modification de la GitHub Action (seul le contenu du `Dockerfile` change).
- HTTPS en `dev` et `onprem`, Docker pour `dev` et `onprem`.

## Decisions

### 1. Le mode est un profil Spring, avec des groupes

Les modes `dev`, `onprem` et `remote` sont des profils. Chaque mode inclut des profils d'axe par `spring.profiles.group.*` :

```
dev     → sqlite, assets-cdn, http-only, qr-dev
onprem  → sqlite, assets-cdn, http-only, kiosk-key-required
remote  → postgres, assets-local, https-dual, kiosk-key-required
local   → dev        (alias)
prod    → remote     (alias)
```

`spring.profiles.default` passe de `local` à `dev`. Les fichiers `application-<axe>.properties` portent les valeurs ; `application-local.properties` et `application-prod.properties` disparaissent au profit des fichiers d'axe.

Alternatives écartées : une propriété personnalisée `assistant.mode` (code en plus pour reproduire ce que les profils font déjà, deux mécanismes concurrents) ; garder `local`/`prod` et ajouter des drapeaux (couplage actuel conservé).

### 2. Second connecteur HTTP par `WebServerFactoryCustomizer`

Spring Boot ne propose pas de second port par propriété. En mode `remote` :
- `server.port=${assistant.https.port}` avec `server.ssl.bundle=kiosk` (HTTPS principal) ;
- un `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` conditionné par `assistant.http.enabled=true` ajoute un connecteur HTTP sur `assistant.http.port`.

En modes HTTP seul, `server.port=${assistant.http.port}` et aucun connecteur additionnel. Propriétés : `assistant.http.enabled`, `assistant.http.port` (8080), `assistant.https.enabled` (faux sauf `remote`), `assistant.https.port` (8443).

Alternative écartée : HTTP principal avec un connecteur HTTPS ajouté (la configuration SSL de Tomcat à la main est plus lourde que le SSL bundle).

### 3. Certificat : script d'entrypoint au premier démarrage

Le `Dockerfile` embarque `docker-entrypoint.sh`. En mode `remote`, si aucun keystore n'existe dans `ASSISTANT_HTTPS_CERT_DIR` (`/certs`, volume), il exécute `keytool -genkeypair` (RSA 2048, SAN construit depuis `ASSISTANT_HTTPS_HOSTNAMES` : `localhost`, `127.0.0.1` et les noms ou IP fournis, validité `ASSISTANT_HTTPS_VALIDITY_DAYS`, 825 par défaut) et écrit un PKCS12. Mot de passe : `ASSISTANT_HTTPS_KEYSTORE_PASSWORD` ou valeur aléatoire écrite à côté du keystore (droits `600`). Le script termine par `exec java -jar` (signaux et PID 1 corrects). Un keystore fourni (`ASSISTANT_HTTPS_KEYSTORE_PATH`, type et mot de passe) remplace la génération et n'est jamais écrasé.

Alternatives écartées : certificat généré au build (même clé privée pour tous les utilisateurs de l'image publique, pas de SAN valide) ; génération en Java (API interne du JDK pour les certificats X.509, ou nouvelle dépendance) ; conteneur de génération séparé (service Compose en plus sur 4 Go).

Vérification préalable : `keytool` présent dans `eclipse-temurin:25-jre`. Sinon, copier le binaire depuis l'étape `jdk` ou basculer l'image runtime.

### 4. Dérivation de l'URL des QR

`QrUrlResolver.baseUrl` applique, dans l'ordre :
1. `assistant.public-base-url` non vide : retournée telle quelle ;
2. hôte de la requête `localhost` ou `127.0.0.1` : en `dev`, inversion de l'hôte (comportement actuel) ; sinon pas d'URL ;
3. autre hôte et mode HTTPS + HTTP : `http://<hôte>:<assistant.http.public-port>` (le port public vaut `assistant.http.port` par défaut, à surcharger quand Docker publie un autre port, `APP_PORT`) ;
4. sinon QR désactivés avec avertissement.

La dérivation ne s'applique qu'aux appels réservés au kiosque (clé `X-Kiosk-Key` vérifiée : `POST /api/enrollment-sessions`, `POST /api/session/handoff`, `GET /api/kiosk/state`), ce qui limite l'injection d'en-tête `Host` : seule la machine qui connaît la clé reçoit l'URL construite. Le nom d'hôte est validé (lettres, chiffres, `.`, `-`, `:` pour une IPv6 entre crochets) avant d'être reflété. `server.forward-headers-strategy` reste à `none`. Le port du connecteur HTTP n'est pas lu depuis la requête (qui arrive en HTTPS sur 8443).

### 5. Cookies et HSTS

Le cookie de session reste non `Secure` (`SERVER_SERVLET_SESSION_COOKIE_SECURE=false`) : un cookie `Secure` est rejeté en HTTP. Les cookies ne sont pas isolés par port : le kiosque (HTTPS, 8443) et un smartphone (HTTP, 8080) sont des appareils distincts, donc sans effet. HSTS est désactivé dans `AdminSecurityConfig` : un nom de domaine avec HSTS ferait passer `http://hôte:8080` en `https://hôte:8080` et casserait le mobile.

### 6. Clé de kiosque selon le mode

`KioskStartupCheck` remplace le test `prod` par : clé obligatoire si `onprem` ou `remote` est actif, avertissement en `dev`. Les alias `local` et `prod` en héritent via leurs groupes. En `onprem`, le port 8080 est accessible depuis le LAN et la clé empêche une connexion par simple identifiant.

### 7. Docker et Compose

`Dockerfile` : `ENV SPRING_PROFILES_ACTIVE=remote`, `EXPOSE 8080 8443`, `ENTRYPOINT` vers le script, répertoire `/certs` appartenant à l'utilisateur `assistant`. `docker-compose.yml` : ports `${APP_PORT:-8080}:8080` et `${APP_HTTPS_PORT:-8443}:8443`, volume `certs:/certs`, variables `ASSISTANT_HTTPS_*`. L'image reste utilisable sans HTTPS en mettant `SPRING_PROFILES_ACTIVE` à un profil HTTP seul (documenté, mais non cible : Docker est réservé à `remote`).

### 8. Version et documentation

`pom.xml` et `CHANGELOG.md` passent à `1.1.0` (nouvelle fonctionnalité compatible via alias). Le README gagne une section « Modes de déploiement » avec le tableau, les propriétés par mode, la commande de lancement de chaque mode (fichier, variable d'environnement, ligne de commande), l'installation du certificat et la mise en garde HTTP. `.github/copilot-instructions.md` mentionne les nouveaux profils.

## Risks / Trade-offs

- [Certificat auto-signé : avertissement navigateur sur le kiosque] → documenter l'acceptation unique, l'import dans le magasin du système, ou le certificat fourni (PKCS12) ; la webcam fonctionne après acceptation.
- [HTTP côté mobile sur un serveur distant : mot de passe d'administration, cookies et jetons en clair] → risque accepté par décision, mise en garde explicite dans le README ; chemin ultérieur : forcer HTTPS sur `/admin/**` et les appels à clé de kiosque.
- [Stacks existantes en `prod` : bascule implicite vers `remote` avec HTTPS et port 8443] → alias `prod`, `docker-compose.yml` mis à jour (port et volume), mention « BREAKING » dans le CHANGELOG ; sans volume `certs` le certificat est régénéré à chaque redémarrage du conteneur (avertissement dans les logs).
- [Dérivation de l'URL depuis `Host`] → limitée aux appels authentifiés par la clé de kiosque, nom d'hôte validé, URL explicite prioritaire.
- [`keytool` absent de l'image JRE] → vérification en première tâche, repli par copie depuis l'étape de build.
- [Premier démarrage plus long (génération de clé RSA sur Pi 4)] → quelques secondes, une seule fois grâce au volume.
- [Duplication de propriétés entre fichiers d'axe] → un fichier par axe, valeurs par défaut communes dans `application.properties`.
- [Mémoire 4 Go] → TLS et le second connecteur ajoutent un surcoût négligeable ; `Xmx384m` inchangé.

## Migration Plan

1. Déployer la nouvelle image avec le `docker-compose.yml` mis à jour (port 8443 et volume `certs`).
2. Renseigner `ASSISTANT_HTTPS_HOSTNAMES` (IP ou nom du serveur) avant le premier démarrage pour que le certificat contienne la bonne adresse.
3. Ouvrir le kiosque en `https://<hôte>:8443/?kiosk=<clé>` et accepter le certificat une fois ; renseigner `ASSISTANT_PUBLIC_BASE_URL` seulement si le port HTTP public diffère.
4. Retour arrière : réutiliser l'image précédente et l'ancien compose ; le volume `certs` est inutilisé, la base n'a pas changé.

## Open Questions

- Aucune bloquante. À confirmer à l'implémentation : disponibilité de `keytool` dans l'image JRE Temurin 25.
