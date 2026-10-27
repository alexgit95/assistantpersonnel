# Purpose

Define profile-specific weather location selection, forecasts, caching, and provider failure behavior.

## Requirements

### Requirement: Météo actuelle et prévisions
Le système SHALL afficher la météo actuelle et celle des prochains jours (5 par défaut) pour la localisation configurée du profil.

#### Scenario: Affichage
- **WHEN** le profil ouvre l'application météo
- **THEN** conditions actuelles et prévisions sont affichées

### Requirement: Localisation par nom de ville
Le profil SHALL définir sa localisation en saisissant un nom de ville ; le système la résout en coordonnées via le géocodage Open-Meteo et enregistre ville, pays et coordonnées dans la configuration de l'application du profil.

#### Scenario: Ville unique
- **WHEN** le profil saisit « Lyon » et qu'un seul résultat est pertinent
- **THEN** la ville est enregistrée avec ses coordonnées

#### Scenario: Ville ambiguë
- **WHEN** plusieurs résultats correspondent
- **THEN** la liste (ville, région, pays) est proposée et le profil en choisit un

#### Scenario: Ville introuvable
- **WHEN** aucun résultat ne correspond
- **THEN** un message d'erreur en français est affiché et la configuration reste inchangée

#### Scenario: Aucune ville configurée
- **WHEN** le profil ouvre la météo sans ville
- **THEN** le widget signale que la ville n'est pas configurée ; le choix de la ville se fait sur `/configuration`, accessible depuis le menu de l'avatar

### Requirement: Source sans clé et cache
Les données SHALL provenir d'Open-Meteo, appelées par le backend, avec un cache borné de 30 min par localisation.

#### Scenario: Appels répétés
- **WHEN** deux profils demandent la même localisation dans les 30 min
- **THEN** un seul appel externe est effectué

### Requirement: Indisponibilité de la source
Le système SHALL afficher un message d'erreur sans bloquer les autres applications si la source est indisponible.

#### Scenario: Source en erreur
- **WHEN** Open-Meteo ne répond pas
- **THEN** le widget affiche un état d'erreur et les autres widgets restent fonctionnels