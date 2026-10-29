# Purpose

Define profile-specific transit favorites and service-alert behavior.

## Requirements

### Requirement: Widget Transports activable par profil
Le système SHALL proposer une application « Transports » (clé `transit`) activable et affectable à un emplacement comme les autres widgets, avec une configuration propre à chaque profil.

#### Scenario: Application disponible
- **WHEN** un profil ouvre la page de configuration
- **THEN** l'application « Transports » est listée parmi les widgets affectables

### Requirement: Lignes favorites limitées à quatre
Le système SHALL permettre à chaque profil d'enregistrer au plus 4 lignes favorites, sans imposer de gare. Chaque favori MUST référencer une ligne active du catalogue IDFM par son identifiant de ligne et MUST être unique dans la configuration du profil. Le serveur MUST refuser une cinquième ligne ou un identifiant inconnu.

#### Scenario: Ajout d'une ligne
- **WHEN** un profil enregistre la ligne « RER A »
- **THEN** son identifiant IDFM est conservé dans la configuration du profil

#### Scenario: Limite atteinte
- **WHEN** un profil possédant déjà 4 lignes en ajoute une cinquième
- **THEN** le serveur répond 400 et la configuration est inchangée

#### Scenario: Isolation par profil
- **WHEN** deux profils configurent des favoris différents
- **THEN** chacun ne voit que ses propres favoris et alertes

### Requirement: Catalogue local des lignes
Le système SHALL proposer les lignes actives du fichier `docs/referentiel-des-lignes.json` dans l'interface de configuration, avec recherche locale par nom et mode de transport. Les identifiants MUST être convertis en identifiants PRIM sous la forme `line:IDFM:<id_line>`. Le catalogue MUST être embarqué dans le JAR et ne MUST nécessiter aucun appel HTTP.

#### Scenario: Recherche d'une ligne
- **WHEN** l'utilisateur cherche « RER A »
- **THEN** le catalogue propose la ligne active avec l'identifiant correspondant à PRIM

#### Scenario: Ligne sans perturbation
- **WHEN** une ligne n'apparaît dans aucune alerte PRIM
- **THEN** elle reste sélectionnable depuis le catalogue local

### Requirement: Alertes trafic par ligne favorite
Pour chaque ligne favorite, le système SHALL afficher les perturbations dont `impactedSections[].lineId` correspond exactement à l'identifiant PRIM de la ligne, triées de la plus sévère à la moins sévère, avec une pastille de ligne. Les lignes perturbées MUST être visuellement mises en avant selon leur sévérité. Le widget SHALL afficher le titre et le résumé de chaque alerte et permettre de développer le contenu du champ PRIM `message` sous forme de texte lisible sans exécuter son HTML. Si une alerte ne fournit pas de section structurée, le système MAY l'associer uniquement si son titre commence par le libellé complet de la ligne. Sans alerte correspondante, le widget MUST afficher « Trafic normal ».

#### Scenario: Correspondance structurée
- **WHEN** `impactedSections` contient l'identifiant PRIM d'une ligne favorite
- **THEN** la perturbation est affichée pour cette ligne

#### Scenario: Détails de l'alerte
- **WHEN** une perturbation correspond à une ligne favorite et contient un champ `message`
- **THEN** le widget affiche son titre et résumé, met la ligne en évidence selon la sévérité, et permet d'ouvrir les détails du message sans interpréter le HTML reçu

#### Scenario: Alerte sans section structurée
- **WHEN** une perturbation n'a pas de `impactedSections` mais son titre commence exactement par le libellé de la ligne favorite
- **THEN** elle est affichée pour cette ligne sans analyser le corps HTML

#### Scenario: Aucune alerte
- **WHEN** la ligne favorite n'a aucune perturbation correspondante
- **THEN** le widget affiche « Trafic normal »

### Requirement: Réponse globale PRIM actualisée toutes les dix minutes
Le système SHALL actualiser une seule fois la réponse globale Messages Info Trafic toutes les 10 minutes entre 5 h et 22 h, heure de Paris, puis la partager entre les profils. Une première demande sans réponse en cache MAY déclencher immédiatement l'actualisation. Les lignes favorites MUST être filtrées localement à partir de cette réponse. Les demandes simultanées MUST être fusionnées en un seul appel.

#### Scenario: Actualisation périodique
- **WHEN** l'application est en mode réel pendant la plage 5 h–22 h
- **THEN** une requête globale PRIM est effectuée toutes les 10 minutes au maximum, indépendamment du nombre de profils ou de lignes favorites

#### Scenario: Requêtes concurrentes
- **WHEN** plusieurs demandes arrivent simultanément alors que la réponse globale est expirée
- **THEN** un seul appel à PRIM est effectué

#### Scenario: Filtrage sans requête par ligne
- **WHEN** plusieurs lignes favorites sont affichées depuis une réponse globale en cache
- **THEN** chaque ligne est filtrée localement sans appel PRIM supplémentaire

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
Le système SHALL ne faire aucun appel à PRIM entre 22 h et 5 h, heure de Paris, par défaut et de façon configurable. Pendant la pause, le widget MUST afficher un court message de pause et la dernière valeur connue le cas échéant. Le catalogue local des lignes MUST rester consultable.

#### Scenario: Alertes pendant la pause
- **WHEN** un profil consulte ses alertes à 23 h
- **THEN** aucun appel n'est effectué et le widget affiche « Alertes en pause jusqu'à 5 h » avec la dernière valeur connue

#### Scenario: Catalogue pendant la pause
- **WHEN** l'utilisateur configure ses lignes favorites à 23 h
- **THEN** le catalogue local reste disponible sans appel externe

#### Scenario: Reprise
- **WHEN** une alerte est demandée à 5 h 01 avec un cache expiré
- **THEN** l'appel à PRIM est effectué

### Requirement: Clé API et indisponibilité
La clé PRIM SHALL être saisie par l'administrateur depuis `/admin` et stockée en clair en base ; aucune variable d'environnement ni propriété ne MUST la fournir. Elle SHALL être envoyée uniquement par le serveur, lue à chaque appel PRIM, et ne MUST jamais être exposée au navigateur ni écrite dans les logs. Tant qu'aucune clé n'est enregistrée, le système MUST NOT appeler PRIM, que ce soit à la demande ou par l'actualisation planifiée, et MUST NOT consommer le budget journalier. Sans clé ou si PRIM est indisponible, le widget MUST afficher un message sans affecter les autres widgets.

#### Scenario: Clé absente
- **WHEN** aucune clé PRIM n'est enregistrée en mode réel
- **THEN** le widget indique que le service n'est pas configuré, aucun appel PRIM n'est émis et le compteur journalier n'est pas incrémenté

#### Scenario: Clé enregistrée à chaud
- **WHEN** l'administrateur enregistre une clé valide
- **THEN** l'actualisation suivante utilise cette clé sans redémarrage

#### Scenario: Clé supprimée
- **WHEN** l'administrateur supprime la clé
- **THEN** les appels PRIM cessent dès l'actualisation suivante et le widget indique que le service n'est pas configuré

#### Scenario: PRIM indisponible
- **WHEN** PRIM répond en erreur ou dépasse le délai
- **THEN** la dernière valeur connue est servie avec son heure, ou un message d'indisponibilité si aucune n'existe

### Requirement: Rafraîchissement périodique du widget
Le widget Transports SHALL réécrire indépendamment le titre, le résumé et les détails des alertes de chaque ligne favorite à un intervalle aléatoire de 15 à 60 secondes, bornes incluses, en réutilisant l'animation d'écriture. Ces cycles d'affichage MUST NOT déclencher d'appel PRIM supplémentaire. Un curseur MUST clignoter en permanence après les textes réécrits, comme dans le widget Annonces. Les animations MUST respecter le réglage d'écriture et `prefers-reduced-motion`.

#### Scenario: Réécriture d'une ligne favorite
- **WHEN** le délai aléatoire d'une ligne favorite s'écoule
- **THEN** son texte est effacé puis réécrit caractère par caractère, et un nouveau délai entre 15 et 60 secondes est tiré

#### Scenario: Curseur en fin de texte
- **WHEN** le texte d'une ligne favorite est entièrement écrit
- **THEN** un curseur clignote après son dernier caractère

#### Scenario: Réécritures indépendantes
- **WHEN** plusieurs lignes favorites sont affichées
- **THEN** chacune suit son propre délai et n'est pas réécrite en même temps par construction

#### Scenario: Réduction des animations
- **WHEN** l'écriture est désactivée ou `prefers-reduced-motion: reduce` est actif
- **THEN** aucune réécriture animée ni curseur clignotant n'est joué

### Requirement: Date de dernière actualisation
Le widget SHALL afficher la date et l'heure de la dernière actualisation réussie de la réponse PRIM, au format français et en heure de Paris. Une réponse de repli MUST conserver son horodatage d'origine.

#### Scenario: Actualisation réussie
- **WHEN** une nouvelle réponse globale PRIM est reçue
- **THEN** le widget affiche sa date et son heure d'actualisation

#### Scenario: Réponse de repli
- **WHEN** l'actualisation échoue ou le budget est épuisé
- **THEN** les données précédentes restent affichées avec leur horodatage d'origine

### Requirement: Mode TEST des API externes
Le système SHALL permettre à l'administrateur d'activer un mode TEST persistant depuis l'administration. Dans ce mode, les appels de données météo MUST utiliser `docs/response-open-meteo.json` et les appels PRIM MUST utiliser `docs/response_info-traffic.json`. La recherche météo MUST toujours retourner Paris depuis le fixture, quel que soit le texte saisi, et les prévisions MUST rester cohérentes avec Paris. Aucun appel HTTP externe ni réservation de quota MUST être effectué pour ces réponses simulées. Les interfaces concernées MUST signaler clairement le mode TEST.

#### Scenario: Mode TEST météo activé
- **WHEN** l'administrateur active le mode TEST et qu'un widget météo cherche une ville ou demande une prévision
- **THEN** Paris et ses prévisions cohérentes avec le fixture sont renvoyés sans appel Open-Meteo, quel que soit le texte recherché

#### Scenario: Mode TEST PRIM activé
- **WHEN** l'administrateur active le mode TEST et qu'un widget Transports demande des alertes
- **THEN** la réponse PRIM locale est utilisée sans appel réseau ni réservation de quota

#### Scenario: Référentiel et fixtures dans le JAR
- **WHEN** l'application est exécutée depuis l'image Docker
- **THEN** le catalogue des lignes et les deux fixtures météo/PRIM restent disponibles comme ressources de l'application

#### Scenario: Retour au mode réel
- **WHEN** l'administrateur désactive le mode TEST
- **THEN** les appels de données utilisent les gateways réelles et les règles de quota et de pause s'appliquent
