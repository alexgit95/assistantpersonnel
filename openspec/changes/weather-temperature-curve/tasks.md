## 1. Données et API

- [x] 1.1 Ajouter à `WeatherForecast` la liste `hourly` (`HourlyTemperature` : heure locale ISO, température nullable) et `currentTime` ; adapter les constructeurs existants
- [x] 1.2 Remplir `hourly` et `currentTime` dans `WeatherResponseParser.parseForecast` (indexation par l'heure lue dans la chaîne, valeurs nulles conservées) sans changer le calcul du matin et de l'après-midi
- [x] 1.3 Mettre à jour la documentation Swagger du contrôleur météo et vérifier que le fixture TEST fournit `current.time` et les températures horaires

## 2. Widget

- [x] 2.1 Ajouter le `<svg>` (axe, `<polyline>`, cercles) et la couche d'étiquettes à `weather.html`, sous les jours
- [x] 2.2 Dans `weather.js`, calculer la fenêtre [maintenant − 3 h, maintenant + 24 h] (horloge du navigateur et `utcOffsetSeconds` en mode réel, `currentTime` en mode TEST) et les coordonnées des points
- [x] 2.3 Tracer la courbe : un cercle par heure, points minimum, maximum et courant plus marqués, étiquettes uniquement sur ces trois points, axes X et Y gradués, trous pour les valeurs nulles
- [x] 2.4 Brancher l'animation de tracé et le rejeu au cycle périodique (signature des données, `prefers-reduced-motion`) ; masquer la courbe en mode compact
- [x] 2.5 Ajouter les styles `weather-chart*` dans `home.css` (même style que le poids, hauteur plafonnée, masquage sous un seuil de hauteur)

## 3. Version, tests et documentation

- [x] 3.1 Passer `pom.xml` à la version 0.0.10
- [x] 3.2 Tests Java : parser (24 heures par jour, valeur nulle, `currentTime`), service, test d'intégration de `/api/weather` en mode TEST
- [x] 3.3 Tests JS : fenêtre (28 points, bornée en début de nuit), minimum/maximum/courant et étiquette unique, axes X/Y gradués (cinq valeurs verticales), valeurs nulles, absence de retracé sans changement
- [x] 3.4 Lancer la suite complète (Maven : `C:\USINE_LOGICIELLE\apache-maven\bin` ; JS : `node.exe --test`) et vérifier le rendu dans le navigateur
- [x] 3.5 Mettre à jour `README.md` et `CHANGELOG.md` (section `[0.0.10]` avec « Version du projet portée à 0.0.10 »)
