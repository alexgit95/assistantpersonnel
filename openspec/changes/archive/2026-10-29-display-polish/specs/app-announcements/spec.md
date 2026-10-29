## MODIFIED Requirements

### Requirement: Date de fin des annonces simples
Une annonce sans compte à rebours SHALL afficher sa date et son heure de fin, en heure de Paris, à la suite de son titre sur la même ligne (format `jj/mm/aaaa à hh:mm`), sans mention « fin le ». Une annonce avec compte à rebours MUST NOT afficher cette date.

#### Scenario: Annonce sans compte à rebours
- **WHEN** une annonce sans compte à rebours se termine le 27/10/2026 à 18:00 heure de Paris
- **THEN** son titre est suivi de « 27/10/2026 à 18:00 » sans les mots « fin le »

#### Scenario: Annonce avec compte à rebours
- **WHEN** une annonce avec compte à rebours est affichée
- **THEN** seule la mention du temps restant indique l'échéance
