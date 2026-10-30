package com.assistant.apps.transit.passages;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class PassageResponseParser {

    public PassageFeed parse(JsonNode root, Instant fetchedAt) {
        JsonNode service = root == null ? null : root.path("Siri").path("ServiceDelivery");
        JsonNode deliveries = service == null ? null : service.get("StopMonitoringDelivery");
        if (deliveries == null || !deliveries.isArray()) {
            throw new PassageUnavailableException(false, null);
        }
        List<Passage> passages = new ArrayList<>();
        Instant updatedAt = instant(text(service, "ResponseTimestamp"));
        for (JsonNode delivery : deliveries) {
            Instant deliveryTime = instant(text(delivery, "ResponseTimestamp"));
            if (deliveryTime != null) {
                updatedAt = deliveryTime;
            }
            JsonNode visits = delivery.get("MonitoredStopVisit");
            if (visits == null || !visits.isArray()) {
                continue;
            }
            for (JsonNode visit : visits) {
                Passage passage = passage(visit.path("MonitoredVehicleJourney"));
                if (passage != null) {
                    passages.add(passage);
                }
            }
        }
        passages.sort(Comparator.comparing(Passage::expectedAt));
        return new PassageFeed(List.copyOf(passages), updatedAt == null ? fetchedAt : updatedAt);
    }

    private static Passage passage(JsonNode journey) {
        JsonNode call = journey.path("MonitoredCall");
        String time = text(call, "ExpectedArrivalTime");
        Instant expectedAt = instant(time);
        if (expectedAt == null) {
            expectedAt = instant(text(call, "ExpectedDepartureTime"));
        }
        if (expectedAt == null) {
            return null;
        }
        String destination = firstValue(journey.get("DestinationName"));
        if (destination == null) {
            destination = firstValue(call.get("DestinationDisplay"));
        }
        return new Passage(lineId(text(journey.path("LineRef"), "value")), destination, expectedAt,
                text(call, "DepartureStatus"));
    }

    /** « STIF:Line::C01154: » donne « C01154 ». */
    static String lineId(String lineRef) {
        if (lineRef == null) {
            return null;
        }
        String[] parts = lineRef.split(":");
        for (int index = parts.length - 1; index >= 0; index--) {
            if (!parts[index].isBlank()) {
                return parts[index];
            }
        }
        return null;
    }

    private static String firstValue(JsonNode array) {
        return array != null && array.isArray() && !array.isEmpty() ? text(array.get(0), "value") : null;
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            return null;
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String result = value.asString().strip();
        return result.isEmpty() ? null : result;
    }
}
