package com.assistant.apps.transit;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class TransitResponseParser {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");
    private static final DateTimeFormatter PRIM_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");

    public TransitFeed parse(JsonNode root, Instant fetchedAt) {
        JsonNode source = root == null ? null : root.get("disruptions");
        if (source == null || !source.isArray()) {
            throw new TransitUnavailableException(null);
        }
        List<TransitDisruption> disruptions = new ArrayList<>(source.size());
        for (JsonNode node : source) {
            Set<String> lineIds = new HashSet<>();
            JsonNode sections = node.get("impactedSections");
            boolean hasImpactedSections = sections != null && sections.isArray() && !sections.isEmpty();
            if (sections != null && sections.isArray()) {
                for (JsonNode section : sections) {
                    String lineId = text(section, "lineId");
                    if (lineId != null) {
                        lineIds.add(lineId);
                    }
                }
            }
            disruptions.add(new TransitDisruption(text(node, "id"), text(node, "title"),
                    text(node, "shortMessage"), text(node, "message"), text(node, "severity"), text(node, "cause"),
                    Set.copyOf(lineIds), hasImpactedSections, periods(node.get("applicationPeriods"))));
        }
        return new TransitFeed(List.copyOf(disruptions), fetchedAt);
    }

    private static List<TransitDisruption.Period> periods(JsonNode source) {
        if (source == null || !source.isArray()) {
            return List.of();
        }
        List<TransitDisruption.Period> periods = new ArrayList<>();
        for (JsonNode period : source) {
            Instant begin = instant(text(period, "begin"));
            Instant end = instant(text(period, "end"));
            if (begin != null && end != null && end.isAfter(begin)) {
                periods.add(new TransitDisruption.Period(begin, end));
            }
        }
        return List.copyOf(periods);
    }

    private static Instant instant(String value) {
        if (value == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, PRIM_DATE).atZone(PARIS).toInstant();
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
