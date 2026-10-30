## 1. Logique serveur

- [x] 1.1 Remplacer `MAX_PASSAGES` par les constantes 6 / 4 / seuil 2 dans `NextPassagesService` et calculer la limite par profil dans `next()` à partir du nombre d'arrêts enregistrés
- [x] 1.2 Passer la limite à `view` (troncature) et à `testEntry` (seuil de régénération du cache simulé)
- [x] 1.3 Passer `MockPassageGateway.VISITS` de 5 à 7
- [x] 1.4 Mettre à jour le libellé Swagger de `TransitPassagesController` (« 4 à 6 prochains passages selon le nombre d'arrêts »)

## 2. Tests

- [x] 2.1 `NextPassagesServiceTest` : 1 arrêt → 6, 2 arrêts → 6, 3 arrêts → 4, arrêt indisponible compté, cache TEST non régénéré à chaque appel
- [x] 2.2 Adapter `MockPassageGatewayTest` au nouveau nombre de visites
- [x] 2.3 Exécuter la suite Maven (`C:\USINE_LOGICIELLE\apache-maven\bin\mvn test`)

## 3. Documentation et version

- [x] 3.1 Mettre à jour `README.md` (widget Prochains passages : 6 passages avec 1 ou 2 arrêts, 4 avec 3)
- [x] 3.2 Passer la version du `pom.xml` de 0.0.12 à 0.0.13
- [x] 3.3 Ajouter la section `[0.0.13]` (Changed) dans `CHANGELOG.md`
