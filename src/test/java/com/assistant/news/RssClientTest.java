package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class RssClientTest {

    private static final String RSS = """
            <?xml version="1.0" encoding="UTF-8"?>
            <rss version="2.0"><channel><title>Flux</title><link>https://example.org</link><description>d</description>
            <item><title>Premier &amp; second</title><description>&lt;p&gt;Corps &lt;b&gt;gras&lt;/b&gt;&lt;/p&gt;&lt;script&gt;x()&lt;/script&gt;</description></item>
            <item><title>Sans description</title></item>
            <item><title>  </title><description>ignoré</description></item>
            </channel></rss>
            """;

    private static final String ATOM = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"><title>Flux</title><id>urn:x</id><updated>2026-10-29T10:00:00Z</updated>
            <entry><title>Entrée Atom</title><id>urn:1</id><updated>2026-10-29T10:00:00Z</updated>
            <summary>Résumé Atom</summary></entry>
            <entry><title>Avec contenu</title><id>urn:2</id><updated>2026-10-29T10:00:00Z</updated>
            <content type="html">&lt;i&gt;Contenu&lt;/i&gt;</content></entry>
            </feed>
            """;

    private final FeedUrlPolicy policy = new FeedUrlPolicy(host -> new InetAddress[] {
            InetAddress.getByAddress(new byte[] {93, (byte) 184, (byte) 216, 34})});

    private RssClient client(HttpClient http) {
        return new RssClient(policy, http);
    }

    private static List<NewsEntry> parse(String xml) {
        return new RssClient(new FeedUrlPolicy(), mock(HttpClient.class)).parse(xml.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void parsesRssAsPlainText() {
        List<NewsEntry> entries = parse(RSS);

        assertThat(entries).containsExactly(
                new NewsEntry("Premier & second", "Corps gras"),
                new NewsEntry("Sans description", ""));
    }

    @Test
    void parsesAtomSummaryAndContent() {
        assertThat(parse(ATOM)).containsExactly(
                new NewsEntry("Entrée Atom", "Résumé Atom"),
                new NewsEntry("Avec contenu", "Contenu"));
    }

    @Test
    void keepsAtMostTenEntries() {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            items.append("<item><title>Titre ").append(i).append("</title></item>");
        }
        String xml = "<rss version=\"2.0\"><channel><title>t</title><link>https://e.org</link><description>d</description>"
                + items + "</channel></rss>";

        List<NewsEntry> entries = parse(xml);

        assertThat(entries).hasSize(RssClient.MAX_ENTRIES);
        assertThat(entries.get(0).title()).isEqualTo("Titre 0");
    }

    @Test
    void refusesDoctypesToPreventXxe() {
        String xml = "<?xml version=\"1.0\"?><!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                + "<rss version=\"2.0\"><channel><title>t</title><item><title>&xxe;</title></item></channel></rss>";

        assertThatThrownBy(() -> parse(xml)).isInstanceOf(NewsUnavailableException.class);
    }

    @Test
    void refusesInvalidXml() {
        assertThatThrownBy(() -> parse("pas du xml")).isInstanceOf(NewsUnavailableException.class);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<InputStream> response(int status, byte[] body) {
        HttpResponse<InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(new ByteArrayInputStream(body));
        return response;
    }

    @SuppressWarnings("unchecked")
    private static HttpClient clientReturning(HttpResponse<InputStream> response) throws Exception {
        HttpClient http = mock(HttpClient.class);
        when(http.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) response);
        return http;
    }

    private static NewsCategory category(String url) {
        return new NewsCategory("Cat", url, 0);
    }

    @Test
    void fetchesAndParsesAFeedOverHttps() throws Exception {
        HttpClient http = clientReturning(response(200, RSS.getBytes(StandardCharsets.UTF_8)));

        List<NewsEntry> entries = client(http).fetch(category("https://example.org/feed"));

        assertThat(entries).hasSize(2);
    }

    @Test
    void doesNotFollowRedirectsAndTreatsNon200AsUnavailable() throws Exception {
        HttpClient http = clientReturning(response(302, new byte[0]));

        assertThatThrownBy(() -> client(http).fetch(category("https://example.org/feed")))
                .isInstanceOf(NewsUnavailableException.class);
    }

    @Test
    void rejectsAResponseLargerThanTheLimit() throws Exception {
        HttpClient http = clientReturning(response(200, new byte[RssClient.MAX_BYTES + 1]));

        assertThatThrownBy(() -> client(http).fetch(category("https://example.org/feed")))
                .isInstanceOf(NewsUnavailableException.class)
                .hasMessageContaining("volumineuse");
    }

    @Test
    @SuppressWarnings("unchecked")
    void neverCallsOutWhenTheHostIsLocal() throws Exception {
        HttpClient http = mock(HttpClient.class);
        FeedUrlPolicy rebinding = new FeedUrlPolicy(host -> new InetAddress[] {
                InetAddress.getByAddress(new byte[] {10, 0, 0, 1})});

        assertThatThrownBy(() -> new RssClient(rebinding, http).fetch(category("https://example.org/feed")))
                .isInstanceOf(NewsUnavailableException.class);
        verify(http, never()).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }
}
