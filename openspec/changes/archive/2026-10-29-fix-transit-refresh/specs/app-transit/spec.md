## ADDED Requirements

### Requirement: Relecture périodique du serveur par le widget
Le widget Transports SHALL relire `/api/transit/alerts` toutes les 60 secondes. Cette relecture MUST NOT déclencher d'appel PRIM au-delà de la règle des 10 minutes, la réponse étant servie depuis le cache serveur. Le widget SHALL mettre à jour son affichage, y compris l'heure de dernière actualisation et l'animation d'écriture, uniquement si l'horodatage de la réponse, son message d'état ou son état de pause ont changé depuis le dernier rendu. Si la réponse est identique, le widget MUST NOT modifier l'affichage. En cas d'échec de la relecture, le widget MUST conserver l'affichage et l'heure précédents.

#### Scenario: Heure synchronisée avec le serveur
- **WHEN** le serveur actualise la réponse PRIM alors que le widget est affiché
- **THEN** le widget affiche la nouvelle heure de mise à jour au plus 60 secondes plus tard, sans rechargement de la page

#### Scenario: Donnée inchangée
- **WHEN** une relecture renvoie le même horodatage, le même message d'état et le même état de pause
- **THEN** le widget ne modifie pas son affichage et ne rejoue pas l'animation d'écriture

#### Scenario: Changement de message d'état
- **WHEN** une relecture renvoie un message d'état différent, par exemple l'entrée en pause nocturne
- **THEN** le widget met son affichage à jour

#### Scenario: Relecture en échec
- **WHEN** une relecture échoue alors que des alertes sont affichées
- **THEN** les alertes et l'heure précédentes restent affichées

#### Scenario: Aucun appel PRIM supplémentaire
- **WHEN** le widget relit le serveur toutes les minutes pendant 10 minutes
- **THEN** le serveur n'effectue qu'une seule actualisation PRIM sur cette période

### Requirement: Actualisation planifiée à l'échéance
L'actualisation planifiée de la réponse globale PRIM SHALL rafraîchir le flux lorsque son âge atteint l'intervalle d'actualisation, avec une tolérance de 5 secondes d'avance, de sorte qu'un déclenchement planifié exactement 10 minutes après la précédente actualisation ne soit pas ignoré. L'espacement entre deux appels PRIM MUST rester d'au moins l'intervalle d'actualisation moins cette tolérance, et le budget journalier ainsi que la pause nocturne MUST continuer de s'appliquer.

#### Scenario: Déclenchement planifié légèrement en avance
- **WHEN** le déclenchement planifié a lieu 2 secondes avant que le flux atteigne 10 minutes d'âge
- **THEN** le flux est rafraîchi

#### Scenario: Déclenchement trop précoce
- **WHEN** une demande arrive alors que le flux a moins de 10 minutes moins 5 secondes
- **THEN** aucun appel PRIM n'est effectué

#### Scenario: Budget et pause
- **WHEN** le budget journalier est atteint ou que la pause nocturne est active à l'échéance
- **THEN** aucun appel PRIM n'est effectué
