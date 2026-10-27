## MODIFIED Requirements

### Requirement: Textes déjà affichés
Un texte déjà affiché et inchangé MUST NOT être ré-animé lors d'une actualisation périodique, sauf lorsqu'un widget demande explicitement sa réécriture (réécriture aléatoire du widget Annonces).

#### Scenario: Rafraîchissement d'un widget
- **WHEN** un widget se rafraîchit avec un contenu identique
- **THEN** aucun effet d'écriture n'est rejoué

#### Scenario: Réécriture explicitement demandée
- **WHEN** un widget demande la réécriture d'un texte inchangé
- **THEN** l'effet d'écriture est rejoué, en respectant la désactivation et la réduction des animations
