## MODIFIED Requirements

### Requirement: Mode TEST des API externes
Le système SHALL permettre à l'administrateur d'activer un mode TEST persistant depuis l'administration. Dans ce mode, les appels de données météo MUST utiliser `docs/response-open-meteo.json` et les appels PRIM MUST utiliser `docs/response_info-traffic.json`. La recherche météo MUST toujours retourner Paris depuis le fixture, quel que soit le texte saisi, et les prévisions MUST rester cohérentes avec Paris. Aucun appel HTTP externe ni réservation de quota MUST être effectué pour ces réponses simulées. Les interfaces concernées MUST signaler clairement le mode TEST.

#### Scenario: Mode TEST météo activé
- **WHEN** l'administrateur active le mode TEST et qu'un widget météo cherche une ville ou demande une prévision
- **THEN** Paris et ses prévisions du fixture sont renvoyés sans appel Open-Meteo

#### Scenario: Mode TEST PRIM activé
- **WHEN** l'administrateur active le mode TEST et qu'un widget Transports demande des alertes
- **THEN** la réponse PRIM locale est utilisée sans appel réseau ni incrément du quota

#### Scenario: Référentiel et fixtures dans le JAR
- **WHEN** l'application est exécutée depuis l'image Docker
- **THEN** le catalogue et les deux fixtures météo/PRIM restent disponibles comme ressources de l'application

#### Scenario: Retour au mode réel
- **WHEN** l'administrateur désactive le mode TEST
- **THEN** les appels météo et PRIM utilisent leurs gateways réelles et les règles de quota et de pause s'appliquent
