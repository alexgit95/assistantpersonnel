package com.assistant.apps.transit;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class MockTransitGateway implements TransitGateway {

    private final Resource fixture;
    private final TransitResponseParser parser;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private volatile TransitFeed cached;

    public MockTransitGateway(ResourceLoader resources, TransitResponseParser parser, ObjectMapper objectMapper, Clock clock) {
        this.fixture = resources.getResource("classpath:integration/response_info-traffic.json");
        this.parser = parser;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public TransitFeed fetchDisruptions() {
        TransitFeed current = cached;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (cached == null) {
                try (var input = fixture.getInputStream()) {
                    cached = parser.parse(objectMapper.readTree(input), Instant.now(clock));
                } catch (IOException exception) {
                    throw new IllegalStateException("La réponse PRIM de test est introuvable.", exception);
                }
            }
            return cached;
        }
    }
}
