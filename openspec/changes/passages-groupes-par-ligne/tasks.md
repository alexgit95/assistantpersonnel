## 1. Serveur : regroupement par ligne

- [x] 1.1 Dans `NextPassagesService`, remplacer `PASSAGES_FEW_STOPS`, `PASSAGES_MANY_STOPS`, `FEW_STOPS_MAX` et `passagesPerStop` par une constante de 3 passages par ligne et une règle de lignes par arrêt selon le nombre d'arrêts enregistrés (1 arrêt : 4 lignes, 2 arrêts : 2 lignes, 3 arrêts : 1 ligne)
- [x] 1.2 Dans `view`, regrouper les passages futurs par `lineId` en conservant l'ordre de premier passage, garder 3 passages par ligne puis les N premières lignes, et renvoyer la liste plate ordonnée par blocs ; compter les arrêts indisponibles dans le nombre d'arrêts
- [x] 1.3 Ajouter `updatedAt` à `Overview` (la plus ancienne des heures de mise à jour des arrêts ayant des données, `null` sinon) et conserver `updatedAt` par arrêt ; adapter `TransitPassagesController` et sa documentation Swagger (`@Operation`, `@ApiResponse`)
- [x] 1.4 Adapter le critère de conservation des horaires simulés de `testEntry` au nouveau besoin (assez de passages futurs pour remplir les lignes affichées)
- [x] 1.5 Dans `toView`, renseigner `PassageView.line` avec le nom court du catalogue (`TransitLine.shortName`), avec repli sur l'identifiant de ligne si la ligne est absente du catalogue ; ne pas modifier le libellé du widget Transports

## 2. Données de test

- [x] 2.1 Vérifier `docs/reponse-prochain-passage.json` et l'enrichir si besoin pour couvrir au moins quatre lignes distinctes ; ajuster `MockPassageGateway` (`VISITS`) pour que chaque ligne ait au moins 3 passages à venir
- [x] 2.2 Vérifier que le jeu de lignes du catalogue permet d'afficher un libellé pour ces lignes en mode TEST

## 3. Tests Java

- [x] 3.1 `NextPassagesServiceTest` : regroupement par ligne sans entrelacement, tri des blocs par premier passage, 3 passages par ligne, ligne rare non masquée, ligne à moins de 3 passages sans complément, pastille limitée à l'identifiant court (lettre ou numéro) avec repli sur `lineId`
- [x] 3.2 `NextPassagesServiceTest` : plafonds de 4, 2 et 1 ligne selon 1, 2 et 3 arrêts (arrêt indisponible compté), heure de départ de repli, passage écoulé, arrêt sans passage
- [x] 3.3 `NextPassagesServiceTest` et `TransitPassagesControllerTest` : `updatedAt` de la vue d'ensemble égal à la plus ancienne heure, `null` sans données, réponse documentée dans Swagger
- [x] 3.4 `MockPassageGatewayTest` : au moins quatre lignes avec au moins 3 passages à venir chacune
- [x] 3.5 Point de contrôle : `mvn clean test` vert (Maven de `C:\USINE_LOGICIELLE\apache-maven\bin`)

## 4. Navigateur

- [x] 4.1 `transit-passages.js` : afficher les rangées dans l'ordre reçu avec la pastille répétée à chaque rangée, supprimer l'heure par arrêt, ajouter `formatPassagesUpdatedAt` au format `Mis à jour HH:MM` (Europe/Paris, sans date) alimenté par `overview.updatedAt`
- [x] 4.2 `fragments/widgets/transit-passages.html` : ajouter l'élément de l'heure de mise à jour en bas du widget
- [x] 4.3 `home.css` : augmenter l'espacement vertical entre les rangées et entre les sections d'arrêt, sans changer les tailles de police des emplacements secondaires
- [x] 4.4 `transit-passages-widget.test.mjs` : adapter les tests (ordre des rangées, pastille répétée, heure unique `HH:MM`, absence d'heure par arrêt, `updatedAt` absent) et lancer `node --test src/test/js/*.test.mjs`

## 5. Documentation, validation et finalisation

- [x] 5.1 Mettre à jour le `README.md` (description du widget Prochains passages : regroupement par ligne, plafonds de lignes, heure unique)
- [x] 5.2 Mettre à jour le `CHANGELOG.md` (`[Added]`, `[Changed]`, `[Fixed]`) avec l'impact technique et fonctionnel
- [ ] 5.3 Vérifier à l'écran les plafonds de 4, 2 et 1 ligne sur l'emplacement principal et sur un emplacement secondaire (mode TEST) et ajuster les constantes si le contenu est coupé
- [x] 5.4 Vérifier qu'aucune modification de `docs/database.puml` n'est nécessaire et lancer `openspec validate passages-groupes-par-ligne`
