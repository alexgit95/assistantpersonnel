package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.assistant.Fixtures;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.connector.Connector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;

class HttpConnectorConfigurationTest {

    @TempDir
    Path dir;

    private AssistantProperties with(boolean httpEnabled, boolean httpsEnabled) throws IOException {
        return with(httpEnabled, httpsEnabled, Files.createFile(dir.resolve("assistant.p12")).toString());
    }

    private static AssistantProperties with(boolean httpEnabled, boolean httpsEnabled, String keystorePath) {
        var base = Fixtures.properties();
        return new AssistantProperties(base.session(), base.scan(), base.recognition(), base.enrollment(),
                base.weather(), base.ui(), base.backup(), base.faceAssets(), base.prim(), base.publicBaseUrl(),
                base.kiosk(), base.handoff(), base.qr(), new AssistantProperties.Http(httpEnabled, 9080, 9080),
                new AssistantProperties.Https(httpsEnabled, 9443, "/certs", keystorePath, "PKCS12",
                        "secret", "", 825));
    }

    private static TomcatServletWebServerFactory customize(AssistantProperties properties) {
        TomcatServletWebServerFactory factory = mock(TomcatServletWebServerFactory.class);
        new HttpConnectorConfiguration().additionalHttpConnector(properties).customize(factory);
        return factory;
    }

    @Test
    void addsAnHttpConnectorWhenBothProtocolsAreEnabled() throws IOException {
        TomcatServletWebServerFactory factory = customize(with(true, true));

        ArgumentCaptor<Connector> captor = ArgumentCaptor.forClass(Connector.class);
        verify(factory).addAdditionalConnectors(captor.capture());
        Connector connector = captor.getValue();
        assertThat(connector.getPort()).isEqualTo(9080);
        assertThat(connector.getScheme()).isEqualTo("http");
        assertThat(connector.getSecure()).isFalse();
    }

    @Test
    void addsNothingInHttpOnlyMode() throws IOException {
        TomcatServletWebServerFactory factory = customize(with(true, false));

        verify(factory, never()).addAdditionalConnectors(any());
    }

    @Test
    void addsNothingWhenHttpIsDisabled() throws IOException {
        TomcatServletWebServerFactory factory = customize(with(false, true));

        verify(factory, never()).addAdditionalConnectors(any());
    }

    @Test
    void refusesToStartWhenTheKeystoreIsMissing() {
        AssistantProperties properties = with(true, true, dir.resolve("absent.p12").toString());

        assertThatThrownBy(() -> customize(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Keystore HTTPS illisible")
                .hasMessageContaining("absent.p12");
    }

    @Test
    void doesNotCheckTheKeystoreInHttpOnlyMode() {
        TomcatServletWebServerFactory factory = customize(with(true, false, dir.resolve("absent.p12").toString()));

        verify(factory, never()).addAdditionalConnectors(any());
    }
}
