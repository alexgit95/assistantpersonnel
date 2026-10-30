package com.assistant.qr;

import static org.assertj.core.api.Assertions.assertThat;

import com.assistant.Fixtures;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class QrUrlResolverTest {

    private static MockHttpServletRequest request(String scheme, String host, int port) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setScheme(scheme);
        request.setServerName(host);
        request.setServerPort(port);
        return request;
    }

    @Test
    void configuredBaseUrlWinsOverEverythingElse() {
        var resolver = new QrUrlResolver(Fixtures.withQr("http://192.168.1.50:8080", true));
        assertThat(resolver.resolve(request("http", "localhost", 8080), "/handoff?t=abc"))
                .contains("http://192.168.1.50:8080/handoff?t=abc");
        assertThat(resolver.baseUrl(request("http", "localhost", 8080))).contains("http://192.168.1.50:8080");
    }

    @Test
    void developmentModeSwapsLocalhostForTheLoopbackAddress() {
        var resolver = new QrUrlResolver(Fixtures.withQr("", true));
        assertThat(resolver.resolve(request("http", "localhost", 8080), "/mobile?s=x"))
                .contains("http://127.0.0.1:8080/mobile?s=x");
    }

    @Test
    void developmentModeSwapsTheLoopbackAddressForLocalhost() {
        var resolver = new QrUrlResolver(Fixtures.withQr("", true));
        assertThat(resolver.baseUrl(request("http", "127.0.0.1", 8080))).contains("http://localhost:8080");
    }

    @Test
    void defaultPortsAreOmitted() {
        var resolver = new QrUrlResolver(Fixtures.withQr("", true));
        assertThat(resolver.baseUrl(request("http", "localhost", 80))).contains("http://127.0.0.1");
        assertThat(resolver.baseUrl(request("https", "localhost", 443))).contains("https://127.0.0.1");
    }

    @Test
    void developmentModeNeverReflectsAnotherHost() {
        var resolver = new QrUrlResolver(Fixtures.withQr("", true));
        assertThat(resolver.baseUrl(request("http", "192.168.1.50", 8080))).isEmpty();
        assertThat(resolver.baseUrl(request("http", "evil.example", 8080))).isEmpty();
    }

    @Test
    void withoutBaseUrlAndOutsideDevelopmentModeQrCodesAreDisabled() {
        var resolver = new QrUrlResolver(Fixtures.withQr("", false));
        assertThat(resolver.baseUrl(request("http", "localhost", 8080))).isEmpty();
        assertThat(resolver.resolve(request("http", "localhost", 8080), "/handoff?t=x")).isEmpty();
    }
}
