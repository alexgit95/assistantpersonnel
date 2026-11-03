## ADDED Requirements

### Requirement: Vérification périodique du kiosque
Le kiosque SHALL effectuer une vérification de la configuration des widgets et de la version du serveur toutes les 60 secondes, à intervalle fixe, tant que la page d'accueil est ouverte. La vérification MUST s'exécuter sans interaction de l'utilisateur et MUST NOT ajouter de route serveur.

#### Scenario: Vérification toutes les minutes
- **WHEN** la page d'accueil est ouverte et aucune condition d'exclusion ne s'applique
- **THEN** la configuration et la version sont vérifiées au plus 60 secondes après la vérification précédente

### Requirement: Rafraîchissement de la configuration des widgets
À chaque vérification, le kiosque SHALL relire la liste des applications du profil connecté, ou de l'écran invité sans session, et la comparer à celle affichée. Si elle diffère (emplacement, activation ou réglage d'un widget), le kiosque MUST ré-afficher les widgets avec la nouvelle liste ; si elle est identique, il MUST NOT ré-afficher. En cas d'échec de lecture, il MUST conserver l'affichage actuel et réessayer à la vérification suivante.

#### Scenario: Emplacement modifié
- **WHEN** un profil connecté déplace un widget depuis l'onglet de configuration
- **THEN** le kiosque affiche la nouvelle disposition au plus une minute plus tard, sans rechargement manuel

#### Scenario: Réglage de widget modifié
- **WHEN** un profil change sa ville météo ou ses arrêts depuis un smartphone
- **THEN** le widget correspondant du kiosque reflète ce réglage au plus une minute plus tard

#### Scenario: Écran invité modifié
- **WHEN** l'administrateur modifie la configuration de l'écran invité et que personne n'est connecté
- **THEN** le kiosque affiche la nouvelle configuration de l'invité au plus une minute plus tard

#### Scenario: Configuration inchangée
- **WHEN** la liste relue est identique à celle affichée
- **THEN** aucun widget n'est ré-affiché ni réinitialisé

#### Scenario: Échec de lecture
- **WHEN** la lecture de la liste des applications échoue
- **THEN** l'affichage actuel est conservé et la lecture est retentée à la vérification suivante

### Requirement: Conditions d'exclusion
Le kiosque MUST NOT ré-afficher les widgets ni recharger la page lorsque l'onglet est masqué, lorsqu'une connexion ou une déconnexion est en cours, ou lorsqu'une session d'enrôlement est dans l'état `COUNTDOWN`, `CAPTURING` ou `REVIEW`. Une session d'enrôlement `WAITING` (QR invité affiché en attente) MUST NOT bloquer la vérification. Une vérification exclue MUST être retentée à la vérification suivante.

#### Scenario: Enrôlement en cours
- **WHEN** un smartphone a démarré une capture (compte à rebours, capture ou choix de l'avatar) et que la minute de vérification arrive
- **THEN** aucun ré-affichage ni rechargement n'a lieu et la vérification est retentée à la minute suivante

#### Scenario: Onglet masqué
- **WHEN** l'onglet du kiosque est masqué
- **THEN** la vérification ne ré-affiche ni ne recharge rien

#### Scenario: QR invité en attente
- **WHEN** la session d'enrôlement est à l'état `WAITING` parce que personne n'est connecté
- **THEN** la vérification s'exécute normalement

### Requirement: Rechargement de la page sur nouvelle version
Quand la version du serveur diffère de celle embarquée dans la page chargée, le kiosque SHALL recharger la page, mais uniquement lorsque personne n'est connecté et qu'aucune condition d'exclusion ne s'applique. Tant qu'un profil est connecté, le rechargement MUST être différé, sans interrompre l'utilisateur. La clé de kiosque MUST rester disponible après le rechargement.

#### Scenario: Nouvelle version sur l'écran invité
- **WHEN** le serveur a été redéployé avec une version différente et que personne n'est connecté
- **THEN** la page se recharge à la vérification suivante et affiche la nouvelle version

#### Scenario: Nouvelle version pendant une session
- **WHEN** une version différente est détectée alors qu'un profil est connecté
- **THEN** la page n'est pas rechargée, et elle l'est dès que plus personne n'est connecté

#### Scenario: Clé de kiosque conservée
- **WHEN** la page est rechargée sur nouvelle version
- **THEN** les appels réservés au kiosque restent autorisés sans ressaisie de la clé

### Requirement: Garde-fou contre une boucle de rechargements
Le kiosque MUST NOT recharger la page lorsque la version du serveur est vide ou égale à celle de la page. Il MUST recharger la page une seule fois pour une version serveur donnée : cette version est mémorisée avant le rechargement et le kiosque MUST NOT recharger de nouveau tant que le serveur annonce cette même version.

#### Scenario: Versions identiques
- **WHEN** la version du serveur est égale à celle de la page
- **THEN** aucun rechargement n'a lieu

#### Scenario: Version inconnue
- **WHEN** le serveur renvoie une version vide
- **THEN** aucun rechargement n'a lieu

#### Scenario: Pas de second rechargement
- **WHEN** la page a déjà été rechargée pour la version `1.2.0-abc1234` et affiche toujours une version différente (cache stale)
- **THEN** le kiosque ne recharge pas une seconde fois pour cette version

### Requirement: Revalidation des ressources du kiosque
Le serveur SHALL servir les fichiers `/js/**` et `/css/**` avec `Cache-Control: no-cache`, afin qu'un rechargement après un déploiement récupère le nouveau code. Les ressources de `/vendor/**` MUST conserver leur cache de 30 jours.

#### Scenario: Ressources du kiosque
- **WHEN** le navigateur demande `/js/home.js`
- **THEN** la réponse contient `Cache-Control: no-cache`

#### Scenario: Modèles de reconnaissance
- **WHEN** le navigateur demande un fichier sous `/vendor/face-api`
- **THEN** la réponse conserve son cache de 30 jours
