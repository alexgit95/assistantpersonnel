## MODIFIED Requirements

### Requirement: Saisie du poids
Le système SHALL permettre à un profil de saisir son poids en kg avec une précision au dixième pour une date, entre 20,0 et 400,0 kg, depuis la page mobile `/mobile/weight` (le widget du Pi n'a plus de formulaire). La virgule et le point SHALL être acceptés comme séparateur décimal ; la valeur est stockée en dixièmes de kg (entier). Le champ SHALL être pré-rempli avec le dernier relevé du profil.

#### Scenario: Saisie valide
- **WHEN** le profil saisit 72,5 kg pour aujourd'hui sur son smartphone
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
Le système SHALL afficher l'évolution graphique du poids du profil courant dans le widget du Pi et son historique paginé sur la page mobile `/mobile/weight`.

#### Scenario: Consultation sur le Pi
- **WHEN** le profil a le widget poids placé
- **THEN** le graphique de ses mesures est affiché avec le QR poids, sans face arrière

#### Scenario: Consultation sur le smartphone
- **WHEN** le profil ouvre `/mobile/weight`
- **THEN** ses entrées sont chargées par pages et seules les siennes sont visibles

### Requirement: Modification et suppression
Le profil SHALL pouvoir corriger ou supprimer ses propres entrées depuis la page mobile `/mobile/weight`.

#### Scenario: Entrée d'un autre profil
- **WHEN** le profil P modifie une entrée de Q
- **THEN** la requête est refusée

#### Scenario: Correction depuis le smartphone
- **WHEN** le profil modifie ou supprime une de ses entrées sur son smartphone
- **THEN** la liste et le graphique reflètent le changement

## ADDED Requirements

### Requirement: Widget poids sans retournement
Le widget poids SHALL afficher uniquement le graphique d'évolution et le QR poids ; il MUST NOT proposer de face arrière, d'historique court ni de formulaire de saisie. Sans mesure, il MUST afficher un graphique vide, le QR poids et le message « Scannez pour saisir votre premier poids ».

#### Scenario: Widget avec mesures
- **WHEN** le profil possède des mesures
- **THEN** le widget affiche le graphique et le QR poids

#### Scenario: Widget sans mesure
- **WHEN** le profil n'a aucune mesure
- **THEN** le widget affiche un graphique vide, le QR poids et un message invitant à saisir le premier poids

#### Scenario: Aucune interaction de retournement
- **WHEN** l'utilisateur clique sur le graphique
- **THEN** aucune face arrière ne s'affiche
