## 1. Dépendance et modèle de données

- [x] 1.1 Ajouter la dépendance Rome au `pom.xml` (version figée) et vérifier le build
- [x] 1.2 Créer l'entité `NewsCategory` (id, name, feedUrl, position) et `NewsCategoryRepository` (tri par position), compatibles SQLite et PostgreSQL
- [x] 1.3 Mettre à jour `docs/database.puml`

## 2. Récupération des flux

- [x] 2.1 Implémenter le validateur d'URL (https uniquement, refus loopback/privée/link-local/multicast/non spécifiée après résolution DNS)
- [x] 2.2 Implémenter `RssClient` : appel via `OutboundHttp`, sans redirection, timeouts, taille max 1 Mo, parsing Rome sans DOCTYPE
- [x] 2.3 Convertir titre et description en texte brut, tronquer, limiter à 10 entrées
- [x] 2.4 Implémenter `NewsGateway` réelle et mock (mode test) choisie selon `ExternalApiModeService`
- [x] 2.5 Implémenter `NewsService` : cache mémoire 10 min par catégorie, un seul fetch concurrent, version périmée servie avec notice en cas d'échec, invalidation à la modification/suppression

## 3. API et widget côté serveur

- [x] 3.1 Créer `NewsAssistantApp` (clé `news`, fragment, module JS)
- [x] 3.2 Créer `NewsController` : liste des catégories, lecture/enregistrement de la sélection du profil (refus d'un id inexistant), contenu des catégories choisies ; annotations Swagger
- [x] 3.3 Ajouter la gestion admin des catégories (CRUD, position, validation d'URL) dans `AdminController` ; annotations Swagger
- [x] 3.4 Vérifier l'export de `news_category` et la restauration d'un ancien fichier sans cette table dans `BackupService`

## 4. Interface

- [x] 4.1 Créer le fragment `fragments/widgets/news.html`
- [x] 4.2 Créer `widgets/news.js` : rotation 15 s, flèches bouclantes, entrées dépliables, repli auto à 20 s, suspension tant qu'une entrée est dépliée, machine à écrire sur les titres, `textContent` uniquement
- [x] 4.3 Ajouter les appels à `api.js` et la configuration par cases à cocher dans `configuration.js`
- [x] 4.4 Ajouter la gestion des catégories (liste, formulaire, position, avertissement de suppression) dans `admin/index.html` et `admin.js`
- [x] 4.5 Ajouter les styles dans `home.css` et `admin.css`

## 5. Tests et documentation

- [x] 5.1 Tests JUnit : validateur d'URL, `RssClient`/parsing (RSS, Atom, DOCTYPE, HTML, taille), `NewsService` (cache, échec, 10 entrées), contrôleurs (sélection, admin, 401/403), restauration d'ancien fichier
- [x] 5.2 Tests JS : rotation, flèches, repli, suspension, une seule catégorie
- [x] 5.3 Lancer la suite complète avec Maven (`C:\USINE_LOGICIELLE\apache-maven\bin`)
- [x] 5.4 Mettre à jour `README.md` et `CHANGELOG.md`
