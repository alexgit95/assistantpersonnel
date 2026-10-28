package com.assistant.apps.transit;

import java.time.Instant;
import java.util.List;
import java.util.Set;

record TransitDisruption(String id, String title, String shortMessage, String message, String severity, String cause,
                         Set<String> lineIds, boolean hasImpactedSections, List<Period> periods) {

    boolean activeAt(Instant instant) {
        if (periods.isEmpty()) {
            return true;
        }
        return periods.stream().anyMatch(period -> !instant.isBefore(period.begin()) && instant.isBefore(period.end()));
    }

    record Period(Instant begin, Instant end) {
    }
}
