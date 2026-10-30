## ADDED Requirements

### Requirement: Courbe des températures horaires
Le widget Météo affiché dans le bloc central SHALL présenter, sous le contenu existant, une courbe des températures horaires de l'heure courante moins 3 heures à l'heure courante plus 24 heures, avec un point par heure, dans le style du graphique du poids (ligne blanche arrondie, étiquettes sur fond noir). La courbe MUST afficher cinq graduations verticales régulièrement espacées, dont les valeurs sont exprimées en °C et couvrent la plage des températures, avec des guides horizontaux pour estimer les valeurs intermédiaires. Elle MUST afficher des étiquettes de point uniquement sur la température minimale, la température maximale et l'heure courante, en n'affichant qu'une étiquette lorsque deux de ces points coïncident, et SHALL porter des graduations d'axe horizontal aux heures pleines multiples de 6. Lorsque les données ne remontent pas à 3 heures avant l'heure courante, la courbe MUST commencer à la première heure disponible. Les emplacements secondaires MUST NOT afficher la courbe.

#### Scenario: Fenêtre complète
- **WHEN** il est 14 h et que les prévisions couvrent la période
- **THEN** la courbe montre les températures de 11 h aujourd'hui à 14 h demain, soit 28 points, avec les graduations 12 h, 18 h, 0 h, 6 h et 12 h

#### Scenario: Étiquettes
- **WHEN** la courbe est affichée
- **THEN** seules les températures minimale, maximale et courante portent une étiquette, et le point courant est plus marqué que les autres

#### Scenario: Axe vertical gradué
- **WHEN** la courbe des températures est affichée
- **THEN** l'axe vertical affiche l'unité °C, des valeurs intermédiaires régulièrement espacées et des guides horizontaux

#### Scenario: Début de nuit
- **WHEN** il est 1 h et que les données commencent à 0 h aujourd'hui
- **THEN** la courbe commence à 0 h et compte moins de points avant l'heure courante

#### Scenario: Emplacement secondaire
- **WHEN** le widget Météo est placé dans un emplacement secondaire
- **THEN** aucune courbe n'est affichée

### Requirement: Tracé animé et retracé de la courbe
La courbe SHALL être tracée progressivement à l'affichage, puis retracée à chaque cycle périodique du widget Météo, avec la même animation que le graphique du poids. Elle MUST NOT être retracée entre deux cycles si ses données n'ont pas changé, et MUST s'afficher sans animation lorsque `prefers-reduced-motion` est actif ou que l'écriture est désactivée.

#### Scenario: Premier affichage
- **WHEN** le widget est affiché pour la première fois
- **THEN** la ligne est tracée de gauche à droite

#### Scenario: Cycle périodique
- **WHEN** le délai aléatoire du cycle périodique du widget s'écoule
- **THEN** la courbe est retracée avec la même animation

#### Scenario: Réduction des animations
- **WHEN** `prefers-reduced-motion: reduce` est actif
- **THEN** la courbe est affichée complète, sans animation

### Requirement: Températures horaires dans la réponse météo
`GET /api/weather` SHALL inclure les températures horaires de la période demandée, chacune avec son heure locale au format ISO, ainsi que l'heure courante des données. Une température indisponible MUST être transmise comme absente sans décaler les autres valeurs. Aucun appel externe supplémentaire MUST être émis pour ces données.

#### Scenario: Réponse enrichie
- **WHEN** un profil demande ses prévisions
- **THEN** la réponse contient les températures horaires avec leur heure et l'heure courante des données, en plus des jours existants

#### Scenario: Température absente
- **WHEN** Open-Meteo renvoie une valeur nulle pour une heure
- **THEN** cette heure est transmise sans température et les heures voisines gardent leur place

#### Scenario: Mode TEST
- **WHEN** le mode TEST est actif
- **THEN** les températures horaires et l'heure courante viennent du fixture, et la courbe est centrée sur l'heure courante du fixture
