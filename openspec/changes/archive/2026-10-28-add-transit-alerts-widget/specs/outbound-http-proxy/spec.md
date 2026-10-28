## ADDED Requirements

### Requirement: Proxy sortant configurable depuis l'administration
Le système SHALL permettre à l'administrateur authentifié de configurer, depuis la page `/admin`, un proxy HTTP/HTTPS par lequel passent tous les appels HTTP et HTTPS sortants du serveur (PRIM, Open-Meteo et tout futur client). Aucune variable d'environnement ne MUST configurer le proxy. Sans proxy activé, les appels MUST être directs.

#### Scenario: Proxy activé
- **WHEN** l'administrateur enregistre `http://proxy:3128` et l'active
- **THEN** les appels vers PRIM et Open-Meteo passent par ce proxy

#### Scenario: Aucun proxy
- **WHEN** le proxy est désactivé ou jamais configuré
- **THEN** les appels sont directs

#### Scenario: Accès réservé
- **WHEN** un utilisateur non administrateur appelle le formulaire du proxy
- **THEN** l'accès est refusé

### Requirement: Prise en compte à chaud
Le système SHALL appliquer une modification du proxy aux appels suivants sans redémarrage de l'application.

#### Scenario: Changement sans redémarrage
- **WHEN** l'administrateur modifie ou désactive le proxy
- **THEN** les appels sortants suivants utilisent la nouvelle configuration

### Requirement: Authentification et exclusions
Le système SHALL accepter des identifiants de proxy optionnels (utilisateur et mot de passe, stockés en clair en base car l'application est destinée à un réseau local) et une liste d'hôtes exclus. Le mot de passe MUST ne jamais être réaffiché dans l'interface ni apparaître dans les logs ; un champ mot de passe laissé vide lors d'une modification conserve la valeur existante.

#### Scenario: Proxy authentifié
- **WHEN** des identifiants sont enregistrés
- **THEN** ils sont présentés au proxy, y compris pour les connexions HTTPS tunnelisées

#### Scenario: Hôte exclu
- **WHEN** l'hôte cible figure dans la liste d'exclusion
- **THEN** l'appel est direct

#### Scenario: Mot de passe non réaffiché
- **WHEN** l'administrateur rouvre le formulaire
- **THEN** le mot de passe n'est pas présent dans la page et une modification avec champ vide le conserve

### Requirement: Test de connexion
Le système SHALL proposer un bouton « Tester la connexion » qui effectue un appel de test sortant via la configuration saisie, sans l'enregistrer ni consommer le budget PRIM, et affiche le résultat (succès ou motif d'échec sans divulguer les identifiants).

#### Scenario: Test réussi
- **WHEN** l'administrateur lance le test avec un proxy valide
- **THEN** un message de succès est affiché

#### Scenario: Test en échec
- **WHEN** le proxy est injoignable ou refuse l'authentification
- **THEN** un message d'erreur explicite est affiché et la configuration enregistrée est inchangée

#### Scenario: Budget PRIM préservé
- **WHEN** le test est lancé
- **THEN** le compteur d'appels PRIM n'est pas incrémenté

### Requirement: Exclusion de la sauvegarde
Les réglages du proxy et le compteur d'appels aux API externes SHALL être exclus de l'export et de l'import de la sauvegarde admin.

#### Scenario: Export sans proxy
- **WHEN** l'administrateur exporte une sauvegarde
- **THEN** le fichier ne contient ni réglage de proxy ni identifiants

#### Scenario: Import conservant le proxy
- **WHEN** une sauvegarde est restaurée
- **THEN** la configuration de proxy actuelle est conservée

### Requirement: Validation et délais
Le système SHALL valider l'URL du proxy à l'enregistrement et refuser une valeur invalide avec un message explicite, sans modifier la configuration active. Les appels sortants MUST conserver des délais de connexion et de lecture bornés.

#### Scenario: URL invalide
- **WHEN** l'administrateur saisit une URL qui n'est pas http ou https valide
- **THEN** l'enregistrement est refusé avec un message explicite et la configuration précédente reste active

#### Scenario: Proxy lent
- **WHEN** le proxy ne répond pas dans le délai
- **THEN** l'appel échoue sans bloquer indéfiniment et le widget concerné affiche son message d'indisponibilité
