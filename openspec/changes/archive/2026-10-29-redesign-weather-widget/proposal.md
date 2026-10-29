## Why

Le widget Météo affiche une phrase dense et cinq jours de min/max sans pictogramme : difficile à lire d'un coup d'œil. On veut un carré « aujourd'hui » lisible (matin, après-midi, risque de pluie, conseil pratique) et trois lignes pour les jours suivants. La variable `ASSISTANT_WEATHER_FORECAST_DAYS` n'a plus de raison d'être, la durée étant figée.

## What Changes

- Emplacement principal : un carré pleine largeur pour aujourd'hui (ville, « Maintenant » avec température et ressenti, température et picto du matin et de l'après-midi, risque de pluie du jour, phrase de conseil), suivi de trois lignes pour les trois jours suivants (température et picto du matin et de l'après-midi).
- Emplacement secondaire : uniquement le carré d'aujourd'hui.
- Matin = mini de 6 h à 12 h, après-midi = maxi de 12 h à 18 h, picto = code météo le plus sévère de la plage.
- Pictos SVG monochromes en trait blanc (soleil, peu nuageux, couvert, brouillard, pluie ou bruine, neige, orage) et picto vent quand des rafales dépassent 50 km/h dans la journée.
- Phrase de conseil générée côté serveur, au tutoiement (parapluie, froid, veste, chaleur, neige, orage, rafales).
- Nouvelles données Open-Meteo : `hourly` (température, code, probabilité de précipitations, rafales) et `daily.precipitation_probability_max` / `wind_gusts_10m_max` ; le fixture `docs/response-open-meteo.json` est étoffé en conséquence.
- Le ressenti, l'humidité et le vent actuels ne sont plus affichés, sauf le ressenti dans « Maintenant ».
- **BREAKING** : la durée des prévisions est figée à 4 jours (aujourd'hui + 3) ; `ASSISTANT_WEATHER_FORECAST_DAYS`, `assistant.weather.forecast-days` et tout ce qui s'y rattache sont supprimés (propriété, paramètre `forecastDays` des passerelles, `docker-compose.yml`, README, tests).

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `app-weather`: nouvel affichage (carré du jour, trois jours suivants, pictos, risque de pluie, rafales, phrase de conseil), 4 jours figés, ressenti actuel conservé.
- `widget-periodic-refresh`: la réécriture périodique couvre les nouveaux textes ; les pictos ne sont pas animés.

## Impact

- Java : `WeatherForecast` (nouveaux champs), `WeatherResponseParser`, `OpenMeteoClient`, `TestWeatherGateway`, `ModeAwareWeatherGateway`, `WeatherGateway`, `WeatherService`, `WeatherController` (DTO et Swagger), `AssistantProperties.Weather`, nouveau générateur de conseil.
- Front : `weather.html`, `weather.js`, `home.css`, sprite SVG des pictos.
- Configuration : `application.properties`, `docker-compose.yml`, `README.md`, `CHANGELOG.md`.
- Données : `docs/response-open-meteo.json` (et copie de test si présente) ; aucun changement de base de données.
- Réseau : réponse Open-Meteo plus volumineuse (hourly sur 4 jours), cache de 30 min inchangé.
