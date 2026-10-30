## Why

Le widget Météo du bloc central ne montre que des valeurs ponctuelles (matin, après-midi, jours suivants). Une courbe de température heure par heure, dans le style du graphique du poids, donne d'un coup d'œil l'évolution des températures autour de l'instant présent.

## What Changes

- Widget Météo, bloc central uniquement : sous le contenu existant, une courbe des températures horaires de maintenant − 3 h à maintenant + 24 h (28 points au plus, un par heure).
- Un cercle par heure, étiquettes sur le minimum, le maximum et « maintenant », axe X gradué toutes les 6 h et axe Y gradué en °C avec guides horizontaux pour estimer les températures intermédiaires.
- Même style et mêmes effets que le graphique du poids : ligne blanche arrondie tracée à l'écran, retracée à chaque cycle périodique du widget, sans animation avec `prefers-reduced-motion`.
- Entre 0 h et 3 h, la courbe commence à 0 h (les données démarrent à 0 h aujourd'hui, pas de `past_days`).
- API : `/api/weather` expose les températures horaires (avec leur heure) et l'heure courante des données. La requête Open-Meteo et le calcul du matin et de l'après-midi ne changent pas.
- Mode TEST : la fenêtre est ancrée sur l'heure courante du fixture.
- Les emplacements secondaires (mode compact) n'affichent pas la courbe.
- Version du projet portée à 0.0.10 (`pom.xml`) et changelog.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-weather`: courbe des températures horaires dans le bloc central, et données horaires exposées par l'API.

## Impact

- Code : `WeatherForecast` (températures horaires et heure courante), `WeatherResponseParser`, `weather.html`, `weather.js`, `home.css`, documentation Swagger du contrôleur météo.
- Tests : `WeatherResponseParserTest`, `WeatherServiceTest`, nouveau test JS de la courbe, test d'intégration de l'API.
- Documentation : `README.md`, `CHANGELOG.md`, `pom.xml` (0.0.10).
- Aucun changement de schéma de base de données ni de dépendance ; aucun appel externe supplémentaire (les données horaires sont déjà demandées).
