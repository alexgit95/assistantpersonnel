## ADDED Requirements

### Requirement: Lignes perturbées en tête
Le widget Transports SHALL afficher en tête les lignes favorites dont le trafic n'est pas normal (au moins une alerte active), suivies des lignes sans alerte. L'ordre des favoris du profil MUST être conservé au sein de chacun des deux groupes. Une ligne sans donnée disponible MUST être traitée comme une ligne normale.

#### Scenario: Ligne perturbée remontée
- **WHEN** un profil a pour favoris RER A, Métro 1, Ligne J, Bus 72 et que Métro 1 et Bus 72 sont perturbées
- **THEN** l'ordre affiché est Métro 1, Bus 72, RER A, Ligne J

#### Scenario: Aucune perturbation
- **WHEN** aucune ligne favorite n'est perturbée
- **THEN** l'ordre des favoris du profil est conservé

#### Scenario: Données indisponibles
- **WHEN** le flux PRIM est indisponible et qu'aucune alerte n'est connue
- **THEN** l'ordre des favoris du profil est conservé
