## Context

Le widget « Prochains passages » (`transit-passages`) reçoit de `GET /api/transit-passages/next` une liste d'arrêts ; pour chacun, `NextPassagesService.view` garde les 6 (1 ou 2 arrêts) ou 4 (3 arrêts) passages futurs les plus proches, toutes lignes confondues, triés par heure. Le navigateur (`transit-passages.js`) les affiche tels quels, avec un horodatage complet par arrêt.

Deux contraintes pèsent sur la conception :

- Le kiosque n'a ni souris ni clavier, et `.app-widget-content` ainsi que les emplacements sont en `overflow: hidden` : tout contenu qui dépasse est coupé sans défilement possible. Le volume affiché doit donc être borné.
- Le flux PRIM de l'arrêt contient tous les passages à venir et `PassageFeed` les conserve triés par heure ; un plafond appliqué sur les passages masque une ligne rare derrière une ligne fréquente.

## Goals / Non-Goals

**Goals:**
- Afficher, par arrêt, des blocs de lignes : 3 passages consécutifs d'une ligne, puis ceux de la ligne suivante, sans entrelacement, tous modes confondus.
- Trier les blocs par leur premier passage et les passages croissants dans un bloc.
- Borner le nombre de lignes par arrêt selon le nombre d'arrêts enregistrés (4, 2, 1).
- Réduire chaque rangée à pastille de ligne, destination et minutes ; afficher une seule heure de mise à jour (`HH:MM`).
- Augmenter l'espacement vertical entre les rangées.

**Non-Goals:**
- Séparer les modes de transport (trains puis bus) : l'ordre reste celui du premier passage.
- Modifier le cache (55 s), le budget PRIM, la pause nocturne, la recherche d'arrêts ou la configuration des arrêts.
- Changer le comportement des autres widgets (Transports, Météo, etc.).
- Régler les plafonds de lignes d'après la hauteur réelle de chaque emplacement : la valeur de départ est une constante, ajustée après un essai à l'écran.

## Decisions

**1. Le regroupement se fait côté serveur, avant le plafond.**
`NextPassagesService.view` filtre les passages futurs, les regroupe par `lineId` en conservant l'ordre de première apparition (le flux est déjà trié par heure, donc les groupes sortent triés par premier passage), garde 3 passages par groupe puis les N premiers groupes. *Alternative écartée* : regrouper dans `transit-passages.js` à partir des 6 ou 4 passages actuels. Elle ne résout pas le masquage d'une ligne rare et dupliquerait la règle de plafonnement côté navigateur.

**2. `StopPassages.passages` reste une liste plate, déjà ordonnée par blocs.**
Le navigateur affiche les rangées dans l'ordre reçu, sans regrouper ni trier. La forme de la réponse reste compatible (même liste de `PassageView`) ; seuls le contenu, l'ordre et le nombre de passages changent. *Alternative écartée* : renvoyer une liste de groupes (`lines: [{ line, passages }]`). Elle aurait été plus expressive, mais elle change le contrat sans bénéfice puisque la pastille est répétée à chaque rangée et qu'aucun en-tête de bloc n'est affiché.

**3. Clé de regroupement : la ligne (`lineId`).**
Les passages d'une même ligne forment un bloc quelle que soit leur destination ; la destination est affichée à chaque rangée. Les passages sans `lineId` sont regroupés ensemble, avec un libellé vide comme aujourd'hui.

**4. Plafond exprimé en lignes par arrêt, selon le nombre d'arrêts enregistrés.**
Constantes dans `NextPassagesService` : 3 passages par ligne ; 4 lignes avec 1 arrêt, 2 avec 2 arrêts, 1 avec 3 arrêts. Ces constantes remplacent `PASSAGES_FEW_STOPS`, `PASSAGES_MANY_STOPS` et `FEW_STOPS_MAX`. Le nombre d'arrêts compte les arrêts indisponibles, comme la règle actuelle. Les valeurs visent environ 12 rangées de passages partagées entre les arrêts ; elles seront validées à l'écran et ajustées sans changement de structure.

**5. Une seule heure de mise à jour, calculée par le serveur.**
`Overview` reçoit un champ `updatedAt` : la plus ancienne des heures `updatedAt` des arrêts qui ont des données. Le navigateur l'affiche une fois en bas du widget (`Mis à jour HH:MM`, fuseau Europe/Paris, sans date) et n'affiche plus l'heure par arrêt. Le champ `updatedAt` de chaque `StopPassages` est conservé dans la réponse pour compatibilité. Le choix de la plus ancienne est prudent : l'heure affichée ne promet pas de fraîcheur qu'un arrêt n'a pas.

**6. Espacement par CSS uniquement.**
Un `gap` vertical sur `.passages-list`, entre sections d'arrêt et entre rangées ; aucune logique JavaScript. Les emplacements secondaires conservent leurs tailles de police réduites existantes.

**7. Le mode TEST doit produire plusieurs lignes à au moins 3 passages.**
`MockPassageGateway` rejoue les visites du fichier de test en cycle (`VISITS = 7`). Il faut garantir au moins quatre lignes distinctes, chacune avec au moins 3 passages futurs, pour que les plafonds de 4, 2 et 1 lignes soient observables. Le critère de conservation des horaires simulés dans `NextPassagesService.testEntry` (`futurePassages(...).size() < perStop`) doit être recalculé sur ce nouveau besoin, sinon des horaires périmés seraient conservés ou régénérés trop souvent.

**8. La pastille affiche l'identifiant court de la ligne.**
`NextPassagesService.toView` renseigne `PassageView.line` avec le nom court du catalogue (`TransitLine.shortName`, issu de `shortname_line` : la lettre d'un train, le numéro d'un bus) au lieu du libellé composé `networkName + shortName` (« Bus 172 »). Si la ligne est absente du catalogue, le repli est l'identifiant de ligne (`lineId`), comme aujourd'hui. Le catalogue donne déjà le nom complet comme nom court quand `shortname_line` est absent, ce qui fournit un repli sans code supplémentaire. *Alternative écartée* : retirer le préfixe côté navigateur par une transformation de texte ; fragile (« Bus », « RER », « Métro », noms de réseau variables) et il vaut mieux utiliser la donnée structurée. Le widget Transports continue d'utiliser le libellé complet : seul le widget Prochains passages change.

## Risks / Trade-offs

- [Contenu coupé sur un petit emplacement, sans défilement] → plafond de lignes par arrêt ; constantes réglables ; vérification visuelle sur l'emplacement principal et sur un emplacement secondaire avant de figer les valeurs.
- [Avec 3 arrêts, une seule ligne par arrêt est peu] → choix assumé ; la constante peut passer à 2 lignes de 2 passages si l'usage le justifie, sans changer la structure.
- [Deux lignes de réseaux ou de modes différents peuvent avoir le même identifiant court (par exemple un bus 172 et une autre ligne 172)] → le regroupement se fait sur `lineId`, pas sur l'identifiant court ; les deux blocs restent distincts même si leurs pastilles se ressemblent ; la destination les distingue.
- [Le nombre de rangées par arrêt n'est plus constant (une ligne n'a parfois qu'un passage)] → la hauteur varie ; le plafond est calculé sur le cas le plus défavorable (3 passages par ligne).
- [Regrouper par `lineId` fusionne deux terminus d'une même ligne dans un bloc] → la destination reste affichée à chaque rangée ; ordre par heure à l'intérieur du bloc.
- [Les tests existants qui indexent les enfants du DOM ou comptent 6 et 4 passages échoueront] → à adapter dans `NextPassagesServiceTest`, `TransitPassagesControllerTest` et `transit-passages-widget.test.mjs`.

## Open Questions

- Les plafonds de lignes (4, 2, 1) sont-ils adaptés à la hauteur réelle des emplacements ? À trancher après essai à l'écran.
