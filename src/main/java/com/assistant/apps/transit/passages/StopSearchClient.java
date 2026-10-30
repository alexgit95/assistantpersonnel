package com.assistant.apps.transit.passages;

import com.assistant.common.ApiException;
import com.assistant.proxy.OutboundHttp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/** Recherche à la demande dans le jeu de données public « arrets » d'Île-de-France Mobilités (sans clé). */
@Component
public class StopSearchClient {

    static final String URL = "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/arrets/records"
            + "?select=arrid,arrname,arrtown,arrtype&limit=20&where={where}";
    static final int MIN_QUERY = 3;
    static final int MAX_QUERY = 100;
    static final Duration CACHE_TTL = Duration.ofMinutes(5);
    private static final int MAX_CACHE = 50;

    private final RestClient restClient;
    private final Clock clock;
    private final Map<String, Cached> cache = Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Cached> eldest) {
            return size() > MAX_CACHE;
        }
    });

    @Autowired
    public StopSearchClient(Clock clock) {
        this(defaultClient(), clock);
    }

    StopSearchClient(RestClient restClient, Clock clock) {
        this.restClient = restClient;
        this.clock = clock;
    }

    public List<TransitStop> search(String query) {
        String text = validate(query);
        String key = text.toLowerCase(Locale.ROOT);
        Instant now = clock.instant();
        Cached hit = cache.get(key);
        if (hit != null && Duration.between(hit.at(), now).compareTo(CACHE_TTL) < 0) {
            return hit.stops();
        }
        try {
            JsonNode body = restClient.get().uri(URL, "search(arrname,\"" + escape(text) + "\")")
                    .retrieve().body(JsonNode.class);
            List<TransitStop> stops = parse(body);
            cache.put(key, new Cached(now, stops));
            return stops;
        } catch (RestClientException exception) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La recherche d'arrêts est momentanément indisponible.");
        }
    }

    /** Texte nettoyé de la recherche ; 400 s'il est hors des bornes. */
    public static String validate(String query) {
        String text = query == null ? "" : query.replaceAll("\\p{Cntrl}", " ").strip();
        if (text.length() < MIN_QUERY || text.length() > MAX_QUERY) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Saisissez entre " + MIN_QUERY + " et " + MAX_QUERY + " caractères.");
        }
        return text;
    }

    /** Neutralise les guillemets et antislashs pour garder la clause ODS bien formée. */
    static String escape(String text) {
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static List<TransitStop> parse(JsonNode body) {
        JsonNode results = body == null ? null : body.get("results");
        if (results == null || !results.isArray()) {
            return List.of();
        }
        List<TransitStop> stops = new ArrayList<>();
        for (JsonNode node : results) {
            String arrid = text(node, "arrid");
            String name = text(node, "arrname");
            if (arrid == null || !arrid.matches("\\d{1,12}") || name == null) {
                continue;
            }
            stops.add(new TransitStop(arrid, name, orEmpty(text(node, "arrtown")), orEmpty(text(node, "arrtype"))));
        }
        return List.copyOf(stops);
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || value.isNull() || !(value.isTextual() || value.isNumber())) {
            return null;
        }
        String result = value.asString().strip();
        return result.isEmpty() ? null : result;
    }

    private static RestClient defaultClient() {
        var client = OutboundHttp.clientBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        var factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(Duration.ofSeconds(5));
        return RestClient.builder().requestFactory(factory).build();
    }

    private record Cached(Instant at, List<TransitStop> stops) {
    }
}
