## MODIFIED Requirements

### Requirement: Rechargement et réécriture de la météo
À chaque cycle, le widget météo SHALL recharger les prévisions puis ré-écrire avec l'effet machine à écrire la ville, la ligne « Maintenant », les températures, le risque de pluie et la phrase de conseil du carré du jour, ainsi que les températures des lignes des jours suivants lorsqu'elles sont affichées, même si les textes sont inchangés. Les pictogrammes MUST être mis à jour sans animation d'écriture.

#### Scenario: Cycle réussi
- **WHEN** le cycle se déclenche et le rechargement réussit
- **THEN** tous les textes du widget sont ré-écrits avec les données reçues et les pictogrammes reflètent ces données

#### Scenario: Échec du rechargement
- **WHEN** le rechargement périodique échoue
- **THEN** l'affichage précédent est conservé et aucun message d'erreur n'est affiché

#### Scenario: Emplacement secondaire
- **WHEN** le widget météo est dans un emplacement secondaire
- **THEN** seuls les textes du carré du jour sont ré-écrits
