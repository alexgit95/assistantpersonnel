## Context

Les widgets suivent le contrat `AssistantApp` (fragment Thymeleaf + module JS) et le widget Transports fournit le modèle : appel externe via `OutboundHttp` (proxy), mode test (`ExternalApiModeService`), configuration par profil dans `configJson`, effet machine à écrire (`typeText`, `startRetypeLoop`) et dépliants `<details>`. La base utilise `ddl-auto` (SQLite en local, PostgreSQL en production), sans outil de migration. Cible : Raspberry Pi 4 (4 Go), mémoire à ménager.

## Goals / Non-Goals

**Goals:**
- Catégories gérées par l'admin (nom, URL RSS, ordre), sélection par profil, rotation et navigation par flèches.
- Récupération sûre et économe des flux (HTTPS, anti-SSRF, cache, plafonds).
- Rendu texte seul, cohérent avec le style machine à écrire.

**Non-Goals:**
- Images, liens cliquables ou HTML des flux.
- Pagination ou archivage des articles, lecture hors ligne.
- Authentification sur les flux, flux non HTTPS.
- Suspension de la rotation quand l'onglet est caché.

## Decisions

- **Entité `NewsCategory`** (`id`, `name`, `feedUrl`, `position`) dans `com.assistant.news`. Annotations JPA standard, compatibles SQLite et PostgreSQL. Alternative écartée : stocker les catégories dans une table de réglages JSON, moins simple à ordonner et à sauvegarder. Le backup générique les inclut sans changement de code ; seule la restauration d'anciens fichiers sans cette table doit être tolérée.
- **Configuration profil** : `configJson` contient `categoryIds` (liste d'ids). Les ids inconnus sont ignorés à l'affichage, ce qui évite toute migration à la suppression d'une catégorie. L'ordre de rotation est celui de `position`, pas celui de la sélection.
- **Rome** pour le parsing (RSS 2.0 et Atom). Le téléchargement est fait par `OutboundHttp` ; les octets lus sont passés à `SyndFeedInput` avec DOCTYPE désactivés (anti-XXE). Alternative écartée : parser JDK maison, moins robuste (encodages, Atom).
- **Anti-SSRF** : URL `https` uniquement, validée à la saisie et à chaque récupération. À chaque fetch : résolution DNS, refus si l'adresse est loopback, privée, link-local, multicast ou non spécifiée ; redirections désactivées. Derrière un proxy, la vérification locale est « au mieux ».
- **Plafonds** : taille de réponse limitée (1 Mo), timeouts de connexion et de lecture, 10 entrées maximum par catégorie, description tronquée.
- **Rendu texte** : le serveur renvoie titre et description convertis en texte brut (balises supprimées, entités décodées) ; le client utilise `textContent` uniquement, comme `providerMessageText` pour Transports.
- **Cache** : mémoire, TTL de 10 minutes par catégorie, un seul fetch concurrent par catégorie ; en cas d'échec, la dernière version connue est servie avec un indicateur d'indisponibilité. Entrée supprimée à la suppression ou à la modification de l'URL.
- **Mode test** : passerelle factice (`NewsGateway` réelle/mock, sur le modèle `TransitGateway`) choisie selon `ExternalApiModeService`, avec un badge « MODE TEST ».
- **Widget JS** (`widgets/news.js`) : machine à états : catégorie courante, minuteur de rotation de 15 s remis à zéro à chaque clic (flèche ou entrée), minuteur de repli de 20 s à l'ouverture d'une entrée, rotation suspendue tant qu'une entrée est dépliée ; au repli (manuel ou automatique), le compteur de 15 s repart de zéro. Une seule catégorie : ni rotation ni flèches. Les flèches bouclent. L'effet machine à écrire ne s'applique qu'aux titres ; la description s'affiche directement à l'ouverture. Les minuteurs sont arrêtés quand le widget est déconnecté du DOM.
- **Admin** : CRUD sur la page d'administration avec champ de position ; l'avertissement de suppression indique que des profils peuvent avoir coché la catégorie, mais la suppression reste possible. Endpoints sous `/admin`, protégés par la session admin, documentés Swagger.
- **API profil** : un endpoint liste les catégories (id, nom) pour la configuration ; un autre renvoie le contenu des catégories choisies par le profil authentifié (`@RequireSession`).

## Risks / Trade-offs

- [SSRF via redirection ou DNS changeant] → redirections désactivées et contrôle de l'IP à chaque fetch.
- [XXE ou flux malveillant] → DOCTYPE désactivés, taille et timeout plafonnés.
- [Charge mémoire sur le Pi] → cache borné (10 entrées par catégorie, descriptions tronquées), pas de collection massive.
- [Flux lent ou indisponible] → cache périmé servi avec notice, timeouts courts, aucune exception remontée à l'utilisateur.
- [Dépendance Rome/JDOM] → à vérifier dans l'image Docker ; version figée.
- [Vérification IP incomplète derrière un proxy d'entreprise] → documentée ; contrôle effectué sur l'adresse résolue localement.
- [Catégorie supprimée encore cochée] → ids ignorés, aucun effet bloquant.
