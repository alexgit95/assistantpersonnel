## 1. Vérifications préalables

- [x] 1.1 Tester avec la clé PRIM réelle le préfixe `STIF:StopPoint:Q:<arrid>:` sur un arrêt de bus, métro, RER et tram, et consigner le résultat dans le design
- [x] 1.2 Copier `docs/reponse-prochain-passage.json` en ressource embarquée `integration/` pour le mode TEST

## 2. Recherche d'arrêts

- [x] 2.1 Créer le client de recherche (API publique `arrets/records`, `select`, `limit=20`, via `OutboundHttp`, timeouts courts)
- [x] 2.2 Valider et échapper le texte saisi (≥ 3 caractères), cache mémoire borné avec TTL court
- [x] 2.3 Tests JUnit 5 + Mockito : requête valide, texte court, guillemets, API indisponible

## 3. Prochains passages (back)

- [x] 3.1 Créer le client PRIM `stop-monitoring` (en-tête `apikey` via `PrimKeyService`) et le parseur (heure d'arrivée sinon départ, tri, passages écoulés ignorés, 4 premiers, ligne, destination)
- [x] 3.2 Créer la passerelle de test : recalage aléatoire des horaires dans le futur, `Random` injectable
- [x] 3.3 Créer le service : cache ~55 s par arrêt borné, fusion des demandes concurrentes, dernière valeur connue, budget `ApiQuotaService`, `QuietHours`, clé absente, mode TEST, arrêt indisponible
- [x] 3.4 Tests JUnit 5 + Mockito : parseur, passerelle de test, cache et concurrence, budget épuisé, pause, clé absente, erreur PRIM, 400 sur arrêt inconnu

## 4. Application et API REST

- [x] 4.1 Créer l'`AssistantApp` `transit-passages` (titre, description, fragment, module JS) distincte de `transit`
- [x] 4.2 Enregistrer les arrêts dans `configJson` du profil (1 à 3, uniques, `arrid` numérique), lecture et suppression
- [x] 4.3 Créer le contrôleur `/api/transit-passages` (recherche, arrêts, passages) exigeant la session faciale, avec `@Tag`, `@Operation`, `@ApiResponse`
- [x] 4.4 Tests JUnit 5 + Mockito : contrôleur, limite de 3, doublon, identifiant invalide, accès sans session

## 5. Widget front

- [x] 5.1 Créer le fragment Thymeleaf et le module `static/js/widgets/transit-passages.js`
- [x] 5.2 Interface de configuration : recherche avec debounce, résultats nom/ville/type, ajout et retrait (3 max)
- [x] 5.3 Affichage des 4 passages « dans X min », ligne, destination, heure de mise à jour, messages (clé absente, pause, budget, indisponible, mode TEST)
- [x] 5.4 Actualisation toutes les 60 s uniquement session ouverte, arrêt à la fermeture ou au retrait du widget
- [x] 5.5 Tests JS existants (`src/test/js`) pour le calcul des minutes et le cycle d'actualisation

## 6. Documentation et validation

- [x] 6.1 Mettre à jour `README.md` et `CHANGELOG.md` (Added)
- [x] 6.2 Exécuter la suite de tests Maven via `C:\USINE_LOGICIELLE\apache-maven\bin` et corriger les échecs
- [x] 6.3 Valider manuellement en mode TEST puis en mode réel avec la clé PRIM
