# Purpose

Define the three deployment modes (`dev`, `onprem`, `remote`), how a mode is selected, the legacy profile aliases, and the README documentation of the modes.

## Requirements

### Requirement: Trois modes de déploiement
Le système SHALL proposer trois modes de déploiement, portés par les profils Spring `dev`, `onprem` et `remote`. `dev` est le mode par défaut quand aucun profil n'est actif. Chaque mode MUST appliquer des valeurs par défaut cohérentes :

| Mode | Base | Assets de reconnaissance | Transport | Mode QR de développement | Clé de kiosque |
|---|---|---|---|---|---|
| `dev` | SQLite | CDN | HTTP | actif | optionnelle |
| `onprem` | SQLite | CDN | HTTP | inactif | obligatoire |
| `remote` | PostgreSQL | embarqués dans l'image | HTTPS et HTTP | inactif | obligatoire |

#### Scenario: Aucun profil
- **WHEN** l'application démarre sans profil actif
- **THEN** le mode `dev` est appliqué (SQLite, CDN, HTTP seul, mode QR de développement)

#### Scenario: Mode onprem
- **WHEN** le profil `onprem` est actif
- **THEN** l'application utilise SQLite, charge les assets de reconnaissance depuis le CDN, écoute en HTTP seul et exige la clé de kiosque

#### Scenario: Mode remote
- **WHEN** le profil `remote` est actif
- **THEN** l'application utilise PostgreSQL, sert les assets depuis `/vendor/face-api`, écoute en HTTPS et en HTTP et exige la clé de kiosque

### Requirement: Sélection du mode
Le mode SHALL pouvoir être choisi par la propriété `spring.profiles.active` d'un fichier de propriétés, par la variable d'environnement `SPRING_PROFILES_ACTIVE` ou par l'argument de lancement `--spring.profiles.active`, avec la précédence standard de Spring Boot. Un profil inconnu MUST NOT changer silencieusement de mode : seuls `dev`, `onprem`, `remote` et leurs alias sont pris en charge.

#### Scenario: Choix par variable d'environnement
- **WHEN** `SPRING_PROFILES_ACTIVE=onprem` est défini au lancement
- **THEN** le mode `onprem` est actif

#### Scenario: Choix par argument de lancement
- **WHEN** l'application est lancée avec `--spring.profiles.active=remote`
- **THEN** le mode `remote` est actif et prime sur le fichier de propriétés

#### Scenario: Choix par fichier de propriétés
- **WHEN** `spring.profiles.active=onprem` figure dans le fichier de propriétés chargé
- **THEN** le mode `onprem` est actif en l'absence de variable ou d'argument

### Requirement: Alias des anciens profils
Les profils `local` et `prod` SHALL rester utilisables comme alias : `local` active le mode `dev` et `prod` active le mode `remote`.

#### Scenario: Ancien profil local
- **WHEN** `SPRING_PROFILES_ACTIVE=local` est défini
- **THEN** le comportement est celui du mode `dev`

#### Scenario: Ancien profil prod
- **WHEN** `SPRING_PROFILES_ACTIVE=prod` est défini
- **THEN** le comportement est celui du mode `remote`, y compris HTTPS et PostgreSQL

### Requirement: Documentation des modes dans le README
Le README SHALL contenir une section présentant les trois modes sous forme de tableau (cas d'usage, webcam, QR et smartphone, base, Docker, clé de kiosque), la commande de lancement de chaque mode (fichier de propriétés, variable d'environnement, argument), la liste des propriétés à valoriser par mode avec leur rôle, la procédure d'acceptation du certificat et la mise en garde sur le HTTP côté mobile.

#### Scenario: Tableau des modes
- **WHEN** on ouvre le README
- **THEN** le tableau des modes `dev`, `onprem` et `remote` est présent avec les colonnes décrites

#### Scenario: Propriétés par mode
- **WHEN** on consulte la section d'un mode
- **THEN** chaque propriété à valoriser est décrite (nom, variable d'environnement, valeur par défaut, rôle)

#### Scenario: Mise en garde HTTP
- **WHEN** on consulte la section du mode `remote`
- **THEN** le README indique que le mot de passe d'administration, les cookies et les jetons circulent en clair sur le port HTTP
