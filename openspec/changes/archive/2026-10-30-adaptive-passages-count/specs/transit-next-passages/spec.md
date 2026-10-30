## MODIFIED Requirements

### Requirement: Affichage des quatre prochains passages
Pour chaque arrêt enregistré, le widget SHALL afficher les prochains passages triés par heure croissante, chacun sous la forme « dans X min » (« À l'approche » ou équivalent lorsque l'écart est inférieur à une minute), avec la ligne et la destination lorsqu'elles sont fournies. Le nombre de passages affichés par arrêt MUST être de 6 lorsque le profil a enregistré 1 ou 2 arrêts, et de 4 lorsqu'il en a enregistré 3. L'heure retenue MUST être `ExpectedArrivalTime`, à défaut `ExpectedDepartureTime`. Les passages déjà écoulés MUST être ignorés. Le widget SHALL afficher l'heure de mise à jour de la donnée.

#### Scenario: Passages à venir avec un ou deux arrêts
- **WHEN** le profil a 1 ou 2 arrêts et PRIM renvoie plus de 6 passages futurs pour un arrêt
- **THEN** les 6 plus proches sont affichés en minutes restantes avec l'heure de mise à jour

#### Scenario: Passages à venir avec trois arrêts
- **WHEN** le profil a 3 arrêts et PRIM renvoie plus de 4 passages futurs pour un arrêt
- **THEN** les 4 plus proches sont affichés en minutes restantes avec l'heure de mise à jour

#### Scenario: Arrêt indisponible compté
- **WHEN** le profil a 3 arrêts dont un est indisponible
- **THEN** la limite reste de 4 passages pour les arrêts affichés

#### Scenario: Heure d'arrivée absente
- **WHEN** un passage ne contient que `ExpectedDepartureTime`
- **THEN** cette heure est utilisée pour le calcul

#### Scenario: Passage écoulé
- **WHEN** l'heure d'un passage est antérieure à l'instant courant
- **THEN** il n'est pas affiché

#### Scenario: Aucun passage
- **WHEN** PRIM ne renvoie aucun passage pour un arrêt
- **THEN** le widget l'indique pour cet arrêt sans affecter les autres
