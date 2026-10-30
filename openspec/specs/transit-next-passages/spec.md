# Purpose

Define the next-passages widget: stop search, per-profile stops, PRIM stop-monitoring display in minutes, face-session-gated refresh, budget, quiet hours and TEST mode.

## Requirements

### Requirement: Widget Prochains passages activable par profil
Le système SHALL proposer une application « Prochains passages » (clé `transit-passages`), distincte de l'application « Transports », activable et affectable à un emplacement comme les autres widgets, avec une configuration propre à chaque profil.

#### Scenario: Application disponible
- **WHEN** un profil ouvre la page de configuration
- **THEN** l'application « Prochains passages » est listée séparément de « Transports »

### Requirement: Recherche d'arrêts à la demande
Le système SHALL rechercher les arrêts à la demande via l'API publique du jeu de données `arrets` d'Île-de-France Mobilités, en ne sélectionnant que `arrid`, `arrname`, `arrtown` et `arrtype`. La requête MUST contenir au moins 3 caractères, MUST être limitée à 20 résultats, MUST échapper les guillemets du texte saisi, et MUST exiger une session faciale ouverte. Le système MUST NOT télécharger l'export complet ni conserver de référentiel local des arrêts. Un même texte MAY être mis en cache quelques minutes.

#### Scenario: Recherche valide
- **WHEN** l'utilisateur saisit « carnot »
- **THEN** le serveur interroge l'API publique et renvoie au plus 20 arrêts avec identifiant, nom, ville et type

#### Scenario: Texte trop court
- **WHEN** l'utilisateur saisit moins de 3 caractères
- **THEN** le serveur répond 400 sans appel externe

#### Scenario: Saisie contenant des guillemets
- **WHEN** le texte contient un guillemet
- **THEN** il est échappé et la clause de filtre envoyée à l'API reste bien formée

#### Scenario: Sans session
- **WHEN** la recherche est demandée sans session faciale ouverte
- **THEN** le serveur refuse la demande sans appel externe

#### Scenario: API publique indisponible
- **WHEN** l'API de recherche ne répond pas
- **THEN** le serveur répond par un message d'indisponibilité et les arrêts déjà enregistrés restent affichables

### Requirement: Arrêts enregistrés limités à trois
Le système SHALL permettre à chaque profil d'enregistrer au plus 3 arrêts uniques, conservés dans la configuration du profil sous la forme `arrid`, nom, ville et type. Le serveur MUST refuser un quatrième arrêt, un doublon ou un `arrid` non numérique. Aucune table de base de données dédiée MUST être créée et aucune revérification périodique des arrêts enregistrés MUST être effectuée.

#### Scenario: Ajout d'un arrêt
- **WHEN** un profil choisit un arrêt dans les résultats
- **THEN** ses quatre informations sont conservées dans la configuration du profil

#### Scenario: Limite atteinte
- **WHEN** un profil ayant déjà 3 arrêts en ajoute un quatrième
- **THEN** le serveur répond 400 et la configuration est inchangée

#### Scenario: Identifiant invalide
- **WHEN** un `arrid` non numérique est envoyé
- **THEN** le serveur répond 400

#### Scenario: Isolation par profil
- **WHEN** deux profils choisissent des arrêts différents
- **THEN** chacun ne voit que ses propres arrêts

### Requirement: Affichage des prochains passages
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

### Requirement: Appel PRIM Prochains passages
Le système SHALL appeler `stop-monitoring` de PRIM avec `MonitoringRef = STIF:StopPoint:Q:<arrid>:` et l'en-tête `apikey` de la clé PRIM existante, lue à chaque appel et jamais exposée au navigateur ni écrite dans les logs. Un arrêt que PRIM rejette (400) MUST s'afficher comme « Arrêt indisponible » avec la possibilité de le retirer ; un arrêt pour lequel PRIM ne renvoie aucun passage MUST afficher « Aucun passage prévu ».

#### Scenario: Appel nominal
- **WHEN** un arrêt d'`arrid` 24377 est actualisé en mode réel
- **THEN** PRIM est appelé avec `STIF:StopPoint:Q:24377:` et l'en-tête `apikey`

#### Scenario: Arrêt inconnu de PRIM
- **WHEN** PRIM répond 400 pour un arrêt
- **THEN** le widget affiche « Arrêt indisponible » pour cet arrêt et propose de le retirer

### Requirement: Actualisation conditionnée à la session faciale
Le widget SHALL actualiser ses passages une fois par minute uniquement tant que la session faciale est ouverte. Le serveur MUST refuser les appels sans session ouverte et MUST ne jamais appeler PRIM sans session. La réponse d'un arrêt MUST être mise en cache environ une minute et partagée entre les profils et les onglets, les demandes simultanées étant fusionnées en un seul appel.

#### Scenario: Session ouverte
- **WHEN** la session faciale est ouverte et que le widget est affiché
- **THEN** les passages sont actualisés toutes les 60 secondes

#### Scenario: Session fermée
- **WHEN** la session faciale est fermée ou perdue
- **THEN** le widget cesse d'actualiser et aucun appel PRIM n'est émis

#### Scenario: Plusieurs onglets ou profils
- **WHEN** deux clients demandent le même arrêt dans la même minute
- **THEN** un seul appel à PRIM est effectué

### Requirement: Budget, pause nocturne et clé absente
Le système SHALL appliquer aux appels de prochains passages le budget journalier PRIM existant (réservation atomique avant appel) et la pause nocturne configurée. Pendant la pause ou budget épuisé, le widget MUST afficher la dernière valeur connue avec son heure de mise à jour et un message explicatif. Tant qu'aucune clé PRIM n'est enregistrée, le système MUST NOT appeler PRIM ni consommer le budget, et le widget MUST afficher un message invitant à renseigner la clé PRIM.

#### Scenario: Clé absente
- **WHEN** aucune clé PRIM n'est enregistrée
- **THEN** le widget affiche un message demandant de renseigner la clé et aucun appel n'est émis

#### Scenario: Pause nocturne
- **WHEN** le widget est actualisé pendant la plage de pause
- **THEN** aucun appel n'est émis et un message de pause s'affiche avec la dernière valeur connue

#### Scenario: Budget épuisé
- **WHEN** le budget journalier est atteint
- **THEN** aucun appel n'est émis et la dernière valeur connue est servie avec son heure de mise à jour

#### Scenario: Échec temporaire de PRIM
- **WHEN** PRIM est indisponible
- **THEN** le widget affiche un message sans affecter les autres widgets et conserve la dernière valeur connue

### Requirement: Mode TEST des prochains passages
En mode TEST, le système SHALL construire la réponse à partir de `docs/reponse-prochain-passage.json` dont les horaires sont recalés dans le futur avec un décalage aléatoire, sans appel externe ni réservation de budget. La recherche d'arrêts MUST être simulée à partir d'un jeu local, sans appel externe, avec la même validation de la saisie. L'interface MUST signaler le mode TEST.

#### Scenario: Passages simulés
- **WHEN** le mode TEST est actif
- **THEN** le widget affiche des passages dont les heures sont postérieures à l'instant courant, sans appel PRIM

#### Scenario: Sans clé en mode TEST
- **WHEN** aucune clé PRIM n'est enregistrée en mode TEST
- **THEN** les passages simulés sont affichés sans message d'erreur
