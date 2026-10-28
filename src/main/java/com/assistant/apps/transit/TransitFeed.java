package com.assistant.apps.transit;

import java.time.Instant;
import java.util.List;

public record TransitFeed(List<TransitDisruption> disruptions, Instant fetchedAt) {
}
