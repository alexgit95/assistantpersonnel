package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assistant.common.ApiException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

class RootCertificateControllerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void downloadsTheCertificateAsAnAttachmentWithoutCaching() throws Exception {
        Path certificate = temporaryDirectory.resolve("root.crt");
        Files.writeString(certificate, "test-certificate");
        RootCertificateController controller = new RootCertificateController(certificate.toString());

        var response = controller.download();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("application/x-x509-ca-cert"));
        assertThat(response.getHeaders().getFirst("Content-Disposition")).contains("root.crt");
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        assertThat(response.getBody().getInputStream().readAllBytes()).containsExactly("test-certificate".getBytes());
    }

    @Test
    void returnsNotFoundWhenCaddyHasNotCreatedTheCertificateYet() {
        RootCertificateController controller = new RootCertificateController(
                temporaryDirectory.resolve("missing-root.crt").toString());

        assertThatThrownBy(controller::download)
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}