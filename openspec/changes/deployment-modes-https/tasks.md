## 1. Profils et modes de déploiement

- [x] 1.1 Remplacer `spring.profiles.default=local` par `dev` et déclarer les groupes `dev`, `onprem`, `remote` et les alias `local` → `dev`, `prod` → `remote` (`spring.profiles.group.*`)
- [x] 1.2 Créer les fichiers d'axe `application-sqlite.properties`, `application-postgres.properties`, `application-assets-cdn.properties`, `application-http-only.properties`, `application-https-dual.properties`, `application-qr-dev.properties` et supprimer `application-local.properties` et `application-prod.properties` en reportant leurs valeurs
- [x] 1.3 Remplacer le test du profil `prod` de `KioskStartupCheck` par « clé obligatoire en `onprem` et `remote`, avertissement en `dev` » (les alias en héritent)
- [x] 1.4 Vérifier par recherche (`"prod"`, `"local"`, `@ActiveProfiles`, `Profiles.of`) qu'aucun autre code ou test ne dépend des anciens noms de profil et les adapter
- [x] 1.5 Tests unitaires : démarrage sans profil (`dev`), `onprem` et `remote` (valeurs par défaut de chaque axe), alias `local` et `prod`, clé de kiosque obligatoire ou non selon le mode
- [x] 1.6 Point de contrôle : tests Maven verts, application démarrée en `dev` et en `onprem` (SQLite, HTTP seul), commit (tests verts, dev et onprem démarrés ; commit laissé à l'utilisateur)

## 2. Propriétés HTTP et HTTPS

- [x] 2.1 Ajouter dans `AssistantProperties` `assistant.http.*` (`enabled`, `port` 8080, `public-port` égal au port par défaut) et `assistant.https.*` (`enabled`, `port` 8443, `keystore-path`, `keystore-type` PKCS12, `keystore-password`, `hostnames`, `validity-days` 825) avec validations (ports 1 à 65535, mot de passe et chemin cohérents quand HTTPS est actif)
- [x] 2.2 Relier `server.port`, `server.ssl.bundle` et `spring.ssl.bundle.jks.kiosk.*` aux propriétés selon l'axe `https-dual`, et `server.port` à `assistant.http.port` en `http-only`
- [x] 2.3 Désactiver HSTS dans `AdminSecurityConfig` et conserver `server.servlet.session.cookie.secure=false` par défaut
- [x] 2.4 Tests unitaires : validation des propriétés, absence d'en-tête `Strict-Transport-Security`

## 3. Connecteur HTTP additionnel

- [x] 3.1 Créer un `WebServerFactoryCustomizer<TomcatServletWebServerFactory>` qui ajoute un connecteur HTTP sur `assistant.http.port` quand `assistant.http.enabled` et `assistant.https.enabled` sont vrais
- [x] 3.2 Refuser le démarrage avec un message explicite si le keystore fourni est illisible ou si les deux ports sont identiques
- [x] 3.3 Test d'intégration : en profil `remote` avec un keystore de test, HTTP et HTTPS répondent ; en `dev` seul HTTP répond
- [x] 3.4 Point de contrôle : tests Maven verts, démarrage `remote` local avec keystore de test (HTTPS sur 8443, HTTP sur 8080), commit (vérifié par DualConnectorIntegrationTest sur SQLite, PostgreSQL absent en local ; commit laissé à l'utilisateur)

## 4. URL des QR

- [x] 4.1 Adapter `QrUrlResolver` : URL explicite, inversion `localhost` ⇄ `127.0.0.1` en `dev`, dérivation `http://<hôte>:<public-port>` en `remote` pour un hôte non bouclé, sinon QR désactivés avec avertissement (message distinct en `onprem`)
- [x] 4.2 Valider l'hôte reflété (nom DNS, IPv4, IPv6 entre crochets) et ne dériver que sur les appels portant une clé de kiosque valide
- [x] 4.3 Tests unitaires et MockMvc : URL explicite prioritaire, bascule `dev`, dérivation `remote`, port public différent, `onprem` sans URL, hôte invalide, appel sans clé refusé en 403

## 5. Image Docker et Compose

- [x] 5.1 Vérifier la présence de `keytool` dans `eclipse-temurin:25-jre` (sinon le copier depuis l'étape `jdk`) (Docker absent en local : garde `keytool -help` ajoutée au Dockerfile, le build échouera explicitement si l'outil manque)
- [x] 5.2 Créer `docker-entrypoint.sh` : génération du PKCS12 avec SAN (`localhost`, `127.0.0.1`, `ASSISTANT_HTTPS_HOSTNAMES`), idempotence, mot de passe issu de `ASSISTANT_HTTPS_KEYSTORE_PASSWORD` ou généré (droits `600`, non journalisé), repli quand un keystore est fourni, `exec java -jar`
- [x] 5.3 Adapter le `Dockerfile` : `SPRING_PROFILES_ACTIVE=remote`, `EXPOSE 8080 8443`, copie du script, répertoire `/certs` appartenant à l'utilisateur `assistant`, avertissement si `/certs` n'est pas un volume
- [x] 5.4 Adapter `docker-compose.yml` : ports `${APP_PORT:-8080}:8080` et `${APP_HTTPS_PORT:-8443}:8443`, volume `certs:/certs`, variables `ASSISTANT_HTTP_*` et `ASSISTANT_HTTPS_*`, profil par défaut `remote`
- [ ] 5.5 Construire l'image et vérifier : certificat généré au premier démarrage, conservé au redémarrage, SAN corrects (`keytool -list -v`), HTTPS et HTTP joignables ; la GitHub Action reste inchangée
- [ ] 5.6 Point de contrôle : stack Compose démarrée avec PostgreSQL, commit

## 6. Documentation, version et finalisation

- [x] 6.1 README : section « Modes de déploiement » avec le tableau (dev, onprem, remote), la commande de lancement de chaque mode (fichier, variable d'environnement, argument), les propriétés à valoriser par mode avec leur rôle, les alias `local` et `prod`
- [x] 6.2 README : lancement du kiosque par mode, acceptation du certificat auto-signé (navigateur, magasin du système, certificat fourni), mise en garde sur le HTTP côté mobile (mot de passe d'administration en clair), mise à jour du tableau des variables et de la section réseau
- [x] 6.3 Passer `pom.xml` à la version `1.1.0`
- [x] 6.4 Mettre à jour `CHANGELOG.md` (entrée `1.1.0` avec `[Added]`, `[Changed]`, `[Fixed]`, mention **BREAKING** pour l'alias `prod` et le volume `certs`) avec l'impact technique et fonctionnel
- [x] 6.5 Mettre à jour `.github/copilot-instructions.md` (profils `dev`, `onprem`, `remote`) et la section de `docs/parcours-qrcode-smartphone.md` qui cite `localhost` et le profil `local`
- [x] 6.6 Vérifier la couverture des tests (JUnit 5 + Mockito), la compatibilité SQLite et PostgreSQL inchangée et l'absence de modification de `docs/database.puml`
- [ ] 6.7 Valider le changement (`openspec validate deployment-modes-https`) et exécuter la vérification manuelle des trois modes (navigateur et smartphone) (`openspec validate` fait, vérification manuelle des trois modes à faire)
- [ ] 6.8 Poser le tag `v1.1.0` après validation pour produire l'image versionnée (à poser par l'utilisateur)
