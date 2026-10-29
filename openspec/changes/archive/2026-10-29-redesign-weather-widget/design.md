## Context

- Le widget affiche aujourd'hui une phrase « maintenant » et une liste de jours (min à max). `OpenMeteoClient` interroge `current` et `daily` (code, max, min) avec `forecast_days` issu de `ASSISTANT_WEATHER_FORECAST_DAYS` (défaut 5). `WeatherResponseParser` produit `WeatherForecast(current, days[date, minC, maxC, description])`. `WeatherCode` ne renvoie que du texte.
- `weather.js` rend un widget « compact » pour les emplacements secondaires (`slotIndex !== 0`) et un widget complet pour le principal. Les textes sont écrits par `typeText`, rechargés par `startRetypeLoop`.
- Le mode TEST lit `docs/response-open-meteo.json` via `TestWeatherGateway` (classpath `integration/response-open-meteo.json`). Ce fixture n'a pas de bloc `hourly`.
- Contraintes : thème noir OLED/blanc, aucun défilement sur l'accueil, Raspberry Pi 4 Go, interface en français, tutoiement.

## Goals / Non-Goals

**Goals:**
- Carré du jour (matin, après-midi, risque de pluie, rafales, conseil) et trois lignes pour les jours suivants, selon la maquette validée.
- Pictos SVG monochromes, sans dépendance à une police emoji.
- Phrase de conseil déterministe et testable.
- Supprimer entièrement `ASSISTANT_WEATHER_FORECAST_DAYS`.

**Non-Goals:**
- Prévisions au-delà de 4 jours ou durée configurable.
- Icônes de nuit, heure de lever et coucher du soleil, humidité, vitesse du vent actuel.
- Nouvelle table ou modification de base de données.

## Decisions

1. **Données Open-Meteo.** La requête devient `current=temperature_2m,apparent_temperature,weather_code`, `hourly=temperature_2m,weather_code,precipitation_probability,wind_gusts_10m` et `daily=precipitation_probability_max,wind_gusts_10m_max`, avec `forecast_days=4` en constante et `timezone=auto`. Les valeurs `daily.weather_code`, `temperature_2m_max/min` ne sont plus utilisées. Alternative écartée : ne garder que `daily` et déduire matin/après-midi, impossible sans données horaires.
2. **Agrégation côté serveur.** `WeatherResponseParser` construit pour chaque jour une `DayPart` (matin, après-midi) : température (min de 6-12 h, max de 12-18 h) et code le plus sévère de la plage. Une table de sévérité des codes WMO fait le choix (orage > neige > pluie/averse/bruine > brouillard > couvert > peu nuageux > dégagé). Alternative écartée : agréger dans le navigateur, qui doublerait la logique et compliquerait les tests.
3. **Modèle.** `WeatherForecast.ForecastDay(date, morning, afternoon, rainChancePercent, maxGustKmh, windAlert, advice)` avec `DayPart(temperatureC, code, description)`. `CurrentConditions` ne garde que température, ressenti et description. Le champ `code` (WMO) est exposé pour que le front choisisse le pictogramme ; `description` sert de libellé accessible.
4. **Conseil.** Classe `WeatherAdvice` (Java pur) avec les règles et leur priorité, seuils en constantes (pluie 50 %, rafales 50 km/h, froid 5 °C, veste 12 °C, chaleur 28 °C). Elle est appelée par le parser et testée unitairement. Alternative écartée : génération dans `weather.js`, plus difficile à tester côté CI Java.
5. **Pictos.** Un sprite SVG (`/img/weather-icons.svg`, symboles référencés par `<use>`) en trait blanc (`stroke: currentColor`, `fill: none`), regroupant soleil, peu nuageux, couvert, brouillard, pluie, neige, orage, vent et un pictogramme neutre. Une table JS associe code WMO et identifiant de symbole ; `aria-label` et `<title>` portent le libellé français. Alternative écartée : emojis, dépendants de la police du Pi et colorés.
6. **Front.** `weather.html` est restructuré (carré du jour, liste de trois jours). `weather.js` conserve `compact` : en secondaire, la liste des jours est masquée et seul le carré est rendu et réécrit. `typeText` s'applique aux textes ; les symboles SVG sont remplacés directement. Un `date` de `days[0]` différent de la date locale du lieu provoque un rechargement au lieu d'afficher un « aujourd'hui » périmé.
7. **Suppression de `forecastDays`.** Retrait de `AssistantProperties.Weather.forecastDays`, de `assistant.weather.forecast-days`, du paramètre `forecastDays` de `WeatherGateway.forecast` et de ses implémentations, du `docker-compose.yml`, du README et des tests. La durée devient la constante `FORECAST_DAYS = 4` dans le client Open-Meteo.
8. **Fixture.** `docs/response-open-meteo.json` reçoit un bloc `hourly` sur 4 jours avec des valeurs variées (pluie, rafales de plus de 50 km/h un jour, jour calme) pour couvrir les cas ; la copie classpath de test est alignée. Les `daily` inutiles sont conservés ou retirés selon ce que le parser lit réellement.

## Risks / Trade-offs

- [Réponse plus volumineuse] → environ 96 valeurs par paramètre horaire, négligeable, cache 30 min inchangé.
- [Heures manquantes dans `hourly`] → une plage sans données renvoie un état indisponible pour le jour concerné plutôt que des valeurs inventées.
- [Fuseaux et « aujourd'hui » périmé après minuit avec le cache] → le premier jour est comparé à la date locale du lieu côté front et le serveur invalide un cache dont le premier jour est passé.
- [Table de sévérité WMO incomplète] → code inconnu : pictogramme neutre et description « Conditions inconnues ».
- [Suppression d'une variable d'environnement] → l'ancienne valeur est ignorée sans erreur ; documenté dans README et CHANGELOG (BREAKING).
- [Emplacement secondaire très petit] → le carré peut être tronqué sur de très petits écrans ; il hérite des règles de débordement existantes et la phrase de conseil est coupée avant la température.

## Migration Plan

1. Déployer l'image : aucune migration de données.
2. Les déploiements qui définissent `ASSISTANT_WEATHER_FORECAST_DAYS` peuvent la retirer ; elle est ignorée.
3. Retour arrière : redéployer l'image précédente.

## Open Questions

Aucune.
