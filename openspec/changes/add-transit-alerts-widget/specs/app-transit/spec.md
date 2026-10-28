## ADDED Requirements

### Requirement: Widget Transports activable par profil
Le système SHALL proposer une application « Transports » (clé `transit`) activable et affectable à un emplacement comme les autres widgets, avec une configuration propre à chaque profil.

#### Scenario: Application disponible
- **WHEN** un profil ouvre la page de configuration
- **THEN** l'application « Transports » est listée parmi les widgets affectables

### Requirement: Favoris gare + ligne limités à trois
Le système SHALL permettre à un profil d'enregistrer au plus 3 favoris, chacun composé d'une gare et d'une ligne. Le serveur MUST refuser un quatrième favori, un favori incomplet ou un doublon.

#### Scenario: Ajout d'un favori
- **WHEN** un profil enregistre la gare « Nation » et la ligne « RER A »
- **THEN** le favori est conservé dans la configuration du profil

#### Scenario: Limite atteinte
- **WHEN** un profil possédant déjà 3 favoris en ajoute un quatrième
- **THEN** le serveur répond 400 et la configuration est inchangée

#### Scenario: Isolation par profil
- **WHEN** deux profils configurent des favoris différents
- **THEN** chacun ne voit que ses propres favoris et alertes

### Requirement: Recherche de gares et de lignes
Le système SHALL permettre de rechercher une gare ou une ligne par nom depuis la page de configuration. Les résultats MUST être mis en cache 24 h et la requête MUST être limitée en longueur.

#### Scenario: Recherche réussie
- **WHEN** l'utilisateur saisit « Nation »
- **THEN** la liste des gares et lignes correspondantes est proposée

#### Scenario: Résultat en cache
- **WHEN** la même recherche est répétée dans les 24 h
- **THEN** aucun appel à PRIM n'est effectué

### Requirement: Alertes trafic par favori
Pour chaque favori, le système SHALL afficher les alertes trafic de la ligne qui concernent la gare choisie ou la ligne entière, triées de la plus sévère à la moins sévère, avec une pastille de ligne. Sans alerte, le widget MUST afficher « Trafic normal ».

#### Scenario: Alerte sur la gare
- **WHEN** une perturbation de la ligne touche la gare du favori
- **THEN** son résumé est affiché pour ce favori

#### Scenario: Alerte ne touchant pas la gare
- **WHEN** une perturbation de la ligne ne touche pas la gare du favori
- **THEN** elle n'est pas affichée pour ce favori

#### Scenario: Aucune alerte
- **WHEN** la ligne n'a aucune perturbation concernant la gare
- **THEN** le widget affiche « Trafic normal »

### Requirement: Une requête par ligne avec cache de 15 minutes
Le système SHALL interroger PRIM une fois par ligne distincte, uniquement à la demande, et mettre en cache la réponse 15 min, partagée entre tous les profils. Le cache MUST avoir une taille bornée, et les requêtes simultanées pour une même ligne MUST être fusionnées en un seul appel.

#### Scenario: Cache valide
- **WHEN** deux profils demandent la même ligne dans les 15 min
- **THEN** un seul appel à PRIM est effectué

#### Scenario: Requêtes concurrentes
- **WHEN** plusieurs demandes arrivent simultanément pour une ligne au cache expiré
- **THEN** un seul appel à PRIM est effectué

### Requirement: Budget journalier persisté
Le système SHALL limiter les appels à PRIM à un budget journalier configurable (inférieur ou égal au quota de 800), compté en heure de Paris et persisté en base pour survivre à un redémarrage. Le compteur MUST être réservé de façon atomique avant chaque appel, et aucun appel ne peut être émis une fois le budget atteint.

#### Scenario: Budget disponible
- **WHEN** le cache est expiré et le budget n'est pas atteint
- **THEN** le compteur est incrémenté puis l'appel est effectué

#### Scenario: Budget épuisé
- **WHEN** le budget journalier est atteint
- **THEN** aucun appel n'est effectué et la dernière valeur connue est servie avec son heure de mise à jour

#### Scenario: Redémarrage
- **WHEN** l'application redémarre en cours de journée
- **THEN** le compteur du jour est relu depuis la base

#### Scenario: Concurrence
- **WHEN** plusieurs appels réservent le budget en même temps
- **THEN** le nombre total d'appels autorisés ne dépasse jamais le budget

### Requirement: Pause nocturne
Le système SHALL ne faire aucun appel à PRIM entre 22 h et 5 h, heure de Paris, par défaut et de façon configurable. Pendant la pause, la recherche MUST être bloquée et le widget MUST afficher un court message de pause, avec la dernière valeur connue le cas échéant.

#### Scenario: Alertes pendant la pause
- **WHEN** un profil consulte ses alertes à 23 h
- **THEN** aucun appel n'est effectué et le widget affiche « Alertes en pause jusqu'à 5 h » avec la dernière valeur connue

#### Scenario: Recherche pendant la pause
- **WHEN** l'utilisateur lance une recherche à 23 h
- **THEN** la recherche est refusée avec un message indiquant la pause

#### Scenario: Reprise
- **WHEN** une alerte est demandée à 5 h 01 avec un cache expiré
- **THEN** l'appel à PRIM est effectué

### Requirement: Clé API et indisponibilité
La clé PRIM SHALL être fournie par la variable d'environnement `ASSISTANT_PRIM_API_KEY`, envoyée uniquement par le serveur, et ne MUST jamais être exposée au navigateur ni écrite dans les logs. Sans clé ou si PRIM est indisponible, le widget MUST afficher un message sans affecter les autres widgets.

#### Scenario: Clé absente
- **WHEN** la variable n'est pas définie
- **THEN** le widget indique que le service n'est pas configuré

#### Scenario: PRIM indisponible
- **WHEN** PRIM répond en erreur ou dépasse le délai
- **THEN** la dernière valeur connue est servie avec son heure, ou un message d'indisponibilité si aucune n'existe

### Requirement: Rafraîchissement périodique du widget
Le widget Transports SHALL recharger ses alertes et ré-écrire ses textes selon le cycle aléatoire de 15 à 60 s des widgets, sans que cela déclenche d'appel PRIM supplémentaire au-delà du cache serveur.

#### Scenario: Cycle du widget
- **WHEN** le cycle se déclenche
- **THEN** les alertes sont relues depuis le serveur et ré-écrites avec l'animation d'écriture
