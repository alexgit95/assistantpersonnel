## ADDED Requirements

### Requirement: Saisie du poids
Le système SHALL permettre à un profil de saisir son poids en kg avec une précision au dixième pour une date, entre 20,0 et 400,0 kg. La virgule et le point SHALL être acceptés comme séparateur décimal ; la valeur est stockée en dixièmes de kg (entier).

#### Scenario: Saisie valide
- **WHEN** le profil saisit 72,5 kg pour aujourd'hui
- **THEN** l'entrée est enregistrée sur son profil

#### Scenario: Point décimal
- **WHEN** le profil saisit 72.5
- **THEN** la valeur est enregistrée comme 72,5 kg

#### Scenario: Trop de décimales
- **WHEN** le profil saisit 72,55
- **THEN** la saisie est refusée (400) avec un message demandant une seule décimale

#### Scenario: Valeur invalide
- **WHEN** la valeur est hors de 20,0-400,0, négative, nulle ou non numérique
- **THEN** la saisie est refusée (400)

### Requirement: Historique paginé
Le système SHALL afficher l'historique du poids du profil courant de façon paginée avec évolution graphique.

#### Scenario: Consultation
- **WHEN** le profil ouvre l'application
- **THEN** les entrées sont chargées par pages et seules les siennes sont visibles

### Requirement: Valeurs et animation du graphique
Le graphique SHALL afficher la valeur en kg à proximité de chaque point visible et SHALL tracer sa courbe progressivement lorsque les données changent. L'animation MUST NOT s'exécuter si `prefers-reduced-motion` est activé.

#### Scenario: Valeurs lisibles
- **WHEN** le graphique contient des mesures
- **THEN** chaque point visible porte son poids au dixième en kg avec la virgule française

#### Scenario: Tracé progressif
- **WHEN** le graphique apparaît ou reçoit de nouvelles mesures
- **THEN** la ligne se trace progressivement au lieu d'apparaître immédiatement

#### Scenario: Réduction de mouvement
- **WHEN** le navigateur déclare `prefers-reduced-motion: reduce`
- **THEN** la courbe apparaît sans animation et les valeurs restent visibles

### Requirement: Modification et suppression
Le profil SHALL pouvoir corriger ou supprimer ses propres entrées.

#### Scenario: Entrée d'un autre profil
- **WHEN** le profil P modifie une entrée de Q
- **THEN** la requête est refusée
