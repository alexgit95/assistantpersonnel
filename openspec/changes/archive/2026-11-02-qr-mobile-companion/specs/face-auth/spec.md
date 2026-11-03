## MODIFIED Requirements

### Requirement: Connexion par visage
Le système SHALL ouvrir une session pour le profil dont la distance d'embedding avec le visage détecté est inférieure au seuil configuré. L'appel `POST /api/auth/login` MUST être réservé au Pi par la clé de kiosque.

#### Scenario: Visage reconnu
- **WHEN** un visage correspond à un profil sous le seuil
- **THEN** une session est ouverte pour ce profil et ses applications sont affichées

#### Scenario: Visage inconnu
- **WHEN** aucun profil ne correspond sous le seuil
- **THEN** aucune session n'est ouverte et un état « non reconnu » est affiché

#### Scenario: Appel sans clé de kiosque
- **WHEN** un client sans clé de kiosque appelle `POST /api/auth/login`
- **THEN** la réponse est 403 et aucune session n'est créée

### Requirement: Session glissante de 10 minutes
La session faciale du Pi SHALL expirer 10 minutes (configurable) après la dernière détection du profil courant. L'appel `POST /api/auth/heartbeat` MUST être réservé au Pi par la clé de kiosque. Les sessions ouvertes pour un smartphone (transfert ou enrôlement) MUST NOT être glissantes : elles ont une durée fixe et ne sont jamais prolongées.

#### Scenario: Prolongation
- **WHEN** le profil courant est revu pendant la session
- **THEN** l'expiration est repoussée de la durée configurée, avec au plus un heartbeat par 30 s

#### Scenario: Expiration
- **WHEN** le profil courant n'est plus revu pendant 10 minutes
- **THEN** la session est invalidée côté serveur et l'écran revient à la reconnaissance

#### Scenario: Session de smartphone
- **WHEN** un smartphone tente de prolonger sa session par heartbeat
- **THEN** l'appel est refusé car réservé au Pi
