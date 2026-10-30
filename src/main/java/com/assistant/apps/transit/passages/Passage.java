package com.assistant.apps.transit.passages;

import java.time.Instant;

/** Passage prévu à un arrêt ; {@code lineId} est l'identifiant CodifLigne (ex. C01154). */
public record Passage(String lineId, String destination, Instant expectedAt, String status) {
}
