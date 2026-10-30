package com.assistant.apps.transit.passages;

import com.assistant.apps.transit.PrimKeyService;
import com.assistant.proxy.OutboundHttp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

@Component("primPassageClient")
public class PrimPassageClient implements PassageGateway {

    static final String URL = "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef={ref}";

    private final RestClient restClient;
    private final PassageResponseParser parser;
    private final PrimKeyService keyService;
    private final Clock clock;

    @Autowired
    public PrimPassageClient(PassageResponseParser parser, PrimKeyService keyService, Clock clock) {
        this(defaultClient(), parser, keyService, clock);
    }

    PrimPassageClient(RestClient restClient, PassageResponseParser parser, PrimKeyService keyService, Clock clock) {
        this.restClient = restClient;
        this.parser = parser;
        this.keyService = keyService;
        this.clock = clock;
    }

    static String monitoringRef(String arrid) {
        return "STIF:StopPoint:Q:" + arrid + ":";
    }

    @Override
    public PassageFeed fetch(String arrid) {
        String apiKey = keyService.currentKey().orElseThrow(() -> new PassageUnavailableException(false, null));
        try {
            JsonNode body = restClient.get().uri(URL, monitoringRef(arrid)).header("apikey", apiKey)
                    .retrieve().body(JsonNode.class);
            return parser.parse(body, Instant.now(clock));
        } catch (HttpClientErrorException.BadRequest exception) {
            throw new PassageUnavailableException(true, exception);
        } catch (RestClientException exception) {
            throw new PassageUnavailableException(false, exception);
        }
    }

    private static RestClient defaultClient() {
        var client = OutboundHttp.clientBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }
}
