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
Pour chaque arrêt enregistré, le widget SHALL afficher les prochains passages regroupés par ligne : les 3 prochains passages d'une ligne sont affichés les uns après les autres, puis ceux de la ligne suivante, sans jamais entrelacer les passages de lignes différentes, quel que soit le mode de transport. Les blocs de lignes MUST être triés par leur premier passage (du plus proche au plus lointain) et, dans un bloc, les passages par heure croissante. Chaque rangée MUST afficher la ligne (répétée à chaque rangée) sous la forme de son identifiant court seulement : la lettre du train ou le numéro du bus, sans préfixe de mode ni de réseau, la destination lorsqu'elle est fournie et le temps restant sous la forme « dans X min » (« À l'approche » ou équivalent lorsque l'écart est inférieur à une minute). Une ligne qui a moins de 3 passages à venir MUST être affichée avec ceux qu'elle a, sans complément. Le nombre de lignes affichées par arrêt MUST être de 4 lorsque le profil a enregistré 1 arrêt, de 2 lorsqu'il en a enregistré 2 et de 1 lorsqu'il en a enregistré 3. L'heure retenue MUST être `ExpectedArrivalTime`, à défaut `ExpectedDepartureTime`. Les passages déjà écoulés MUST être ignorés. Le widget SHALL afficher une seule heure de mise à jour, au format `HH:MM` (fuseau Europe/Paris, sans date) : la plus ancienne des heures de mise à jour des arrêts qui ont des données.

#### Scenario: Passages regroupés par ligne
- **WHEN** un arrêt reçoit des passages des lignes A et B entremêlués dans le temps
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
