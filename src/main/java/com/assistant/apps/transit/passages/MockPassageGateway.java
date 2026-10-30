package com.assistant.apps.transit.passages;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/** Rejoue docs/reponse-prochain-passage.json avec des horaires aléatoires dans le futur. */
@Component
public class MockPassageGateway implements PassageGateway {

    static final int VISITS = 5;

    private final Resource fixture;
    private final PassageResponseParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RandomGenerator random;
    private volatile JsonNode template;

    @Autowired
    public MockPassageGateway(ResourceLoader resources, PassageResponseParser parser, ObjectMapper objectMapper,
                              Clock clock) {
        this(resources, parser, objectMapper, clock, new java.util.Random());
    }

    MockPassageGateway(ResourceLoader resources, PassageResponseParser parser, ObjectMapper objectMapper,
                       Clock clock, RandomGenerator random) {
        this.fixture = resources.getResource("classpath:integration/reponse-prochain-passage.json");
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.random = random;
    }

    @Override
    public PassageFeed fetch(String arrid) {
        Instant now = clock.instant();
        JsonNode root = template().deepCopy();
        ObjectNode service = (ObjectNode) root.path("Siri").path("ServiceDelivery");
        service.put("ResponseTimestamp", DateTimeFormatter.ISO_INSTANT.format(now));
        ObjectNode delivery = (ObjectNode) service.path("StopMonitoringDelivery").get(0);
        delivery.put("ResponseTimestamp", DateTimeFormatter.ISO_INSTANT.format(now));
        ArrayNode sources = (ArrayNode) delivery.get("MonitoredStopVisit");
        ArrayNode visits = objectMapper.createArrayNode();
        long offset = random.nextInt(60, 300);
        for (int index = 0; index < VISITS; index++) {
            ObjectNode visit = (ObjectNode) sources.get(index % sources.size()).deepCopy();
            visit.put("RecordedAtTime", DateTimeFormatter.ISO_INSTANT.format(now.minusSeconds(random.nextInt(5, 60))));
            ((ObjectNode) visit.path("MonitoredVehicleJourney").path("MonitoredCall"))
                    .put("ExpectedDepartureTime", DateTimeFormatter.ISO_INSTANT.format(now.plusSeconds(offset)));
            visits.add(visit);
            offset += random.nextInt(180, 600);
        }
        delivery.set("MonitoredStopVisit", visits);
        return parser.parse(root, now);
    }

    private JsonNode template() {
        JsonNode current = template;
        if (current == null) {
            synchronized (this) {
                if (template == null) {
                    try (var input = fixture.getInputStream()) {
                        template = objectMapper.readTree(input);
                    } catch (IOException exception) {
                        throw new IllegalStateException("La réponse PRIM de test est introuvable.", exception);
                    }
                }
                current = template;
            }
        }
        return current;
    }
}
