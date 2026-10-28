package com.assistant.proxy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import com.assistant.proxy.ProxySettingsService.ProxyInput;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ProxySettingsServiceTest {

    private final ProxySettingRepository repository = mock(ProxySettingRepository.class);
    private final AtomicReference<ProxySetting> stored = new AtomicReference<>();

    ProxySettingsServiceTest() {
        when(repository.findById(ProxySettingsService.SETTING_ID))
                .thenAnswer(invocation -> Optional.ofNullable(stored.get()));
        when(repository.save(any(ProxySetting.class))).thenAnswer(invocation -> {
            stored.set(invocation.getArgument(0));
            return invocation.getArgument(0);
        });
    }

    @AfterEach
    void resetProxy() {
        OutboundHttp.configure(ProxyConfig.DIRECT);
    }

    @Test
    void savedProxyIsAppliedImmediatelyAndRoutesExternalHosts() {
        ProxySettingsService service = new ProxySettingsService(repository);

        service.save(new ProxyInput(true, "http://proxy.lan:3128", "svc", "pw", "intranet.local, *.exemple.fr"));

        ProxyConfig config = OutboundHttp.current();
        assertThat(config.proxyFor(URI.create("https://prim.iledefrance-mobilites.fr/x")).address().toString())
                .contains("proxy.lan");
        assertThat(config.proxyFor(URI.create("https://api.exemple.fr/x"))).isEqualTo(Proxy.NO_PROXY);
        assertThat(config.proxyFor(URI.create("http://intranet.local/x"))).isEqualTo(Proxy.NO_PROXY);
        assertThat(config.proxyFor(URI.create("http://localhost:8080/x"))).isEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void disabledOrMissingProxyMeansDirectCalls() {
        ProxySettingsService service = new ProxySettingsService(repository);
        service.applyStoredSetting();
        assertThat(OutboundHttp.current().proxyFor(URI.create("https://a.example/x"))).isEqualTo(Proxy.NO_PROXY);

        service.save(new ProxyInput(true, "http://proxy.lan:3128", "", "", ""));
        service.save(new ProxyInput(false, "http://proxy.lan:3128", "", "", ""));

        assertThat(OutboundHttp.current().proxyFor(URI.create("https://a.example/x"))).isEqualTo(Proxy.NO_PROXY);
    }

    @Test
    void emptyPasswordKeepsStoredPasswordAndViewNeverExposesIt() {
        ProxySettingsService service = new ProxySettingsService(repository);
        service.save(new ProxyInput(true, "http://proxy.lan:3128", "svc", "first-secret", ""));

        service.save(new ProxyInput(true, "http://proxy.lan:8080", "svc", "", ""));

        assertThat(stored.get().getPassword()).isEqualTo("first-secret");
        assertThat(OutboundHttp.current().password()).isEqualTo("first-secret");
        assertThat(service.current().hasPassword()).isTrue();
        assertThat(service.current().toString()).doesNotContain("first-secret");
    }

    @Test
    void invalidInputIsRejectedWithoutChangingActiveConfiguration() {
        ProxySettingsService service = new ProxySettingsService(repository);
        service.save(new ProxyInput(true, "http://proxy.lan:3128", "", "", ""));

        for (String bad : new String[] {"ftp://proxy.lan", "proxy.lan:3128", "http://", "http://u:p@proxy.lan",
            "http://proxy.lan/path", "http://proxy.lan:99999", "http://proxy.lan\n:3128"}) {
            assertThatThrownBy(() -> service.save(new ProxyInput(true, bad, "", "", "")))
                    .as(bad).isInstanceOf(ApiException.class);
        }
        assertThatThrownBy(() -> service.save(new ProxyInput(true, "", "", "", "")))
                .isInstanceOf(ApiException.class);
        assertThat(stored.get().getUrl()).isEqualTo("http://proxy.lan:3128");
        assertThat(OutboundHttp.current().port()).isEqualTo(3128);
    }

    @Test
    void connectionTestUsesTheProxyWithCredentialsWithoutSavingAnything() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer fakeProxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        fakeProxy.createContext("/", exchange -> {
            String header = exchange.getRequestHeaders().getFirst("Proxy-Authorization");
            if (header == null) {
                exchange.getResponseHeaders().add("Proxy-Authenticate", "Basic realm=\"proxy\"");
                exchange.sendResponseHeaders(407, -1);
            } else {
                authorization.set(header);
                exchange.sendResponseHeaders(200, -1);
            }
            exchange.close();
        });
        fakeProxy.start();
        try {
            ProxySettingsService service = new ProxySettingsService(repository, "http://target.example/ping");
            String url = "http://127.0.0.1:" + fakeProxy.getAddress().getPort();

            var success = service.test(new ProxyInput(false, url, "svc", "pw", ""));
            var refused = service.test(new ProxyInput(false, url, "", "", ""));

            assertThat(success.success()).isTrue();
            assertThat(authorization.get()).isEqualTo("Basic "
                    + Base64.getEncoder().encodeToString("svc:pw".getBytes()));
            assertThat(refused.success()).isFalse();
            assertThat(refused.message()).contains("authentification");
            assertThat(stored.get()).isNull();
            assertThat(OutboundHttp.current()).isEqualTo(ProxyConfig.DIRECT);
        } finally {
            fakeProxy.stop(0);
        }
    }

    @Test
    void connectionTestReportsUnreachableProxyWithoutLeakingCredentials() {
        ProxySettingsService service = new ProxySettingsService(repository, "http://target.example/ping");

        var result = service.test(new ProxyInput(true, "http://127.0.0.1:1", "svc", "top-secret", ""));

        assertThat(result.success()).isFalse();
        assertThat(result.message()).doesNotContain("top-secret").doesNotContain("svc");
        assertThat(service.test(new ProxyInput(true, "nope", "", "", "")).success()).isFalse();
    }
}
