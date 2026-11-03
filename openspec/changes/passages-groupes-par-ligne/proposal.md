## Why

Le widget « Prochains passages » affiche aujourd'hui, par arrêt, les 6 (ou 4) passages les plus proches toutes lignes confondues, triés par heure : les lignes sont entremêlées et une ligne rare est masquée par une ligne fréquente. Le kiosque n'ayant ni souris ni clavier, tout contenu qui dépasse l'emplacement est coupé sans défilement possible ; il faut donc un affichage plus lisible, plus aéré et dont le volume est borné.

## What Changes

- Les passages d'un arrêt sont regroupés par ligne : les 3 prochains passages d'une ligne sont affichés les uns après les autres, puis ceux de la ligne suivante, sans jamais entrelacer les lignes (tous modes de transport confondus).
- Les blocs de lignes sont triés par leur premier passage (du plus court au plus long) ; à l'intérieur d'un bloc, les passages sont croissants.
- Chaque rangée affiche uniquement la pastille de ligne, la destination et le temps restant ; la pastille est répétée à chaque rangée, sans en-tête de bloc, et ne montre que l'identifiant court de la ligne (la lettre du train ou le numéro du bus), sans préfixe de mode ni de réseau (plus de « Bus 172 »).
- Une section par arrêt (nom de l'arrêt en titre) est conservée ; une ligne qui a moins de 3 passages est affichée telle quelle, sans complément.
- Le plafond actuel de 6 passages (1 ou 2 arrêts) ou 4 passages (3 arrêts) par arrêt est remplacé par un plafond de lignes par arrêt : 4 lignes avec 1 arrêt, 2 lignes avec 2 arrêts, 1 ligne avec 3 arrêts, chacune avec 3 passages.
- Une seule heure de mise à jour est affichée en bas du widget, au format `HH:MM` sans date : la plus ancienne des heures des arrêts affichés.
- L'espacement vertical entre les rangées est augmenté.
- La réponse de `GET /api/transit-passages/next` conserve sa forme (liste de passages par arrêt, désormais déjà ordonnée par blocs de lignes) et gagne un champ `updatedAt` au niveau de la vue d'ensemble ; le nombre de passages par arrêt n'est plus plafonné à 6 ou 4.
- Le fichier de test `docs/reponse-prochain-passage.json` est enrichi pour couvrir plusieurs lignes à plus de 3 passages chacune.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities

- `transit-next-passages` : l'exigence « Affichage des prochains passages » change (regroupement par ligne, 3 passages par ligne, tri des blocs, plafond de lignes par arrêt selon le nombre d'arrêts, rangée réduite à ligne, destination et minutes, heure de mise à jour unique) ; l'exigence du mode TEST est précisée pour les passages simulés sur plusieurs lignes.

## Impact

- Code serveur : `NextPassagesService` (regroupement, plafonds, heure de mise à jour, libellé court de la ligne), `Overview` (champ `updatedAt`), `TransitPassagesController` (documentation Swagger de la réponse).
- Code navigateur : `widgets/transit-passages.js`, `fragments/widgets/transit-passages.html`, `home.css` (rangées, espacement, section par arrêt).
- API : `GET /api/transit-passages/next` (contenu et ordre des passages modifiés, champ `updatedAt` ajouté).
- Données de test : `docs/reponse-prochain-passage.json`.
- Tests : `NextPassagesServiceTest`, `TransitPassagesControllerTest`, `MockPassageGatewayTest`, `transit-passages-widget.test.mjs`.
- Documentation : `README.md`, `CHANGELOG.md`. Aucun impact sur la base de données ni sur `docs/database.puml`.
