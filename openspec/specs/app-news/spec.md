# Purpose

Define the news widget: admin-managed RSS categories, per-profile category selection, rotation and safe feed retrieval.

## Requirements

### Requirement: Widget Actualités activable par profil
Le système SHALL proposer une application « Actualités » (clé `news`) activable et affectable à un emplacement comme les autres widgets, avec une configuration propre à chaque profil.

#### Scenario: Application disponible
- **WHEN** un profil ouvre la page de configuration
- **THEN** l'application « Actualités » est listée parmi les widgets affectables

### Requirement: Sélection des catégories par profil
Le système SHALL permettre à chaque profil de cocher les catégories d'actualités à afficher parmi celles définies par l'administrateur. La sélection est enregistrée dans la configuration du profil. Les identifiants inconnus MUST être ignorés à l'affichage, et le serveur MUST refuser l'enregistrement d'un identifiant de catégorie inexistant.

#### Scenario: Cocher plusieurs catégories
- **WHEN** un profil coche les catégories A et B
- **THEN** seules A et B sont affichées dans son widget, dans l'ordre d'affichage défini par l'administrateur

#### Scenario: Catégorie supprimée
- **WHEN** une catégorie cochée par un profil est supprimée par l'administrateur
- **THEN** le widget ignore cette catégorie sans erreur

#### Scenario: Aucune catégorie cochée
- **WHEN** un profil n'a coché aucune catégorie
- **THEN** le widget affiche un message invitant à en choisir

#### Scenario: Isolation par profil
- **WHEN** deux profils cochent des catégories différentes
- **THEN** chacun ne voit que ses propres catégories

### Requirement: Entrées d'une catégorie
Pour chaque catégorie affichée, le widget SHALL présenter au plus 10 entrées du flux, chacune par son titre en texte brut. Un clic sur un titre MUST déplier la description de l'entrée, également en texte brut, sans interpréter de HTML. L'effet machine à écrire de l'application SHALL s'appliquer aux titres ; la description MUST s'afficher directement à l'ouverture.

#### Scenario: Plafond des entrées
- **WHEN** un flux contient plus de 10 entrées
- **THEN** seules les 10 premières sont affichées

#### Scenario: Dépliage
- **WHEN** l'utilisateur clique sur le titre d'une entrée
- **THEN** sa description s'affiche en texte brut sous le titre

#### Scenario: Contenu HTML dans le flux
- **WHEN** une description contient des balises HTML
- **THEN** elles sont supprimées et aucun script ni image n'est exécuté ou chargé

### Requirement: Rotation automatique des catégories
Lorsque plusieurs catégories sont sélectionnées, le widget SHALL passer à la catégorie suivante après 15 secondes sans action de l'utilisateur, puis recommencer à la première après la dernière. Un clic sur une flèche ou sur une entrée MUST remettre le compteur à zéro. Tant qu'une entrée est dépliée, la rotation MUST être suspendue ; l'entrée se replie automatiquement après 20 secondes sans action, et le compteur de 15 secondes repart alors de zéro. La rotation MUST NOT être suspendue lorsque l'onglet est masqué.

#### Scenario: Passage automatique
- **WHEN** 15 secondes s'écoulent sans action sur la catégorie affichée
- **THEN** la catégorie suivante est affichée

#### Scenario: Action utilisateur
- **WHEN** l'utilisateur clique sur une flèche ou sur une entrée
- **THEN** le compteur de 15 secondes repart de zéro

#### Scenario: Entrée dépliée
- **WHEN** une entrée est dépliée
- **THEN** la rotation est suspendue jusqu'au repli de l'entrée

#### Scenario: Repli automatique
- **WHEN** 20 secondes s'écoulent sans action sur une entrée dépliée
- **THEN** elle se replie et un nouveau compteur de 15 secondes démarre

#### Scenario: Une seule catégorie
- **WHEN** une seule catégorie est sélectionnée
- **THEN** aucune rotation n'a lieu et aucune flèche n'est affichée

### Requirement: Navigation par flèches
Lorsque plusieurs catégories sont sélectionnées, le widget SHALL afficher des flèches précédent et suivant permettant de changer de catégorie. La navigation MUST boucler : suivant depuis la dernière catégorie affiche la première, précédent depuis la première affiche la dernière.

#### Scenario: Flèche suivante
- **WHEN** l'utilisateur clique sur la flèche suivante depuis la dernière catégorie
- **THEN** la première catégorie est affichée

#### Scenario: Flèche précédente
- **WHEN** l'utilisateur clique sur la flèche précédente depuis la première catégorie
- **THEN** la dernière catégorie est affichée

### Requirement: Récupération et cache des flux RSS
Le serveur SHALL récupérer les flux RSS et Atom des catégories via le client HTTP sortant (proxy inclus) et les analyser avec la bibliothèque Rome en refusant les DOCTYPE. Le contenu de chaque catégorie MUST être mis en cache en mémoire pendant 10 minutes, de sorte qu'un seul appel sortant ait lieu par catégorie et par période, quel que soit le nombre de profils. La taille de la réponse et les délais d'appel MUST être plafonnés. En cas d'échec, la dernière version connue SHALL être servie avec une notice d'indisponibilité ; sans version connue, une notice seule est renvoyée.

#### Scenario: Cache partagé
- **WHEN** deux profils consultent la même catégorie dans les 10 minutes
- **THEN** un seul appel sortant est effectué

#### Scenario: Flux indisponible
- **WHEN** le flux ne répond pas ou est invalide
- **THEN** le widget affiche la dernière version connue avec une notice, ou une notice seule, sans erreur bloquante

#### Scenario: Réponse trop volumineuse
- **WHEN** la réponse dépasse la taille maximale autorisée
- **THEN** elle est rejetée et traitée comme un flux indisponible

### Requirement: Protection contre les appels vers le réseau local
Le serveur MUST n'appeler que des URL `https`. À chaque récupération, il MUST résoudre le nom d'hôte et refuser toute adresse loopback, privée, link-local, multicast ou non spécifiée, et MUST NOT suivre les redirections.

#### Scenario: Adresse locale
- **WHEN** l'URL d'une catégorie résout vers une adresse privée ou loopback
- **THEN** aucun appel n'est émis et la catégorie est traitée comme indisponible

#### Scenario: Redirection
- **WHEN** le flux répond par une redirection
- **THEN** elle n'est pas suivie

### Requirement: Mode test du widget Actualités
Lorsque le mode test des API externes est actif, le widget SHALL afficher des catégories et entrées factices sans aucun appel sortant, avec le badge « MODE TEST ».

#### Scenario: Mode test actif
- **WHEN** le mode test est actif
- **THEN** aucun appel réseau n'est émis et le badge « MODE TEST » est visible
