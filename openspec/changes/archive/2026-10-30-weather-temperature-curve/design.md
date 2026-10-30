## Context

`OpenMeteoClient` demande déjà `hourly=temperature_2m,…` sur 4 jours (`forecast_days=4`, `timezone=auto`), soit 96 valeurs horaires à partir de 0 h aujourd'hui. `WeatherResponseParser.parseDay` les parcourt pour calculer le matin (minimum 6–12 h) et l'après-midi (maximum 12–18 h), puis les jette. Le graphique du poids (`weight.js`) dessine un `<svg viewBox="0 0 640 320">` avec un `<polyline>`, des cercles et des étiquettes HTML positionnées en pourcentage, tracé par `stroke-dasharray`/`stroke-dashoffset` avec rejeu périodique. Le widget météo possède déjà un cycle périodique (`startRetypeLoop` appelant `loadForecast({ periodic: true })`).

## Goals / Non-Goals

**Goals:**
- Courbe des températures de maintenant − 3 h à maintenant + 24 h dans le bloc central.
- Même style et mêmes effets de tracé que le graphique du poids.
- Aucun appel externe supplémentaire.

**Non-Goals:**
- Obtenir les heures passées de la veille (`past_days`) : entre 0 h et 3 h, la courbe commence à 0 h.
- Étendre aux emplacements secondaires (mode compact).
- Factoriser le code de tracé avec le widget du poids (copie, poids inchangé).

## Decisions

- **Données exposées** : `WeatherForecast` reçoit `hourly`, liste de `HourlyTemperature(String time, Double temperatureC)` (heure locale ISO `yyyy-MM-ddTHH:00`, température éventuellement nulle) pour toute la période reçue, et `currentTime` (heure courante des données, `current.time`). Alternative écartée : ne renvoyer que la fenêtre déjà découpée, qui obligerait à recalculer côté serveur à chaque heure alors que la prévision est mise en cache 30 minutes. Les valeurs sont indexées par l'heure lue dans la chaîne ; une valeur absente est conservée comme `null` plutôt que de décaler la courbe.
- **Pas de changement de requête** : ni `past_days` (décalerait les tableaux `daily` et obligerait à adapter le parser et le fixture), ni `past_hours`/`forecast_hours` (prennent l'heure courante pour référence et casseraient le calcul du matin et de l'après-midi).
- **Fenêtre côté navigateur** : `weather.js` calcule l'heure de référence, trouve son index dans `hourly` et prend les index `[ref − 3, ref + 24]` bornés aux données disponibles. En mode réel, la référence est l'horloge du navigateur décalée de `utcOffsetSeconds` (même convention que les horodatages d'Open-Meteo) ; en mode TEST (`testMode` vrai), c'est `currentTime`, car le fixture est daté. La fenêtre est recalculée à chaque rendu, donc elle glisse avec l'heure.
- **Tracé** : copie de la logique du poids dans `weather.js` (signature des données pour ne pas retracer sans besoin, rejeu quand le cycle périodique l'exige, `stroke-dasharray` + animation CSS existante) avec son propre `<svg>` et ses classes `weather-chart*`, qui reprennent le style du poids. Alternative écartée : module partagé, qui obligerait à modifier le poids pour une partie commune réduite à l'animation.
- **Éléments du graphique** : un cercle de rayon 3 par heure ; cercles plus gros au minimum, au maximum et à « maintenant » ; étiquettes seulement à ces trois points (au-dessus pour le maximum et « maintenant », en dessous pour le minimum ; une seule étiquette si deux points coïncident) ; graduations d'axe aux heures multiples de 6 contenues dans la fenêtre. L'axe vertical affiche cinq valeurs régulièrement espacées, chacune avec le symbole °, et des guides horizontaux. Le domaine est d'abord arrondi selon un pas lisible (1, 2 ou 5 × puissance de 10), avec un écart minimal pour garder cinq repères même si les températures sont constantes.
- **Valeurs nulles** : un point nul n'est pas tracé et interrompt la ligne plutôt que de relier ses voisins.
- **Accessibilité et mouvement** : `aria-label` du graphique résumant minimum, maximum et valeur actuelle ; aucune animation avec `prefers-reduced-motion` ; courbe masquée en mode compact.
- **Version** : `pom.xml` passe à 0.0.10 et le changelog reçoit une section `[0.0.10]`.

## Risks / Trade-offs

- [Place limitée dans le bloc central] → la courbe occupe l'espace restant avec une hauteur plafonnée et un rapport 2:1 comme le poids ; elle est masquée sous un seuil de hauteur plutôt que d'écraser le contenu.
- [Entre 0 h et 3 h, moins de points à gauche] → comportement accepté et documenté ; la courbe reste lisible.
- [Prévision en cache jusqu'à 30 minutes] → les valeurs horaires ne changent qu'avec un nouveau flux ; le point « maintenant » suit l'horloge du navigateur et reste donc à l'heure.
- [Changement d'heure : jours de 23 ou 25 heures] → l'indexation par l'heure lue dans la chaîne évite tout décalage ; Open-Meteo utilise un décalage fixe sur toute la réponse.
- [Duplication du code de tracé] → limitée à quelques dizaines de lignes, au profit d'un widget du poids inchangé.
