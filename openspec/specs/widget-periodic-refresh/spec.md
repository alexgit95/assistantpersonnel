# Purpose

Define periodic random reload and retype cycles of the weather and weight widgets.

## Requirements

### Requirement: Cycle aléatoire par widget
Le système SHALL exécuter pour chaque instance des widgets météo et poids un cycle de rafraîchissement dont le délai est tiré aléatoirement entre 15 s et 60 s inclus, puis ré-armé après chaque exécution. Le délai MUST être propre à chaque widget, et non à chaque élément de texte.

#### Scenario: Délai borné
- **WHEN** un cycle est armé
- **THEN** son délai est compris entre 15 000 et 60 000 ms

#### Scenario: Un cycle par widget
- **WHEN** le widget météo contient plusieurs textes
- **THEN** un seul cycle les ré-écrit tous ensemble

### Requirement: Rechargement et réécriture de la météo
À chaque cycle, le widget météo SHALL recharger les prévisions puis ré-écrire avec l'effet machine à écrire la ville, la météo actuelle et les prévisions, y compris en vue compacte, même si les textes sont inchangés.

#### Scenario: Cycle réussi
- **WHEN** le cycle se déclenche et le rechargement réussit
- **THEN** tous les textes du widget sont ré-écrits avec les données reçues

#### Scenario: Échec du rechargement
- **WHEN** le rechargement périodique échoue
- **THEN** l'affichage précédent est conservé et aucun message d'erreur n'est affiché

### Requirement: Rechargement et réécriture du poids
À chaque cycle, le widget poids SHALL recharger les mesures, ré-écrire leurs textes et rejouer l'animation de tracé du graphique, sauf si l'utilisateur a demandé la réduction des animations, auquel cas le tracé reste statique.

#### Scenario: Tracé rejoué
- **WHEN** le cycle se déclenche alors que le graphique est affiché
- **THEN** la ligne du graphique rejoue son animation de tracé

#### Scenario: Réduction des animations
- **WHEN** `prefers-reduced-motion: reduce` est actif
- **THEN** le tracé n'est pas animé

### Requirement: Pause pendant la saisie du poids
Le widget poids SHALL ignorer un cycle tant que la carte est retournée sur la face de saisie ou qu'un champ du formulaire a le focus, puis ré-armer le cycle suivant.

#### Scenario: Carte retournée
- **WHEN** le cycle se déclenche alors que la carte est retournée
- **THEN** aucun rechargement ni réécriture n'est effectué et un nouveau délai est armé

#### Scenario: Statut préservé
- **WHEN** le message « Mesure ajoutée. » est affiché pendant la saisie
- **THEN** il n'est pas effacé par un cycle

### Requirement: Arrêt et désactivation du cycle
Le cycle SHALL s'arrêter quand le widget n'est plus rattaché au document et SHALL ne pas démarrer quand l'animation d'écriture est désactivée.

#### Scenario: Widget retiré
- **WHEN** le widget est retiré du document
- **THEN** le cycle ne se ré-arme plus

#### Scenario: Écriture désactivée
- **WHEN** l'option d'animation d'écriture est désactivée
- **THEN** aucun cycle n'est démarré

### Requirement: Délai partagé avec les annonces
Le tirage du délai SHALL être fourni par un module partagé utilisé à la fois par les annonces, la météo et le poids, avec des bornes identiques de 15 s à 60 s.

#### Scenario: Annonces inchangées
- **WHEN** une annonce est ré-écrite
- **THEN** son cycle reste propre à l'annonce et utilise le même tirage de délai
