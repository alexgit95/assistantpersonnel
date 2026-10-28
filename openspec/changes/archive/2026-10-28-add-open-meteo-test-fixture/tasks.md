## 1. Fixture et parsing

- [x] 1.1 Créer `docs/response-open-meteo.json` avec Paris, une réponse de géocodage réelle au format Open-Meteo et une réponse de prévision actuelle + cinq jours
- [x] 1.2 Extraire le parsing des prévisions d'`OpenMeteoClient` dans `WeatherResponseParser` partagé
- [x] 1.3 Charger et valider le fixture une seule fois dans `TestWeatherGateway`; recherche toujours Paris quel que soit le texte et prévision toujours Paris quelle que soit la localisation passée
- [x] 1.4 Tests JUnit du parser et de la gateway TEST : réponses cohérentes, ville fixe, mode TEST et absence d'appel HTTP

## 2. Mode TEST et packaging

- [x] 2.1 Vérifier que l'activation du mode TEST ne modifie pas la ville enregistrée; conserver le flux explicite de sélection/enregistrement
- [x] 2.2 Vérifier que le badge MODE TEST et le widget affichent Paris avec les prévisions du fixture, puis le lieu réel après désactivation
- [x] 2.3 Embarquer la fixture dans le JAR, le contexte Docker et l'image; ajuster `.dockerignore` sans inclure les autres documents
- [x] 2.4 Tests d'intégration : saisie d'une autre ville renvoie Paris en TEST, aucun appel réseau, config de profil préservée par le simple basculement

## 3. Documentation et validation

- [x] 3.1 Mettre à jour `README.md` et `CHANGELOG.md` pour décrire le fixture météo et le mode TEST
- [x] 3.2 Augmenter la version dans `pom.xml` et ajouter simultanément l'entrée correspondante au `CHANGELOG.md`
- [x] 3.3 Exécuter les tests Maven avec `C:\USINE_LOGICIELLE\apache-maven\bin` et les tests JS
- [x] 3.4 Valider la change OpenSpec
