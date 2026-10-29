# Purpose

Define profile-specific weather location selection, forecasts, caching, and provider failure behavior.

## Requirements

### Requirement: Météo actuelle et prévisions
En mode réel, le système SHALL fournir la météo actuelle et les prévisions de 4 jours (aujourd'hui et les trois jours suivants, durée figée et non configurable) pour la localisation configurée du profil. Chaque jour SHALL comporter une température et un code météo pour le matin et pour l'après-midi, le risque de pluie du jour et les rafales maximales. En mode TEST, il MUST fournir ces données depuis le fichier `docs/response-open-meteo.json`, même si une autre ville est enregistrée au profil.

#### Scenario: Affichage en mode réel
- **WHEN** le profil ouvre l'application météo en mode réel
- **THEN** les conditions actuelles et les 4 jours de prévisions de sa localisation enregistrée sont fournis

#### Scenario: Affichage en mode TEST avec une autre ville enregistrée
- **WHEN** le profil a une autre ville configurée et ouvre la météo en mode TEST
- **THEN** le widget affiche Paris et les prévisions correspondantes du fixture

#### Scenario: Fixture indisponible
- **WHEN** le fixture météo local est illisible ou invalide en mode TEST
- **THEN** le widget affiche une erreur d'indisponibilité sans appeler Open-Meteo

### Requirement: Matin et après-midi
Le matin SHALL être la plage 6 h à 12 h et l'après-midi la plage 12 h à 18 h, en heure locale de la localisation. La température du matin SHALL être le minimum de sa plage et celle de l'après-midi le maximum de la sienne. Le code météo de chaque plage SHALL être le plus sévère rencontré dans la plage (orage, neige, pluie, brouillard, couvert, partiellement nuageux, dégagé).

#### Scenario: Températures de la journée
- **WHEN** les prévisions horaires d'un jour sont reçues
- **THEN** la température du matin est le minimum de 6 h à 12 h et celle de l'après-midi le maximum de 12 h à 18 h

#### Scenario: Averse masquée par un ciel clair
- **WHEN** une plage contient une averse et des heures dégagées
- **THEN** le code de la plage est celui de l'averse

### Requirement: Carré du jour
Le widget météo SHALL afficher en haut un carré sur toute sa largeur pour aujourd'hui, contenant la ville, une ligne « Maintenant » avec la température et le ressenti actuels, la température et le pictogramme du matin et de l'après-midi, le risque de pluie du jour en pourcentage, une phrase de conseil et le pictogramme vent lorsque des rafales de plus de 50 km/h sont attendues. L'humidité et la vitesse du vent actuels MUST NOT être affichées.

#### Scenario: Journée avec pluie
- **WHEN** le risque de pluie du jour est de 70 %
- **THEN** le carré affiche « Risque de pluie : 70 % » et une phrase conseillant un parapluie

#### Scenario: Ressenti actuel
- **WHEN** la température actuelle est de 14 °C et le ressenti de 12 °C
- **THEN** le carré affiche ces deux valeurs sur la ligne « Maintenant »

### Requirement: Trois jours suivants
En emplacement principal, le widget SHALL afficher sous le carré trois lignes, une par jour suivant, chacune avec le jour de la semaine, la température et le pictogramme du matin, puis la température et le pictogramme de l'après-midi. En emplacement secondaire, le widget MUST afficher uniquement le carré du jour.

#### Scenario: Emplacement principal
- **WHEN** le widget météo occupe l'emplacement principal
- **THEN** le carré du jour et trois lignes de jours suivants sont affichés

#### Scenario: Emplacement secondaire
- **WHEN** le widget météo occupe un emplacement secondaire
- **THEN** seul le carré du jour est affiché, sans les lignes des jours suivants

### Requirement: Pictogrammes météo monochromes
Les pictogrammes SHALL être des SVG monochromes en trait blanc compatibles avec le thème noir OLED, sans dépendre d'emojis ni d'une police système. Le système SHALL couvrir au minimum : soleil, peu nuageux, couvert, brouillard, pluie ou bruine, neige, orage et vent. Chaque pictogramme MUST porter un libellé accessible en français.

#### Scenario: Pictogramme par code météo
- **WHEN** un code météo de pluie est reçu pour une plage
- **THEN** le pictogramme de pluie est affiché avec le libellé « Pluie »

#### Scenario: Code inconnu
- **WHEN** un code météo non reconnu est reçu
- **THEN** un pictogramme neutre est affiché sans erreur

### Requirement: Rafales de vent
Le widget SHALL afficher le pictogramme vent sur un jour lorsque les rafales maximales de ce jour dépassent 50 km/h, avec un libellé accessible indiquant la valeur (par exemple « Rafales jusqu'à 62 km/h »). Le seuil de 50 km/h SHALL être une constante unique côté serveur.

#### Scenario: Rafales fortes
- **WHEN** les rafales maximales d'un jour atteignent 62 km/h
- **THEN** le pictogramme vent est affiché avec le libellé « Rafales jusqu'à 62 km/h »

#### Scenario: Rafales modérées
- **WHEN** les rafales maximales d'un jour sont de 50 km/h ou moins
- **THEN** aucun pictogramme vent n'est affiché pour ce jour

### Requirement: Phrase de conseil
Le serveur SHALL générer, pour chaque jour, une phrase française au tutoiement résumant la journée. Les règles SHALL être appliquées dans cet ordre de priorité : orage, neige, risque de pluie de 50 % ou plus (parapluie), rafales de plus de 50 km/h, minimum inférieur à 5 °C (« couvre-toi bien »), minimum inférieur à 12 °C (veste), maximum de 28 °C ou plus (chaleur, hydratation), sinon un message neutre. Plusieurs conseils compatibles MAY être combinés dans une seule phrase.

#### Scenario: Pluie et fraîcheur
- **WHEN** le risque de pluie est de 70 % et le minimum de 9 °C
- **THEN** la phrase conseille un parapluie et une veste

#### Scenario: Grand froid
- **WHEN** le minimum du jour est de 3 °C sans pluie
- **THEN** la phrase conseille de bien se couvrir

#### Scenario: Journée sans particularité
- **WHEN** aucune condition particulière n'est remplie
- **THEN** la phrase est un message neutre indiquant qu'il n'y a rien de particulier

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