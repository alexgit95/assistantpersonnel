## Context

`TransitService.refreshIfDue` et `refreshLive` ne rafraîchissent que si l'âge du flux (`fetchedAt`) atteint `refreshInterval` (10 minutes) ; `refreshLive` applique aussi une garde sur `lastAttemptAt`. `@Scheduled` s'exécute avec un taux fixe de 10 minutes. `fetchedAt` étant posé après la réponse PRIM, le tick suivant arrive quelques centaines de millisecondes trop tôt et est ignoré. Côté navigateur, `transit.js` appelle `/api/transit/alerts` au chargement puis toutes les 10 minutes, soit la durée de la session faciale glissante : le widget est souvent reconstruit avant le deuxième appel, et chaque reconstruction relit un cache du même âge.

## Goals / Non-Goals

**Goals:**
- Que l'heure affichée suive celle du serveur à la minute près.
- Garantir un rafraîchissement PRIM toutes les 10 minutes, même sans navigateur connecté.
- Ne pas rejouer l'animation d'écriture quand rien n'a changé.

**Non-Goals:**
- Changer la fréquence des appels PRIM, le budget journalier ou la pause nocturne.
- Modifier le mode TEST ou l'API `/api/transit/alerts`.
- Afficher une heure de dernière vérification distincte de l'heure d'actualisation PRIM.

## Decisions

- **Relecture toutes les 60 s** (`REFRESH_MS = 60_000`) : le serveur sert son cache, donc aucun appel PRIM de plus. Le premier appel après l'expiration du TTL déclenche l'actualisation à la demande, ce qui maintient l'écart réel entre deux appels PRIM à environ 10 à 11 minutes. Alternative écartée : pousser les mises à jour (SSE/WebSocket), disproportionné pour une relecture par minute.
- **Rendu seulement si la donnée change** : le widget mémorise la signature `fetchedAt` + `notice` + `paused` de la dernière réponse rendue. Si la nouvelle signature est identique, il ne fait rien. Sinon il re-rend l'ensemble des lignes (au plus toutes les 10 minutes en pratique). Cela évite de rejouer l'animation chaque minute en plus des réécritures aléatoires de 15 à 60 s.
- **Échec de relecture** : l'affichage et l'heure précédents sont conservés ; seule la ligne d'état affiche un message d'erreur s'il n'y a encore rien d'affiché. Alternative écartée : effacer l'heure, qui laisserait le widget vide à la moindre coupure.
- **Marge sur la garde d'âge** : une constante `SCHEDULE_SLACK` de 5 secondes est retranchée de `refreshInterval` pour les trois gardes (âge dans `refreshIfDue`, âge et `lastAttemptAt` dans `refreshLive`). Un tick planifié ou une requête arrivant au plus 5 secondes avant l'échéance rafraîchit donc, ce qui supprime les tours ignorés. L'écart entre deux appels PRIM reste d'au moins 10 minutes moins 5 secondes, et le budget journalier (750 par défaut pour 144 appels de base) n'est pas menacé. Alternative écartée : un tick toutes les minutes qui teste l'échéance, plus de code pour le même effet.
- **Version** : `pom.xml` passe à 0.0.9 et le changelog reçoit une section `[0.0.9]`.

## Risks / Trade-offs

- [Charge serveur : une requête par minute et par widget ouvert] → la réponse vient du cache mémoire, sans base ni appel externe ; coût négligeable sur un Raspberry Pi pour un usage domestique.
- [La marge de 5 s fait descendre l'écart minimal entre appels PRIM à 9 min 55] → négligeable devant le budget ; une constante nommée la rend explicite et testable.
- [Une signature identique masque un changement de contenu sans changement de `fetchedAt`] → le contenu ne change qu'avec un nouveau flux PRIM, qui change `fetchedAt` ; l'activité des alertes dépend de l'heure (`activeAt`) mais reste recalculée au prochain flux, comportement inchangé.
- [Onglet masqué : les minuteurs sont ralentis par le navigateur] → sans effet fonctionnel, la relecture reprend au retour.
