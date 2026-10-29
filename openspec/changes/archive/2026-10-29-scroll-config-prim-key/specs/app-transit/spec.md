## MODIFIED Requirements

### Requirement: Clé API et indisponibilité
La clé PRIM SHALL être saisie par l'administrateur depuis `/admin` et stockée en clair en base ; aucune variable d'environnement ni propriété ne MUST la fournir. Elle SHALL être envoyée uniquement par le serveur, lue à chaque appel PRIM, et ne MUST jamais être exposée au navigateur ni écrite dans les logs. Tant qu'aucune clé n'est enregistrée, le système MUST NOT appeler PRIM, que ce soit à la demande ou par l'actualisation planifiée, et MUST NOT consommer le budget journalier. Sans clé ou si PRIM est indisponible, le widget MUST afficher un message sans affecter les autres widgets.

#### Scenario: Clé absente
- **WHEN** aucune clé PRIM n'est enregistrée en mode réel
- **THEN** le widget indique que le service n'est pas configuré, aucun appel PRIM n'est émis et le compteur journalier n'est pas incrémenté

#### Scenario: Clé enregistrée à chaud
- **WHEN** l'administrateur enregistre une clé valide
- **THEN** l'actualisation suivante utilise cette clé sans redémarrage

#### Scenario: Clé supprimée
- **WHEN** l'administrateur supprime la clé
- **THEN** les appels PRIM cessent dès l'actualisation suivante et le widget indique que le service n'est pas configuré

#### Scenario: PRIM indisponible
- **WHEN** PRIM répond en erreur ou dépasse le délai
- **THEN** la dernière valeur connue est servie avec son heure, ou un message d'indisponibilité si aucune n'existe
