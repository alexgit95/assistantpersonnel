## MODIFIED Requirements

### Requirement: Affichage des prochains passages
Pour chaque arrêt enregistré, le widget SHALL afficher les prochains passages regroupés par ligne : les 3 prochains passages d'une ligne sont affichés les uns après les autres, puis ceux de la ligne suivante, sans jamais entrelacer les passages de lignes différentes, quel que soit le mode de transport. Les blocs de lignes MUST être triés par leur premier passage (du plus proche au plus lointain) et, dans un bloc, les passages par heure croissante. Chaque rangée MUST afficher la ligne (répétée à chaque rangée) sous la forme de son identifiant court seulement : la lettre du train ou le numéro du bus, sans préfixe de mode ni de réseau, la destination lorsqu'elle est fournie et le temps restant sous la forme « dans X min » (« À l'approche » ou équivalent lorsque l'écart est inférieur à une minute). Une ligne qui a moins de 3 passages à venir MUST être affichée avec ceux qu'elle a, sans complément. Le nombre de lignes affichées par arrêt MUST être de 4 lorsque le profil a enregistré 1 arrêt, de 2 lorsqu'il en a enregistré 2 et de 1 lorsqu'il en a enregistré 3. L'heure retenue MUST être `ExpectedArrivalTime`, à défaut `ExpectedDepartureTime`. Les passages déjà écoulés MUST être ignorés. Le widget SHALL afficher une seule heure de mise à jour, au format `HH:MM` (fuseau Europe/Paris, sans date) : la plus ancienne des heures de mise à jour des arrêts qui ont des données.

#### Scenario: Passages regroupés par ligne
- **WHEN** un arrêt reçoit des passages des lignes A et B entremêlés dans le temps
- **THEN** les 3 prochains passages de A sont affichés à la suite, puis ceux de B, sans alterner

#### Scenario: Blocs triés par premier passage
- **WHEN** la ligne A a son premier passage dans 7 minutes et la ligne B dans 5 minutes
- **THEN** le bloc de B est affiché avant celui de A, chacun avec ses passages croissants

#### Scenario: Ligne rare non masquée
- **WHEN** une ligne fréquente a plus de 3 passages avant le premier passage d'une ligne rare, dans la limite du nombre de lignes affiché
- **THEN** la ligne rare est quand même affichée avec ses passages

#### Scenario: Identifiant court de la ligne
- **WHEN** un passage concerne le bus de la ligne 172 ou la ligne ferroviaire A
- **THEN** la pastille affiche « 172 » ou « A », sans le mot « Bus » ni le nom du réseau

#### Scenario: Ligne à moins de 3 passages
- **WHEN** une ligne n'a que 2 passages à venir
- **THEN** ses 2 passages sont affichés et aucune rangée n'est ajoutée pour compléter

#### Scenario: Plafond de lignes avec un arrêt
- **WHEN** le profil a 1 arrêt dont plus de 4 lignes ont des passages à venir
- **THEN** les 4 lignes dont le premier passage est le plus proche sont affichées, chacune avec ses 3 prochains passages

#### Scenario: Plafond de lignes avec deux arrêts
- **WHEN** le profil a 2 arrêts dont plus de 2 lignes ont des passages à venir
- **THEN** 2 lignes sont affichées pour chaque arrêt

#### Scenario: Plafond de lignes avec trois arrêts
- **WHEN** le profil a 3 arrêts dont plus d'une ligne a des passages à venir
- **THEN** 1 ligne est affichée pour chaque arrêt

#### Scenario: Arrêt indisponible compté
- **WHEN** le profil a 3 arrêts dont un est indisponible
- **THEN** le plafond reste de 1 ligne pour les arrêts affichés

#### Scenario: Heure d'arrivée absente
- **WHEN** un passage ne contient que `ExpectedDepartureTime`
- **THEN** cette heure est utilisée pour le calcul

#### Scenario: Passage écoulé
- **WHEN** l'heure d'un passage est antérieure à l'instant courant
- **THEN** il n'est pas affiché

#### Scenario: Aucun passage
- **WHEN** PRIM ne renvoie aucun passage pour un arrêt
- **THEN** le widget l'indique pour cet arrêt sans affecter les autres

#### Scenario: Heure de mise à jour unique
- **WHEN** deux arrêts ont des données mises à jour à 09:12 et 09:11
- **THEN** le widget affiche une seule heure « Mis à jour 09:11 » et aucune heure par arrêt

### Requirement: Mode TEST des prochains passages
En mode TEST, le système SHALL construire la réponse à partir de `docs/reponse-prochain-passage.json` dont les horaires sont recalés dans le futur avec un décalage aléatoire, sans appel externe ni réservation de budget. Les passages simulés MUST couvrir au moins quatre lignes distinctes ayant chacune au moins 3 passages à venir, afin que le regroupement et les plafonds de lignes soient observables. La recherche d'arrêts MUST être simulée à partir d'un jeu local, sans appel externe, avec la même validation de la saisie. L'interface MUST signaler le mode TEST.

#### Scenario: Passages simulés
- **WHEN** le mode TEST est actif
- **THEN** le widget affiche des passages dont les heures sont postérieures à l'instant courant, sans appel PRIM

#### Scenario: Plusieurs lignes simulées
- **WHEN** le mode TEST est actif avec un seul arrêt enregistré
- **THEN** le widget affiche 4 lignes ayant chacune 3 passages

#### Scenario: Sans clé en mode TEST
- **WHEN** aucune clé PRIM n'est enregistrée en mode TEST
- **THEN** les passages simulés sont affichés sans message d'erreur
