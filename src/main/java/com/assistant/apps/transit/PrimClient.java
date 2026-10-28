package com.assistant.apps.transit;

import com.assistant.config.AssistantProperties;
import com.assistant.proxy.OutboundHttp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component("primClient")
public class PrimClient implements TransitGateway {

    static final URI ENDPOINT = URI.create(
            "https://prim.iledefrance-mobilites.fr/marketplace/disruptions_bulk/disruptions/v2");

    private final RestClient restClient;
    private final TransitResponseParser parser;
    private final AssistantProperties properties;
    private final Clock clock;

    @Autowired
    public PrimClient(TransitResponseParser parser, AssistantProperties properties, Clock clock) {
        this(defaultClient(), parser, properties, clock);
    }

    PrimClient(RestClient restClient, TransitResponseParser parser, AssistantProperties properties, Clock clock) {
        this.restClient = restClient;
        this.parser = parser;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public TransitFeed fetchDisruptions() {
        String apiKey = properties.prim().apiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new TransitUnavailableException(null);
        }
        try {
            JsonNode body = restClient.get().uri(ENDPOINT).header("apikey", apiKey).retrieve().body(JsonNode.class);
            return parser.parse(body, Instant.now(clock));
        } catch (RestClientException exception) {
            throw new TransitUnavailableException(exception);
        }
    }

    private static RestClient defaultClient() {
        var client = OutboundHttp.clientBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
