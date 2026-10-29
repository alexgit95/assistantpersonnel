## MODIFIED Requirements

### Requirement: Affectation des widgets par glisser-déposer
La page de configuration SHALL permettre d'affecter les widgets disponibles aux cinq emplacements fixes par glisser-déposer et d'enregistrer l'affectation pour le profil courant. La page SHALL pouvoir défiler verticalement si son contenu dépasse la fenêtre et SHALL proposer un bouton de retour à l'accueil, sous forme d'icône flèche accessible (libellé « Retour à l'accueil »).

#### Scenario: Déplacement d'un widget
- **WHEN** le profil dépose un widget sur un emplacement cible
- **THEN** l'affectation est mise à jour et sauvegardée pour ce profil

#### Scenario: Affectation persistante
- **WHEN** le profil revient sur l'accueil après enregistrement ou rechargement
- **THEN** les widgets sont rendus dans les emplacements qu'il avait choisis

#### Scenario: Configuration défilante
- **WHEN** le profil ouvre la page de configuration sur une fenêtre plus petite que son contenu
- **THEN** il peut faire défiler la page pour atteindre les cibles, les réglages et le bouton « Enregistrer »

#### Scenario: Retour à l'accueil
- **WHEN** le profil clique sur l'icône de retour de la page de configuration
- **THEN** il retourne à l'accueil `/`
