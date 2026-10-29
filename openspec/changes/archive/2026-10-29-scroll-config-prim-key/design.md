## Context

- `home.css` est chargé par l'accueil et par `/configuration`. Il pose `overflow:hidden` et `height:100dvh` sur `html, body`, et `overflow:hidden` sur `.configuration-page`, ce qui bloque le défilement de la configuration. `/admin` charge seulement `theme.css` et `admin.css`.
- Le proxy sortant fournit un modèle : `ProxySetting` (id fixe 1), `ProxySettingsService`, formulaire dans `admin/index.html`, mot de passe en clair jamais réaffiché. Il est exclu de la sauvegarde via `EXCLUDED_FROM_BACKUP` dans `BackupService`.
- La clé PRIM vient aujourd'hui de `AssistantProperties.Prim.apiKey`, lue par `PrimClient` et testée par `TransitService` via `configured()`.
- `BackupService.validate` exige que l'ensemble des tables du fichier soit strictement égal à celui du schéma ; un ancien dump sans `prim_setting` serait rejeté.
- Contraintes : Spring Boot 4, Java 25, SQLite en local et PostgreSQL en production, `ddl-auto` sans migration, Raspberry Pi 4 Go.

## Goals / Non-Goals

**Goals:**
- Accueil non défilant ; `/configuration` et `/admin` défilants.
- Clé PRIM saisie, remplacée et supprimée depuis `/admin`, appliquée à chaud.
- Aucun appel PRIM et aucune consommation de quota sans clé.
- Clé PRIM incluse dans la sauvegarde ; anciens dumps toujours restaurables.

**Non-Goals:**
- Chiffrer la clé en base.
- Bouton « Tester la clé » (consommerait du quota PRIM).
- Modifier la CI ou le mode TEST.
- Déplacer le budget, le cache ou la pause PRIM hors des propriétés.

## Decisions

1. **Défilement : classe `home-body` sur l'accueil.** Les règles `height:100dvh; overflow:hidden` de `html, body` passent sous `html.home-page, body.home-body` (ou équivalent), posées dans `index.html`. `.configuration-page` perd `overflow:hidden` et `flex:1`, `.configuration-grid` est revue pour fonctionner sans hauteur imposée. Alternative écartée : séparer `home.css` et `configuration.css`, trop coûteux car la grille et les slots sont partagés.
2. **Retour à l'accueil.** Le bouton icône existant est conservé tel quel ; on vérifie seulement sa visibilité une fois la page défilante. Alternative écartée : bouton texte.
3. **Entité `PrimSetting` (id fixe 1, colonne `api_key` nullable).** Même schéma que `ProxySetting`. Colonne de type `String` simple, compatible SQLite et PostgreSQL. Alternative écartée : table clé/valeur générique, sur-ingénierie pour une valeur.
4. **`PrimKeyService`.** Méthodes `status()` (booléen configurée), `save(String)`, `clear()` et `currentKey()` (Optional). Lecture directe en base à chaque appel, sans cache : une lecture toutes les 10 minutes suffit et la restauration de sauvegarde est prise en compte sans invalidation.
5. **Garde sans clé.** `TransitService` remplace `properties.prim().configured()` par `PrimKeyService`, et la vérification a lieu avant toute réservation de quota. `PrimClient` lit la clé via le service et lève `TransitUnavailableException` si elle est absente. `AssistantProperties.Prim` perd `apiKey` et `configured()`.
6. **Suppression de la variable d'environnement.** Retrait de `assistant.prim.api-key` dans les `application*.properties`, de `ASSISTANT_PRIM_API_KEY` dans `docker-compose.yml` et le README, et ajustement des tests qui construisent `Prim`.
7. **Sauvegarde : `PrimSetting` n'est pas dans `EXCLUDED_FROM_BACKUP`.** L'export et la restauration la prennent en charge automatiquement via le metamodel JPA.
8. **Compatibilité des anciens dumps.** Dans `BackupService.validate`, les tables du schéma ajoutées après la version initiale (ici `prim_setting`) peuvent manquer du fichier ; elles sont traitées comme vides, donc la ligne existante est supprimée par la restauration. Toute autre différence reste une erreur. Alternative écartée : incrémenter `FORMAT_VERSION` et refuser les anciens fichiers, contraire à la décision.
9. **Formulaire admin.** Champ `password` (`autocomplete="new-password"`), bouton « Enregistrer la clé » et bouton « Supprimer la clé » (POST avec CSRF), endpoints `/admin/prim` et `/admin/prim/delete`, retour via `notice` comme les autres formulaires. Validation : longueur maximale et espaces retirés ; un champ vide conserve la clé existante.

## Risks / Trade-offs

- [Clé en clair en base et dans le dump] → Avertissement de confidentialité étendu, accès admin protégé, jamais réaffichée ni journalisée.
- [Aucun contrôle de validité de la clé à la saisie] → Pas d'appel de test pour préserver le quota ; une clé invalide se manifeste par l'indisponibilité du widget.
- [Régression de l'accueil en changeant le CSS global] → Vérification visuelle de l'accueil et du verrouillage, tests de gabarit existants.
- [Exception de compatibilité dans `validate` trop large] → Limiter la liste des tables optionnelles à `prim_setting` avec un test de rejet pour une table manquante quelconque.
- [Déploiement existant avec `ASSISTANT_PRIM_API_KEY`] → La variable est ignorée ; la clé doit être ressaisie dans `/admin` après mise à jour. À documenter dans le README et le CHANGELOG.

## Migration Plan

1. Déployer l'image : Hibernate crée `prim_setting` automatiquement.
2. Saisir la clé dans `/admin` ; le widget Transports reprend à l'actualisation suivante.
3. Retour arrière : redéployer l'ancienne image et remettre `ASSISTANT_PRIM_API_KEY` ; la table supplémentaire est ignorée.

## Open Questions

Aucune.
