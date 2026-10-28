## Why

Le mode TEST simule déjà les réponses météo, mais les données sont construites en dur dans `TestWeatherGateway`; le résultat n'est pas entièrement déterministe depuis un fichier et peut ne pas correspondre à la ville choisie. Une fixture explicite permettra de vérifier un rendu météo cohérent sans appel externe.

## What Changes

- Ajouter `docs/response-open-meteo.json` comme fixture canonique regroupant la ville Paris, sa réponse de géocodage et une réponse de prévisions au format Open-Meteo.
- En mode TEST, faire renvoyer Paris par la recherche quelle que soit la requête saisie et utiliser les prévisions du même fixture, indépendamment de la localisation déjà enregistrée.
- Ne pas modifier automatiquement la configuration du profil; elle ne change que si l'utilisateur sélectionne et enregistre le résultat de recherche.
- Remplacer les données météo codées en dur par la fixture chargée comme ressource locale, sans requête HTTP.
- Embarquer le fichier dans le JAR et l'image Docker; afficher Paris et le badge MODE TEST dans le widget.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-weather`: le mode TEST force la ville et les prévisions cohérentes du fixture local; le mode réel conserve le géocodage Open-Meteo.
- `app-transit`: préciser que le mode TEST commun utilise aussi le fixture météo local et ne contacte aucune API externe.

## Impact

- Backend : `TestWeatherGateway`, `ModeAwareWeatherGateway`, `WeatherForecast` et leurs tests.
- Ressources/build : nouveau `docs/response-open-meteo.json`, inclusion dans les ressources Maven et Docker, ajustement de `.dockerignore`.
- Frontend : badge MODE TEST et nom de ville cohérent avec les données météo simulées.
- Documentation : `README.md`, `CHANGELOG.md`; incrément de version dans `pom.xml` au moment de l'implémentation.
- Aucun changement de schéma JPA ou PlantUML; aucune modification de CI/CD.
