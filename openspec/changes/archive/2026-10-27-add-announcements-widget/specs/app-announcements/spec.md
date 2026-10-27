## ADDED Requirements

### Requirement: Widget Annonces activable par profil
Le système SHALL fournir une application `announcements` (« Annonces ») découverte par le registre d'applications, que chaque profil peut activer, désactiver et affecter à un emplacement comme toute autre application.

#### Scenario: Activation
- **WHEN** un profil active le widget Annonces
- **THEN** il apparaît dans l'emplacement choisi et liste les annonces en cours

#### Scenario: Désactivation
- **WHEN** un profil désactive le widget
- **THEN** il n'est plus affiché pour ce profil et les annonces restent inchangées pour les autres profils

### Requirement: Annonces communes à tous les profils
Une annonce SHALL avoir un titre, une date de début, une date de fin et un indicateur de compte à rebours. Les annonces MUST être globales : elles ne sont rattachées à aucun profil et sont identiques pour tous les profils ayant activé le widget. Le titre est le seul contenu textuel et MUST être affiché comme texte brut.

#### Scenario: Même contenu pour tous
- **WHEN** deux profils différents ouvrent le widget au même instant
- **THEN** ils voient la même liste d'annonces

#### Scenario: Titre contenant du balisage
- **WHEN** le titre contient des caractères HTML
- **THEN** ils sont affichés littéralement et ne sont pas interprétés

### Requirement: Barre de progression du compte à rebours
Une annonce avec compte à rebours SHALL afficher sous le temps restant une barre de progression fine, en noir et blanc, indiquant l'avancement de l'instant courant (horloge serveur) entre la date de début (0 %) et la date de fin (100 %). La barre se met à jour chaque seconde avec le compte à rebours, et MUST NOT apparaître pour une annonce sans compte à rebours. L'API SHALL donc fournir la date de début de chaque annonce. La barre SHALL occuper 80 % de la largeur du widget, centrée, et son remplissage SHALL porter une animation continue de balayage (effet de rechargement) en niveaux de gris, désactivée en `prefers-reduced-motion`.

#### Scenario: Mi-parcours
- **WHEN** l'instant courant est à mi-chemin entre le début et la fin d'une annonce avec compte à rebours
- **THEN** la barre est remplie à 50 %

#### Scenario: Progression en direct
- **WHEN** le widget reste affiché
- **THEN** le remplissage augmente avec le temps, sans dépasser 100 %

#### Scenario: Annonce sans compte à rebours
- **WHEN** une annonce n'est pas marquée compte à rebours
- **THEN** aucune barre de progression n'est affichée

### Requirement: Annonces en cours uniquement
Le serveur SHALL ne renvoyer que les annonces dont la date de début est antérieure ou égale à l'instant serveur et dont la date de fin est strictement postérieure à cet instant. Les annonces sont triées par date de fin croissante (la plus proche en premier).

#### Scenario: Annonce à venir
- **WHEN** la date de début d'une annonce est dans le futur
- **THEN** elle n'est pas renvoyée ni affichée

#### Scenario: Annonce expirée
- **WHEN** la date de fin d'une annonce est passée
- **THEN** elle n'est pas renvoyée ni affichée

#### Scenario: Ordre d'affichage
- **WHEN** plusieurs annonces sont en cours
- **THEN** elles sont affichées de haut en bas par date de fin croissante

#### Scenario: Aucune annonce
- **WHEN** aucune annonce n'est en cours
- **THEN** le widget affiche un message indiquant l'absence d'annonce

### Requirement: Accès authentifié et horloge serveur
La lecture des annonces SHALL exiger une session valide (401 sinon). La réponse MUST inclure l'instant serveur courant, et le compte à rebours MUST se baser sur cet instant plutôt que sur l'horloge du navigateur.

#### Scenario: Sans session
- **WHEN** un appel de lecture est fait sans session valide
- **THEN** la réponse est 401

#### Scenario: Horloge navigateur décalée
- **WHEN** l'horloge du navigateur diffère de celle du serveur
- **THEN** le temps restant affiché correspond à la différence entre la date de fin et l'horloge serveur

### Requirement: Compte à rebours temps réel
Une annonce marquée compte à rebours SHALL afficher le temps restant jusqu'à sa date de fin en mois, jours, heures, minutes et secondes, mis à jour chaque seconde sans rechargement. Le calcul MUST être calendaire en fuseau `Europe/Paris` (un mois est un mois civil, non 30 jours). Les unités de tête égales à zéro MUST être masquées ; les unités suivantes, même nulles, restent affichées.

#### Scenario: Décompte vivant
- **WHEN** le widget est ouvert sur une annonce avec compte à rebours
- **THEN** le temps restant diminue chaque seconde

#### Scenario: Unités de tête masquées
- **WHEN** il reste 2 jours, 0 heure, 5 minutes et 3 secondes
- **THEN** l'affichage commence aux jours (pas de mois) et conserve l'heure nulle

#### Scenario: Annonce sans compte à rebours
- **WHEN** une annonce n'est pas marquée compte à rebours
- **THEN** seul son titre est affiché

### Requirement: Réécriture périodique des textes
Le titre de chaque annonce affichée SHALL être intégralement réécrit avec l'animation d'écriture, même si le texte est inchangé, à intervalle aléatoire propre à chaque annonce, tiré entre 15 secondes et 60 secondes (bornes incluses, valeurs fixes non paramétrables). Un nouvel intervalle est tiré après chaque réécriture, et les annonces ne sont pas réécrites en même temps par construction. Les chiffres du compte à rebours, mis à jour chaque seconde, ne sont pas réécrits. La réécriture MUST respecter `ASSISTANT_UI_TYPING_ENABLED` et `prefers-reduced-motion`, et MUST s'arrêter quand le widget ou l'annonce n'est plus affiché.

#### Scenario: Réécriture après un délai aléatoire
- **WHEN** le délai tiré pour une annonce s'écoule alors que le widget est affiché
- **THEN** le titre de cette annonce est effacé puis réécrit caractère par caractère, et un nouveau délai est tiré

#### Scenario: Bornes du délai
- **WHEN** un délai est tiré
- **THEN** il est compris entre 15 et 60 secondes

#### Scenario: Annonces indépendantes
- **WHEN** plusieurs annonces sont affichées
- **THEN** chacune suit son propre délai, sans réécriture synchronisée

#### Scenario: Réécriture désactivée
- **WHEN** l'écriture animée est désactivée ou la réduction des animations est active
- **THEN** aucune réécriture animée n'est jouée

### Requirement: Curseur clignotant en fin de titre
À la fin du texte de chaque annonce, un curseur SHALL clignoter en permanence, y compris une fois l'écriture terminée, comme si la frappe allait reprendre. Il s'applique aux annonces avec et sans compte à rebours et se place après le titre (et après la date de fin pour une annonce sans compte à rebours).

#### Scenario: Annonce simple
- **WHEN** le texte d'une annonce sans compte à rebours est entièrement écrit
- **THEN** un curseur continue de clignoter après le dernier caractère

#### Scenario: Annonce avec compte à rebours
- **WHEN** le titre d'une annonce avec compte à rebours est entièrement écrit
- **THEN** un curseur continue de clignoter après le titre

#### Scenario: Mouvement réduit
- **WHEN** l'utilisateur a activé la réduction des animations
- **THEN** le curseur est affiché fixe, sans clignotement

### Requirement: Date de fin des annonces simples
Une annonce sans compte à rebours SHALL afficher sa date et son heure de fin, en heure de Paris, à la suite de son titre sur la même ligne (format `jj/mm/aaaa à hh:mm`). Une annonce avec compte à rebours MUST NOT afficher cette date.

#### Scenario: Annonce sans compte à rebours
- **WHEN** une annonce sans compte à rebours se termine le 27/10/2026 à 18:00 heure de Paris
- **THEN** son titre est suivi de « fin le 27/10/2026 à 18:00 »

#### Scenario: Annonce avec compte à rebours
- **WHEN** une annonce avec compte à rebours est affichée
- **THEN** seule la mention du temps restant indique l'échéance

### Requirement: Espacement régulier de la liste
La séparation entre le titre du widget et la première annonce SHALL être identique à celle entre deux annonces.

#### Scenario: Première annonce
- **WHEN** le widget affiche plusieurs annonces
- **THEN** l'espace et le trait de séparation au-dessus de la première annonce sont les mêmes qu'entre deux annonces

### Requirement: Texte centré
Le titre du widget et le texte des annonces SHALL être centrés dans la largeur du widget.

#### Scenario: Titre sur plusieurs lignes
- **WHEN** le texte d'une annonce occupe plusieurs lignes
- **THEN** chaque ligne est centrée

### Requirement: Disparition en temps réel
Quand la date de fin d'une annonce est atteinte alors que le widget est affiché, l'annonce MUST disparaître sans action de l'utilisateur.

#### Scenario: Fin du compte à rebours
- **WHEN** le décompte d'une annonce atteint zéro
- **THEN** l'annonce est retirée de la liste, et le message d'absence s'affiche s'il n'en reste aucune

### Requirement: Fuseau horaire Europe/Paris
Toutes les dates d'annonce SHALL être saisies et affichées en fuseau `Europe/Paris` et stockées de façon non ambiguë (UTC).

#### Scenario: Saisie en heure de Paris
- **WHEN** l'administrateur saisit une date de fin à 18:00
- **THEN** l'annonce se termine à 18:00 heure de Paris, été comme hiver

### Requirement: Annonces incluses dans la sauvegarde
Les annonces SHALL être exportées et restaurées avec le reste de la base par le mécanisme de sauvegarde existant.

#### Scenario: Export et restauration
- **WHEN** l'administrateur télécharge puis restaure une sauvegarde
- **THEN** les annonces sont retrouvées à l'identique
