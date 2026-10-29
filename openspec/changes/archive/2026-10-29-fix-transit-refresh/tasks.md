## 1. Serveur

- [x] 1.1 Ajouter une constante `SCHEDULE_SLACK` (5 s) dans `TransitService` et l'appliquer aux gardes d'âge de `refreshIfDue` et `refreshLive` ainsi qu'à la garde `lastAttemptAt`

## 2. Navigateur

- [x] 2.1 Passer `REFRESH_MS` de `transit.js` à 60 secondes
- [x] 2.2 Mémoriser la signature `fetchedAt` + `notice` + `paused` de la dernière réponse rendue et ne rien faire si la nouvelle est identique
- [x] 2.3 Conserver l'affichage et l'heure précédents en cas d'échec de relecture (message d'erreur seulement s'il n'y a encore rien d'affiché)

## 3. Version, tests et documentation

- [x] 3.1 Passer `pom.xml` à la version 0.0.9
- [x] 3.2 Tests Java : un rafraîchissement à 5 s avant l'échéance a lieu, un appel plus précoce n'en émet pas, budget et pause toujours respectés
- [x] 3.3 Tests JS dans `transit-widget.test.mjs` : relecture à 60 s, aucun rendu si signature identique, rendu si l'horodatage ou le message change, affichage conservé en cas d'échec
- [x] 3.4 Lancer la suite complète (Maven : `C:\USINE_LOGICIELLE\apache-maven\bin` ; JS : `node.exe --test`)
- [x] 3.5 Mettre à jour `README.md` et `CHANGELOG.md` (section `[0.0.9]` avec « Version du projet portée à 0.0.9 »)
