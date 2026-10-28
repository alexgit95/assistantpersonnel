package com.assistant.apps.transit;

import java.io.IOException;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class LineCatalog {

    private final List<TransitLine> lines;
    private final Map<String, TransitLine> byId;

    public LineCatalog(ResourceLoader resources, ObjectMapper objectMapper, Clock clock) {
        Resource source = resources.getResource("classpath:integration/referentiel-des-lignes.json");
        try (var input = source.getInputStream()) {
            JsonNode root = objectMapper.readTree(input);
            LocalDate today = LocalDate.now(clock);
            lines = java.util.stream.StreamSupport.stream(root.spliterator(), false)
                    .filter(node -> "active".equalsIgnoreCase(text(node, "status")))
                    .filter(node -> dateNotExpired(node, today))
                    .map(LineCatalog::toLine)
                    .filter(line -> line.id() != null && !line.id().isBlank() && line.name() != null)
                    .distinct()
                    .sorted(Comparator.comparing(TransitLine::label, String.CASE_INSENSITIVE_ORDER)
                            .thenComparing(TransitLine::id))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Le référentiel local des lignes est introuvable.", exception);
        }
        byId = lines.stream().collect(Collectors.toUnmodifiableMap(TransitLine::id, Function.identity(),
                (first, ignored) -> first));
    }

    public List<TransitLine> search(String query) {
        String normalized = query == null ? "" : query.strip().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return lines;
        }
        return lines.stream().filter(line -> contains(line.label(), normalized)
            || contains(line.name(), normalized) || contains(line.shortName(), normalized)
            || contains(line.networkName(), normalized)
            || ("rail".equalsIgnoreCase(line.transportMode())
                && contains("Ligne " + line.shortName(), normalized)))
                .toList();
    }

    public Optional<TransitLine> find(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<TransitLine> favorites(List<String> ids) {
        return ids.stream().map(byId::get).filter(java.util.Objects::nonNull).toList();
    }

    public boolean isActive(String id) {
        return byId.containsKey(id);
    }

    private static boolean dateNotExpired(JsonNode node, LocalDate today) {
        String until = text(node, "valid_todate");
        return until == null || !LocalDate.parse(until).isBefore(today);
    }

    private static TransitLine toLine(JsonNode node) {
        String name = text(node, "name_line");
        String shortName = text(node, "shortname_line");
        return new TransitLine(text(node, "id_line"), name, shortName == null ? name : shortName,
                text(node, "transportmode"), text(node, "networkname"),
                text(node, "colourweb_hexa"), text(node, "textcolourweb_hexa"));
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String result = value.asString().strip();
        return result.isEmpty() ? null : result;
    }

    private static boolean contains(String value, String normalized) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(normalized);
    }
}
