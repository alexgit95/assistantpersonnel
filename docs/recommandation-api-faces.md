# Recommandation : sécuriser `GET /api/faces`

## 1. Constat

| Élément | État actuel |
|---|---|
| Route | `GET /api/faces` dans `ProfileController` |
| Authentification | Aucune : ni `@RequireSession` ni `@KioskOnly`, et `/api/**` est en `permitAll` |
| Contenu | `id`, `name`, `avatar` (data URL) et `embeddings` (tous les vecteurs de 128 flottants, 8 par profil par défaut), pour tous les profils sauf l'invité (`ProfileService.listFaces`) |
| Seul consommateur | `refreshFaces()` dans `home.js`, donc le navigateur du kiosque. Le smartphone n'appelle pas cette route |
| Usage côté kiosque | Reconnaissance (`classify`) et détection de doublon à l'enrôlement (`enrollment-policy.js`) |
| Cache | `Cache-Control: no-store` : évite la mise en cache mais ne restreint pas l'accès |

Les `id` (entiers séquentiels, `GenerationType.IDENTITY`), noms, avatars et gabarits biométriques fuitent donc ensemble. Tout appareil qui joint le port HTTP peut faire `curl http://<hôte>:8080/api/faces`.

## 2. Décision

Passer la route en `@KioskOnly` (en-tête `X-Kiosk-Key` exigé), comme `/api/auth/login`.

- Le seul appelant légitime est déjà le kiosque, qui détient la clé.
- Le mécanisme existe (`KioskKeyInterceptor`, comparaison en temps constant).
- Modes `onprem` et `remote` : la clé est obligatoire (`KioskStartupCheck` refuse de démarrer sans). Mode `dev` : clé vide = contrôle désactivé, aucun changement pour le développeur.

Alternatives écartées :
- `@RequireSession` : impossible, la route sert à identifier la personne avant toute session.
- Comparaison côté serveur : plus sûr, mais l'empreinte du visage capturé quitterait le navigateur. Cela remet en cause le principe « la biométrie reste dans le navigateur » : refonte, pas correctif.

## 3. Changements à faire

### Backend (`ProfileController`)

```java
@KioskOnly
@Parameter(name = KioskKeyInterceptor.HEADER, in = ParameterIn.HEADER,
        description = "Cle de kiosque (appel reserve au navigateur du Pi)")
@Operation(summary = "Liste des visages connus (reserve au kiosque)",
        description = "Identifiant et empreintes de chaque profil, pour la comparaison cote navigateur du kiosque. "
                + "Les informations de profil sont chargees apres authentification.")
@ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste des profils"),
        @ApiResponse(responseCode = "403", description = "Cle de kiosque absente ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
        @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
@GetMapping("/faces")
```

Retirer le libellé « public sur le LAN ».

### Front (`api.js`)

```js
faces: () => request('GET', '/api/faces', undefined, { kiosk: true }),
```

Sans cette ligne, le kiosque reçoit un 403 que `refreshFaces` avale avec un `console.warn` : la reconnaissance cesse sans erreur visible. Journaliser avec `console.error` ou afficher un état d'erreur clair sur 403.

### Minimiser les données de `/api/faces`

Le moteur de comparaison et la politique de détection des doublons n'utilisent que l'identifiant et les empreintes : `matcher.js` classe avec `id` et `embeddings`, et `enrollment-policy.js` n'a besoin que de `id` pour exclure le profil courant. Le nom et l'avatar ne sont donc pas nécessaires avant la connexion.

- Faire évoluer `FaceView` en `{ id, embeddings }` et ne plus sérialiser le nom ni l'avatar dans `GET /api/faces`.
- Après connexion réussie, appeler `GET /api/profiles/me` (déjà protégé par `@RequireSession`) pour obtenir le nom et l'avatar du seul profil courant. Réutiliser cet appel lors de la restauration d'une session. Effacer ces données à la déconnexion et au changement de profil.
- Ne pas supprimer l'avatar des autres réponses sans vérifier leurs consommateurs : l'administration ou la confirmation d'enrôlement peuvent en avoir besoin. Appliquer le même principe à chaque DTO : ne renvoyer que les champs consommés et autorisés pour ce parcours.

Ainsi, la liste pré-authentification contient encore les empreintes nécessaires à la reconnaissance, mais plus les noms ni les images. `@KioskOnly` reste nécessaire pour protéger les empreintes elles-mêmes.

### Tests

D'après la lecture du code, `SessionFlowIntegrationTest` n'a aucune clé de kiosque configurée : `@KioskOnly` laisse passer et les assertions existantes devraient rester vertes (à confirmer en lançant la suite). À ajouter :

1. `ProfileControllerTest` : avec clé configurée, 403 sans en-tête, 403 avec mauvaise clé, 200 avec la bonne (modèle : `EnrollmentSessionControllerTest`).
2. Test d'intégration avec `assistant.kiosk.key` défini : 403 sans clé ; avec clé, la réponse contient `id` et `embeddings`, mais ni `name` ni `avatar`.
3. Test Swagger : réponse 403 et paramètre d'en-tête documentés pour `/api/faces`.
4. Test JS (`node --test`) : `faces()` joint l'en-tête de kiosque ; le kiosque charge le profil courant via `/api/profiles/me` après login et restauration, et efface ses informations au logout.

### Documentation

- `README.md` : ajouter « liste des visages » aux appels réservés au Pi.
- `CHANGELOG.md` : `[Changed]` (route réservée au kiosque) et `[Fixed]` (fin de l'exposition publique des gabarits biométriques).
- `docs/database.puml` : aucun changement.
- `openspec/` : vérifier qu'aucune spec ne décrit la route comme publique.

## 4. Impact et compatibilité

| Cas | Effet |
|---|---|
| Kiosque `onprem` ou `remote` | Aucun, si `api.js` joint la clé ; nom/avatar chargés après session |
| Développeur en `dev` sans clé | Aucun |
| Smartphone | Aucun |
| Kiosque ouvert sans `?kiosk=<clé>` | La reconnaissance cesse |
| Scripts externes lisant `/api/faces` | Cassés (probablement inexistants) |
| Retour arrière | Retirer l'annotation et la ligne `api.js`, aucune donnée touchée |

## 5. Corrigé / non corrigé

**Corrigé :** un appareil du LAN sans la clé ne peut plus lire la liste des gabarits. Même le kiosque autorisé ne reçoit plus les noms ni les avatars de tous les profils dans cette liste ; il ne charge que les informations du profil connecté via une route de session.

**Non corrigé :**
1. La clé est un secret unique partagé : quiconque l'obtient récupère tout. Utiliser une clé longue et aléatoire (32 caractères ou plus) et prévoir sa rotation. Elle transite par l'URL `?kiosk=<clé>` au premier chargement.
2. Le kiosque détient toutes les empreintes en mémoire : un miroir compromis physiquement les expose. Le nom et l'avatar du profil courant restent présents pendant la session.
3. `POST /api/profiles` et les routes d'écriture : même catégorie de risque, à auditer séparément.
4. Pas de limitation de débit sur les essais de clé.
5. Le mode `dev` reste ouvert par conception.

## 6. Améliorations complémentaires

- Auditer les autres réponses API contenant un avatar et le retirer uniquement quand aucun écran autorisé ne l'utilise ; en particulier, vérifier les réponses de création et de mise à jour des profils.
- Réduire le nombre d'empreintes envoyées par profil (sous-ensemble ou moyenne) : évolution de `matcher.js` et recalibrage des seuils à évaluer avec précaution.
- Journaliser les 403 sur cette route pour détecter un balayage du réseau.
- Chiffrer les gabarits au repos en base si le risque de fuite du fichier SQLite ou du dump préoccupe.

## 7. Plan d'exécution

1. Ajouter `@KioskOnly` et la documentation Swagger sur `faces()`.
2. Réduire `FaceView` à `id` et `embeddings` ; charger le profil courant avec `/api/profiles/me` après login/restauration et effacer ses informations au logout/bascule.
3. Passer `{ kiosk: true }` dans `api.js` et rendre le 403 visible.
4. Ajouter les tests (contrôleur, DTO sans nom/avatar, intégration avec clé, Swagger, JS et cycle de chargement du profil).
5. Lancer `mvn clean test` (`C:\USINE_LOGICIELLE\apache-maven\bin`) puis `node --test src/test/js/*.test.mjs`.
6. Mettre à jour README et CHANGELOG.
7. Vérifier manuellement : kiosque avec clé (reconnaissance et avatar du profil connecté OK), `curl` sans clé (403), `curl` avec clé (200 sans nom/avatar).
8. Ouvrir un second chantier : audit de `POST /api/profiles` et des routes d'écriture.
