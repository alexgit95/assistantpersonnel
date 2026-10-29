package com.assistant.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.common.ApiException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.junit.jupiter.api.Test;

class FeedUrlPolicyTest {

    private static InetAddress ip(int... octets) throws UnknownHostException {
        byte[] bytes = new byte[octets.length];
        for (int i = 0; i < octets.length; i++) {
            bytes[i] = (byte) octets[i];
        }
        return InetAddress.getByAddress(bytes);
    }

    private final FeedUrlPolicy policy = new FeedUrlPolicy(host -> new InetAddress[] {ip(93, 184, 216, 34)});

    @Test
    void acceptsAnHttpsUrl() {
        assertThat(policy.validateForSave("  https://example.org/feed.xml ").toString())
                .isEqualTo("https://example.org/feed.xml");
    }

    @Test
    void rejectsNonHttpsOrMalformedUrls() {
        for (String value : new String[] {"http://example.org/feed", "ftp://example.org", "", "  ", null,
                "https://", "https://user:pw@example.org/feed", "not a url"}) {
            assertThatThrownBy(() -> policy.validateForSave(value)).as(String.valueOf(value))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void rejectsTooLongUrls() {
        String url = "https://example.org/" + "a".repeat(NewsCategoryService.MAX_URL_LENGTH);

        assertThatThrownBy(() -> policy.validateForSave(url)).isInstanceOf(ApiException.class);
    }

    @Test
    void rejectsLocalHostsAndLiteralAddressesOnSave() {
        FeedUrlPolicy real = new FeedUrlPolicy();
        for (String host : new String[] {"localhost", "feeds.localhost", "printer.local", "127.0.0.1", "10.1.2.3",
                "192.168.0.10", "172.16.5.5", "169.254.1.1", "[::1]", "[fc00::1]", "0.0.0.0", "100.64.0.1",
                "2130706433"}) {
            assertThatThrownBy(() -> real.validateForSave("https://" + host + "/feed")).as(host)
                    .isInstanceOf(ApiException.class);
        }
    }

    @Test
    void acceptsPublicNamesWithoutResolvingThemOnSave() {
        FeedUrlPolicy failingResolver = new FeedUrlPolicy(host -> {
            throw new UnknownHostException(host);
        });

        assertThat(failingResolver.validateForSave("https://example.org/feed")).isNotNull();
    }

    @Test
    void requiresAPublicAddressOnEveryFetch() {
        assertThat(policy.requirePublic("https://example.org/feed").getHost()).isEqualTo("example.org");
    }

    @Test
    void refusesAHostResolvingToAPrivateAddress() {
        FeedUrlPolicy rebinding = new FeedUrlPolicy(host -> new InetAddress[] {ip(93, 184, 216, 34), ip(10, 0, 0, 5)});

        assertThatThrownBy(() -> rebinding.requirePublic("https://example.org/feed"))
                .isInstanceOf(NewsUnavailableException.class);
    }

    @Test
    void refusesAnUnknownHostOrAnInvalidStoredUrl() {
        FeedUrlPolicy unknown = new FeedUrlPolicy(host -> {
            throw new UnknownHostException(host);
        });

        assertThatThrownBy(() -> unknown.requirePublic("https://example.org/feed"))
                .isInstanceOf(NewsUnavailableException.class);
        assertThatThrownBy(() -> policy.requirePublic("http://example.org/feed"))
                .isInstanceOf(NewsUnavailableException.class);
    }

    @Test
    void classifiesLocalAddresses() throws Exception {
        assertThat(FeedUrlPolicy.isLocal(ip(192, 168, 1, 1))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(ip(172, 16, 0, 1))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(ip(169, 254, 1, 1))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(ip(100, 64, 0, 1))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(ip(224, 0, 0, 1))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(InetAddress.getByName("fc00::1"))).isTrue();
        assertThat(FeedUrlPolicy.isLocal(ip(8, 8, 8, 8))).isFalse();
        assertThat(FeedUrlPolicy.isLocal(ip(100, 128, 0, 1))).isFalse();
    }
}
