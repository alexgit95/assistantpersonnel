## Context

Le projet route déjà les appels météo via `ModeAwareWeatherGateway`. `TestWeatherGateway` fabrique aujourd'hui Paris, Lyon et des prévisions synthétiques en mémoire; la recherche ne retourne que la ville dont le nom contient la saisie et la prévision réutilise la localisation passée, ce qui peut désaccorder l'identité de la ville et les données de test. Le mode TEST est un réglage admin persistant; les caches météo sont déjà séparés par mode.

## Goals / Non-Goals

**Goals:**
- Utiliser un fichier canonique `docs/response-open-meteo.json` pour la ville de test Paris, le géocodage et les prévisions.
- Toujours retourner Paris en recherche TEST, indépendamment de la saisie, et afficher ses prévisions cohérentes quel que soit le lieu enregistré au profil.
- Ne faire aucun appel HTTP Open-Meteo en mode TEST et conserver intact le comportement en mode réel.
- Embarquer la fixture dans le JAR et l'image Docker.

**Non-Goals:**
- Modifier les endpoints de production, le cache météo ou la géolocalisation Open-Meteo en mode réel.
- Écraser la localisation du profil au moment où l'administrateur active le mode TEST.
- Modifier le schéma de base ou les pipelines CI/CD.

## Decisions

- **Fixture unique regroupée** : `docs/response-open-meteo.json` contient `location`, `geocodingResponse` au format `/v1/search` et `forecastResponse` au format `/v1/forecast`. La réponse de géocodage contient uniquement Paris (ID 2988507, coordonnées 48.8566/2.3522); la réponse de prévision comprend les champs `current` et `daily` requis par le widget sur cinq jours.
- **Parser partagé** : extraire le parsing des prévisions d'`OpenMeteoClient` dans un composant commun (`WeatherResponseParser`) utilisé à la fois par le client réel et `TestWeatherGateway`. Ainsi la fixture exerce le même mapping que le JSON réel.
- **Recherche TEST déterministe** : `TestWeatherGateway.geocode(query)` ignore `query` et retourne l'unique ville Paris du fixture. Le parcours de configuration existant garde sa règle d'enregistrement : un résultat unique est enregistré via le flux normal de sélection; l'activation du mode TEST seule ne modifie aucun profil.
- **Prévision cohérente** : `TestWeatherGateway.forecast(location, forecastDays)` ignore la localisation du profil, lit la réponse de prévision du fixture et retourne le `WeatherForecast` dont `location` est Paris. Il limite les jours affichés au nombre demandé et disponible dans la fixture. Le mode TEST demeure indiqué dans le DTO.
- **Chargement sans réseau** : le JSON est lu une seule fois depuis `classpath:integration/response-open-meteo.json` et conservé en mémoire sous forme de DTOs légers. Aucun HTTP ni quota n'est consommé. Les caches de `WeatherService`, déjà séparés par mode, restent en place.
- **Packaging** : ajouter le fichier canonique aux ressources Maven, au COPY Docker et aux exceptions précises de `.dockerignore`, sans dupliquer sa source.

## Risks / Trade-offs

- [Fixture non représentative des saisons] → choisir des valeurs lisibles et stables qui couvrent l'état courant et cinq jours; l'objectif est le rendu, pas l'exactitude météo.
- [Ville de recherche différente du texte saisi] → badge MODE TEST permanent dans le widget et message visible sur l'administration.
- [Parsing divergent] → parser commun à la gateway réelle et à la gateway de test, couvert avec le vrai shape Open-Meteo.

## Migration Plan

Aucune migration de données. Le mode réel reste inchangé. En cas de retour arrière du code, le réglage de mode TEST existant continue d'utiliser l'ancien gateway de test codé en dur jusqu'au retour de l'ancienne version.

## Open Questions

Aucune.
