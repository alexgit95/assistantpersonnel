package com.assistant.apps.transit.passages;

import java.time.Instant;
import java.util.List;

/** Passages triés par heure croissante, avec l'horodatage de la donnée. */
public record PassageFeed(List<Passage> passages, Instant updatedAt) {
}
