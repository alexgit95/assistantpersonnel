# Purpose

Define profile-specific weather location selection, forecasts, caching, and provider failure behavior.

## Requirements

### Requirement: Météo actuelle et prévisions
En mode réel, le système SHALL afficher la météo actuelle et celle des prochains jours (5 par défaut) pour la localisation configurée du profil. En mode TEST, il MUST afficher la météo actuelle et les prévisions Paris du fichier `docs/response-open-meteo.json`, même si une autre ville est enregistrée au profil.

#### Scenario: Affichage en mode réel
- **WHEN** le profil ouvre l'application météo en mode réel
- **THEN** les conditions actuelles et les prévisions de sa localisation enregistrée sont affichées

#### Scenario: Affichage en mode TEST avec une autre ville enregistrée
- **WHEN** le profil a une autre ville configurée et ouvre la météo en mode TEST
- **THEN** le widget affiche Paris et les prévisions correspondantes du fixture
#### Scenario: Fixture indisponible
- **WHEN** le fixture météo local est illisible ou invalide en mode TEST
- **THEN** le widget affiche une erreur d'indisponibilité sans appeler Open-Meteo

### Requirement: Localisation par nom de ville
En mode réel, le profil SHALL définir sa localisation en saisissant un nom de ville ; le système la résout en coordonnées via le géocodage Open-Meteo et enregistre ville, pays et coordonnées dans la configuration de l'application du profil. En mode TEST, la recherche MUST retourner l'unique ville Paris du fixture quel que soit le texte saisi. Le basculement global vers le mode TEST MUST NOT modifier la localisation déjà enregistrée au profil.

#### Scenario: Ville unique
- **WHEN** le profil saisit « Lyon » en mode réel et qu'un seul résultat est pertinent
- **THEN** la ville est enregistrée avec ses coordonnées

#### Scenario: Ville ambiguë
- **WHEN** plusieurs résultats correspondent en mode réel
- **THEN** la liste (ville, région, pays) est proposée et le profil en choisit un

#### Scenario: Ville introuvable
- **WHEN** aucun résultat ne correspond en mode réel
- **THEN** un message d'erreur en français est affiché et la configuration reste inchangée

#### Scenario: Aucune ville configurée
- **WHEN** le profil ouvre la météo sans ville
- **THEN** le widget signale que la ville n'est pas configurée ; le choix de la ville se fait sur `/configuration`, accessible depuis le menu de l'avatar

#### Scenario: Recherche en mode TEST
- **WHEN** le profil saisit « Lyon », « Tokyo » ou un autre texte en mode TEST
- **THEN** l'unique résultat proposé est Paris avec les coordonnées du fixture, sans appel externe

#### Scenario: Activation du mode TEST
- **WHEN** l'administrateur active le mode TEST alors qu'un profil a une ville enregistrée
- **THEN** la configuration de ville du profil reste inchangée

### Requirement: Source sans clé et cache
En mode réel, les données SHALL provenir d'Open-Meteo, appelé par le backend, avec un cache borné de 30 min par localisation. En mode TEST, le système MUST charger les données météo depuis `docs/response-open-meteo.json` sans appel HTTP externe.

#### Scenario: Appels réels répétés
- **WHEN** deux profils demandent la même localisation en mode réel dans les 30 min
- **THEN** un seul appel externe est effectué

#### Scenario: Fixture en mode TEST
- **WHEN** la météo ou la recherche de ville est demandée en mode TEST
- **THEN** les réponses viennent du fixture local et aucun appel Open-Meteo n'est effectué

### Requirement: Indisponibilité de la source
Le système SHALL afficher un message d'erreur sans bloquer les autres applications si la source est indisponible.

#### Scenario: Source en erreur
- **WHEN** Open-Meteo ne répond pas
- **THEN** le widget affiche un état d'erreur et les autres widgets restent fonctionnels