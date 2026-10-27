# Instructions Générales du Projet

## Contexte d'Entreprise et Environnement
- **Cadre Professionnel :** Ce projet est développé dans un cadre d'entreprise strict. Le respect de l'architecture en place et la non-régression des configurations de déploiement sont critiques.
- **Environnement de Développement :** Le projet est conçu pour être exécuté dans un environnement de développement local léger, sans dépendance obligatoire à Docker ou Kubernetes pour les développeurs.

## Contexte de l'Architecture Technique
- **Backend :** Java Spring Boot (Utiliser la version stable la plus récente, actuellement **Spring Boot 4.x.x**)
- **Conteneurisation :** Docker

- **Bases de données :**
  - **Production / Dev distant :** PostgreSQL
  - **Local (`local` profile) :** SQLite (pour un développement léger sans dépendance docker externe obligatoire)

##  Workflow CI/CD et Spécificités du Déploiement Cible (Raspberry Pi 4)

* **Cycle de Vie et Déploiement :** 

  * Le développement et les tests initiaux se font en local avec le profil local branché sur **SQLite**.
  * Le déploiement s'effectue exclusivement de manière automatisée après chaque commit/push sur **GitHub**.
  * Une **GitHub Action** est déjà configurée et active dans le projet (consulte le dossier .github/workflows/ pour en comprendre les étapes de build et de validation). Ne propose aucune modification des pipelines CI/CD sans demande explicite.
* **Architecture de Production (Raspberry Pi 4) :** 

  * L'application finale est packagée dans une **image Docker** puis déployée et orchestrée via Arcane et donc un Docker compose sur 
  **Raspberry Pi 4 (modèle 4 Go de RAM)**.
  * La base de données en production (et dev distant) bascule automatiquement sur **PostgreSQL** via un conteneur docker aussi.
* **Directives pour l'IA (Prise de décision & Propositions) :** 

  * **Gestion de la Base de Données (Sans outil de migration) :** Le projet n'utilise aucun outil de migration de base de données (pas de Liquibase, pas de Flyway). La création et la mise à jour des tables reposent sur la stratégie de génération automatique d'Hibernate (ddl-auto). Assure-toi que les définitions d'entités @Entity et leurs annotations de mapping restent 100% compatibles et transparentes à la fois pour le pilote **SQLite** en local et le pilote **PostgreSQL** en production.
  * **Optimisation stricte des ressources (4 Go RAM) :** Le Raspberry Pi 4 partage ses 4 Go de RAM entre le système d'exploitation, l'environnement Kubernetes et les conteneurs. Tes propositions de code et de configuration de la JVM (Java 25) doivent être ultra-sobre. Privilégie un garbage collector adapté aux faibles empreintes (comme Serial GC si nécessaire), évite absolument le chargement de collections massives en mémoire (utilise la pagination JPA) et bannis les fuites de mémoire potentielles.

## Règles de Codage et Automatisation

### 1. Gestion des Versions et Stack Backend
- Initialise et maintiens le projet avec **Spring Boot 4.x.x** (compatible avec Java 25+).
- Utilise la dépendance `springdoc-openapi-starter-webmvc-ui` pour exposer la documentation.

### 2. Documentation Swagger obligatoire sur les APIs
- À chaque création ou modification de contrôleur REST, ajoute obligatoirement les annotations OpenAPI 3 / Swagger (`@Tag`, `@Operation`, `@ApiResponse`).
- Veille à ce que chaque endpoint expose une description claire des paramètres d'entrée, des codes de statut HTTP de retour (200, 201, 400, 404, 500) et des formats de réponses DTO.

### 3. Gestion Multi-Base & Modélisation (PostgreSQL & SQLite)
- Structure les fichiers de configuration de manière à isoler les environnements via les profils Spring :
  - `application-local.properties` (ou `.yml`) : Configurer le dialecte, le pilote JDBC et la stratégie JPA pour **SQLite**.
  - `application-prod.properties` (ou `.yml`) : Configurer l'accès à la base **PostgreSQL**.
- Lorsque tu génères du code d'entité JPA ou des scripts de migration, écris du SQL ou des annotations standard compatibles avec les deux bases de données.
- **📊 RÈGLE DE MODÉLISATION (PlantUML) :** À chaque modification, ajout ou suppression impactant la structure de la base de données (entités JPA, tables, colonnes, relations, contraintes), tu dois obligatoirement générer ou mettre à jour le fichier de modélisation de la base de données au format **PlantUML** (ex: `docs/database.puml`).

### 4. Tests Unitaires Obligatoires & Exécution Maven
- À **chaque modification ou ajout** de code source Java, implémente ou mets à jour les tests unitaires correspondants (JUnit 5 + Mockito).
- Assure une couverture complète pour chaque nouvelle règle métier introduite.
- **🛠️ Environnement d'exécution :** Pour lancer, compiler et valider les tests, tu dois utiliser exclusivement le chemin ou le binaire Maven spécifié ici : **`C:\USINE_LOGICIELLE\apache-maven\bin`** (ex: exécuter les commandes via ce wrapper ou chemin précis).

### 5. Documentation Fonctionnelle (README.md)
- À **chaque modification** de fonctionnalité ou ajout de comportement, alimente immédiatement la documentation fonctionnelle dans le fichier `README.md` principal situé à la racine.
- Rédige de manière claire et structurée.

### 6. Journal des Modifications (CHANGELOG.md)
- À **chaque tâche de code terminée**, génère ou mets à jour le fichier `CHANGELOG.md` à la racine du projet.
- Utilise le format Markdown standard [Keep a Changelog] avec les sections : `[Added]`, `[Changed]`, `[Fixed]`.
- Décris brièvement l'impact technique ET fonctionnel du changement.
