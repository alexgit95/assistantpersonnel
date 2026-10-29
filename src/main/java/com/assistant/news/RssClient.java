package com.assistant.news;

import com.assistant.proxy.OutboundHttp;
import com.rometools.rome.feed.synd.SyndContent;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RssClient implements NewsGateway {

    static final int MAX_ENTRIES = 10;
    static final int MAX_BYTES = 1024 * 1024;
    static final int MAX_TITLE_LENGTH = 200;
    static final int MAX_DESCRIPTION_LENGTH = 600;
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(8);

    private final FeedUrlPolicy urlPolicy;
    private final HttpClient httpClient;

    @Autowired
    public RssClient(FeedUrlPolicy urlPolicy) {
        this(urlPolicy, OutboundHttp.clientBuilder().connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NEVER).build());
    }

    RssClient(FeedUrlPolicy urlPolicy, HttpClient httpClient) {
        this.urlPolicy = urlPolicy;
        this.httpClient = httpClient;
    }

    @Override
    public List<NewsEntry> fetch(NewsCategory category) {
        URI uri = urlPolicy.requirePublic(category.getFeedUrl());
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(REQUEST_TIMEOUT).GET()
                .header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml")
                .header("User-Agent", "assistant-personnel/news").build();
        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    throw new NewsUnavailableException("Réponse HTTP " + response.statusCode());
                }
                return parse(readLimited(body));
            }
        } catch (IOException exception) {
            throw new NewsUnavailableException("Flux inaccessible.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NewsUnavailableException("Récupération interrompue.", exception);
        }
    }

    List<NewsEntry> parse(byte[] xml) {
        SyndFeedInput input = new SyndFeedInput();
        input.setAllowDoctypes(false);
        SyndFeed feed;
        try (XmlReader reader = new XmlReader(new ByteArrayInputStream(xml))) {
            feed = input.build(reader);
        } catch (FeedException | IOException | RuntimeException exception) {
            throw new NewsUnavailableException("Flux invalide.", exception);
        }
        List<NewsEntry> entries = new ArrayList<>(MAX_ENTRIES);
        for (SyndEntry entry : feed.getEntries()) {
            if (entries.size() == MAX_ENTRIES) {
                break;
            }
            String title = NewsText.plain(entry.getTitle(), MAX_TITLE_LENGTH);
            if (title.isEmpty()) {
                continue;
            }
            entries.add(new NewsEntry(title, NewsText.plain(descriptionOf(entry), MAX_DESCRIPTION_LENGTH)));
        }
        return List.copyOf(entries);
    }

    private static String descriptionOf(SyndEntry entry) {
        SyndContent description = entry.getDescription();
        if (description != null && description.getValue() != null && !description.getValue().isBlank()) {
            return description.getValue();
        }
        return entry.getContents().stream().map(SyndContent::getValue)
                .filter(value -> value != null && !value.isBlank()).findFirst().orElse("");
    }

    private static byte[] readLimited(InputStream body) throws IOException {
        byte[] bytes = body.readNBytes(MAX_BYTES + 1);
        if (bytes.length > MAX_BYTES) {
            throw new NewsUnavailableException("Réponse trop volumineuse.");
        }
        return bytes;
    }
}
