package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Mode remote (axe https-dual) sur SQLite : HTTPS sur le port principal et HTTP sur le connecteur additionnel. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
@ActiveProfiles({"sqlite", "assets-cdn", "https-dual"})
class DualConnectorIntegrationTest {

    private static final String PASSWORD = "test-keystore-password";
    private static final int HTTP_PORT = freePort();
    private static final int HTTPS_PORT = freePort();

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path generateKeystore() throws Exception {
        Path keystore = Files.createTempDirectory("assistant-tls").resolve("assistant.p12");
        String keytool = Path.of(System.getProperty("java.home"), "bin", "keytool").toString();
        Process process = new ProcessBuilder(keytool, "-genkeypair", "-alias", "assistant", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "2", "-dname", "CN=localhost", "-ext", "SAN=dns:localhost,ip:127.0.0.1",
                "-storetype", "PKCS12", "-keystore", keystore.toString(), "-storepass", PASSWORD)
                .redirectErrorStream(true).start();
        assertThat(process.waitFor()).as(new String(process.getInputStream().readAllBytes())).isZero();
        return keystore;
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Path keystore = generateKeystore();
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:target/it-dual-" + UUID.randomUUID() + ".db?foreign_keys=on");
        registry.add("assistant.admin.password", () -> "integration-admin-password");
        registry.add("assistant.http.port", () -> HTTP_PORT);
        registry.add("assistant.http.public-port", () -> HTTP_PORT);
        registry.add("assistant.https.port", () -> HTTPS_PORT);
        registry.add("assistant.https.keystore-path", () -> keystore.toString().replace('\\', '/'));
        registry.add("assistant.https.keystore-password", () -> PASSWORD);
    }

    private static HttpClient trustingClient() throws Exception {
        TrustManager trustAll = new X509TrustManager() {
            @Override
            public void checkClientTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public void checkServerTrusted(X509Certificate[] chain, String authType) {
            }

            @Override
            public X509Certificate[] getAcceptedIssuers() {
                return new X509Certificate[0];
            }
        };
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[] {trustAll}, new SecureRandom());
        return HttpClient.newBuilder().sslContext(context).build();
    }

    private static HttpResponse<String> get(String url) throws Exception {
        return trustingClient().send(HttpRequest.newBuilder(URI.create(url)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void httpsAnswersOnThePrimaryPort() throws Exception {
        HttpResponse<String> response = get("https://localhost:" + HTTPS_PORT + "/mobile");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Strict-Transport-Security")).isEmpty();
    }

    @Test
    void httpAnswersOnTheAdditionalConnector() throws Exception {
        HttpResponse<String> response = get("http://localhost:" + HTTP_PORT + "/mobile");

        assertThat(response.statusCode()).isEqualTo(200);
    }
}
