## ADDED Requirements

### Requirement: Profil invité global
Le système SHALL maintenir un unique profil invité, repéré par la ligne `guest_setting` (id 1) et nommé « Invité », créé de façon idempotente au démarrage puis après chaque restauration de sauvegarde. À la création, il MUST posséder les applications Météo à l'emplacement 1 et Actualités à l'emplacement 3, activées, avec une configuration vide. Il MUST NOT être créé par une requête de lecture.

#### Scenario: Premier démarrage
- **WHEN** l'application démarre sans ligne `guest_setting`
- **THEN** le profil invité est créé avec Météo à l'emplacement 1 et Actualités à l'emplacement 3

#### Scenario: Démarrage suivant
- **WHEN** l'application redémarre avec un invité existant
- **THEN** rien n'est créé ni modifié

#### Scenario: Réamorçage après restauration
- **WHEN** une restauration remplace les données par un dump sans invité
- **THEN** l'invité est recréé avec ses valeurs par défaut sans redémarrage

### Requirement: Écran invité sans session
Le système SHALL afficher sur l'accueil les widgets de l'invité dès qu'il n'existe pas de session faciale valide : au chargement, après une déconnexion, à l'expiration ou à la perte de session et pendant la pause de reconnaissance. Dès qu'une session s'ouvre, ces widgets MUST être remplacés par ceux du profil connecté. Les emplacements, leur géométrie et le bouton « Créer un profil » MUST rester identiques.

#### Scenario: Chargement sans session
- **WHEN** l'accueil est ouvert sans session valide
- **THEN** la météo s'affiche en haut à gauche et les actualités en haut à droite

#### Scenario: Reconnaissance d'un profil
- **WHEN** un visage connu est reconnu
- **THEN** les widgets de ce profil remplacent ceux de l'invité

#### Scenario: Déconnexion
- **WHEN** l'utilisateur clique sur « Déconnexion » ou que sa session expire
- **THEN** les widgets de l'invité reviennent et les données du profil ne sont plus affichées

#### Scenario: Invité non configuré
- **WHEN** aucun invité n'existe
- **THEN** les emplacements restent vides comme avant

### Requirement: Liste blanche des applications invitées
Chaque application SHALL déclarer `guestAllowed()`, faux par défaut ; seules Météo et Actualités MUST le déclarer vrai. Le serveur MUST filtrer par cette liste la liste des applications, les fragments et les données servis à l'invité, même pour une application activée par défaut, et MUST NOT servir le poids ou toute autre application non autorisée sans session.

#### Scenario: Application activée par défaut
- **WHEN** l'invité n'a aucune ligne pour le poids, activé par défaut pour les profils
- **THEN** le poids n'apparaît ni dans la liste ni dans les fragments de l'invité

#### Scenario: Fragment non autorisé
- **WHEN** un client sans session demande le fragment du poids
- **THEN** le serveur répond 401 ou 404

#### Scenario: Nouvelle application
- **WHEN** une application est ajoutée sans surcharger `guestAllowed()`
- **THEN** elle n'est jamais servie à l'invité

### Requirement: Lecture seule sans session
Le système SHALL résoudre l'invité sans session uniquement sur les lectures annotées `@AllowGuest` : liste des applications, fragments, prévision météo et flux d'actualités. Une session valide MUST toujours être prioritaire sur l'invité ; un cookie expiré ou inconnu MUST être traité comme une absence de session. Les écritures, la recherche de ville, `/configuration` et les autres lectures MUST rester refusées sans session. Les réponses résolues pour l'invité MUST porter `Cache-Control: no-store`. La connexion MUST refuser l'identifiant du profil invité.

#### Scenario: Lecture autorisée
- **WHEN** un client sans cookie demande la prévision météo
- **THEN** la prévision de la ville de l'invité est renvoyée avec `Cache-Control: no-store`

#### Scenario: Cookie expiré
- **WHEN** un client envoie un cookie de session expiré sur une lecture invitée
- **THEN** il est traité comme invité et ne reçoit pas 401

#### Scenario: Écriture sans session
- **WHEN** un client sans session tente d'enregistrer une ville ou des catégories
- **THEN** le serveur répond 401

#### Scenario: Session prioritaire
- **WHEN** un client avec une session valide demande la liste des applications
- **THEN** il reçoit celles de son profil et non celles de l'invité

#### Scenario: Connexion à l'invité
- **WHEN** une connexion est demandée pour l'identifiant du profil invité
- **THEN** elle est refusée et aucune session n'est créée

### Requirement: Formulaire d'administration de l'écran invité
Le système SHALL proposer sous `/admin/guest`, réservé au rôle ADMIN et protégé par CSRF, un formulaire permettant de choisir l'application de chacun des 5 emplacements parmi celles dont `guestAllowed()` est vrai, la ville de la météo (recherche puis choix dans la même page) et les catégories d'actualités. Aucune route de l'invité MUST être exposée sous `/api/**`. Le formulaire MUST refuser une application non autorisée, un emplacement hors 0 à 4, une application placée deux fois ou deux applications dans le même emplacement.

#### Scenario: Accès réservé
- **WHEN** un visiteur non administrateur ouvre `/admin/guest`
- **THEN** il est redirigé vers la connexion administrateur

#### Scenario: Choix de la ville
- **WHEN** l'administrateur recherche et enregistre une ville
- **THEN** elle devient la ville de l'invité sans modifier celle d'aucun profil

#### Scenario: Application interdite
- **WHEN** une requête tente de placer le poids dans un emplacement de l'invité
- **THEN** le serveur la refuse et la configuration est inchangée

#### Scenario: Réaffectation
- **WHEN** l'administrateur déplace Actualités vers l'emplacement 4
- **THEN** l'écran invité l'affiche dans cet emplacement

### Requirement: Invité exclu des fonctions de profil
Le profil invité MUST NOT apparaître dans la liste des visages reconnaissables, dans la liste des profils de l'administration, ni être purgeable, et MUST NOT compter dans `ASSISTANT_MAX_PROFILES`.

#### Scenario: Liste des visages
- **WHEN** le navigateur demande les visages
- **THEN** l'invité n'est pas renvoyé

#### Scenario: Limite de profils
- **WHEN** `ASSISTANT_MAX_PROFILES` vaut 3, 3 profils réels et l'invité existent
- **THEN** la création d'un quatrième profil est refusée et celle d'un troisième avec 2 profils réels est acceptée

#### Scenario: Purge de l'invité
- **WHEN** une purge est demandée pour l'identifiant de l'invité
- **THEN** elle est refusée et l'invité subsiste
