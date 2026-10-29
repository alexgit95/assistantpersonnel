## 1. Suppression de forecastDays

- [x] 1.1 Retirer `forecastDays` de `AssistantProperties.Weather` et `assistant.weather.forecast-days` de `application.properties`
- [x] 1.2 Retirer le paramètre `forecastDays` de `WeatherGateway.forecast` et de `OpenMeteoClient`, `TestWeatherGateway`, `ModeAwareWeatherGateway`, `WeatherService` ; introduire la constante `FORECAST_DAYS = 4`
- [x] 1.3 Retirer `ASSISTANT_WEATHER_FORECAST_DAYS` de `docker-compose.yml` et du tableau du README
- [x] 1.4 Corriger les tests qui construisent `AssistantProperties.Weather` (`Fixtures`, `WeatherServiceTest`) et toute référence restante (recherche globale `forecastDays`, `forecast-days`, `FORECAST_DAYS`)

## 2. Données et modèle

- [x] 2.1 Modifier la requête Open-Meteo (`current`, `hourly`, `daily`, `forecast_days=4`)
- [x] 2.2 Étoffer `docs/response-open-meteo.json` avec un bloc `hourly` sur 4 jours (pluie, rafales de plus de 50 km/h un jour, jour calme) et aligner la copie classpath
- [x] 2.3 Remplacer `ForecastDay` et `CurrentConditions` par le nouveau modèle (`DayPart` matin/après-midi, risque de pluie, rafales, `windAlert`, `advice`, code WMO)
- [x] 2.4 Adapter `WeatherResponseParser` : plages 6-12 h et 12-18 h, code le plus sévère, risque de pluie, rafales, état indisponible si heures manquantes
- [x] 2.5 Créer `WeatherAdvice` avec les règles et constantes de seuil, et l'appeler depuis le parser
- [x] 2.6 Adapter `TestWeatherGateway` et le DTO/Swagger de `WeatherController`
- [x] 2.7 Invalider un cache de prévision dont le premier jour est antérieur à la date locale du lieu

## 3. Front

- [x] 3.1 Créer le sprite SVG monochrome (soleil, peu nuageux, couvert, brouillard, pluie, neige, orage, vent, neutre) et la table code WMO vers symbole
- [x] 3.2 Restructurer `weather.html` : carré du jour et liste de trois jours
- [x] 3.3 Réécrire `weather.js` : rendu du carré, des trois lignes en principal, du carré seul en secondaire, libellés accessibles, rechargement si « aujourd'hui » est périmé
- [x] 3.4 Mettre à jour `home.css` pour le carré, les lignes, les pictos et la variante compacte, sans défilement sur l'accueil
- [x] 3.5 Aligner la réécriture périodique : textes réécrits, pictos remplacés sans animation, échec silencieux

## 4. Tests

- [x] 4.1 Tests de `WeatherAdvice` : une règle par seuil, priorité, combinaison pluie et fraîcheur, message neutre
- [x] 4.2 Tests de `WeatherResponseParser` : agrégation matin/après-midi, code le plus sévère, rafales à 50 et 62 km/h, heures manquantes
- [x] 4.3 Tests de `WeatherService`, `TestWeatherGateway`, `OpenMeteoClient` et `WeatherController` adaptés au nouveau modèle et à la durée figée
- [x] 4.4 Tests JS (`node --test src/test/js/*.test.mjs`) pour la table code vers pictogramme et le rendu principal/secondaire si la logique est extraite
- [x] 4.5 Lancer `mvn clean test` avec le Maven de `C:\USINE_LOGICIELLE\apache-maven\bin`

## 5. Documentation

- [x] 5.1 Mettre à jour le README (nouvel affichage, durée de 4 jours, variable supprimée)
- [x] 5.2 Mettre à jour `CHANGELOG.md` (Added, Changed, mention BREAKING de la variable supprimée)
