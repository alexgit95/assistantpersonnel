package com.assistant.config;

import com.assistant.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Certificat HTTPS", description = "Téléchargement de l'autorité locale Caddy")
public class RootCertificateController {

    private static final MediaType CERTIFICATE_MEDIA_TYPE = MediaType.parseMediaType("application/x-x509-ca-cert");
    private final Path certificatePath;

    public RootCertificateController(@Value("${assistant.certificate.root-path:/caddy-data/caddy/pki/authorities/local/root.crt}")
                                     String certificatePath) {
        this.certificatePath = Path.of(certificatePath);
    }

    @GetMapping(value = "/root.crt", produces = "application/x-x509-ca-cert")
    @Operation(summary = "Télécharge le certificat racine Caddy pour ce réseau")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Certificat racine PEM"),
            @ApiResponse(responseCode = "404", description = "Certificat indisponible sur ce déploiement",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public ResponseEntity<Resource> download() {
        if (!Files.isRegularFile(certificatePath) || !Files.isReadable(certificatePath)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Certificat racine indisponible sur ce déploiement.");
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .contentType(CERTIFICATE_MEDIA_TYPE)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("root.crt").build().toString())
                .body(new FileSystemResource(certificatePath));
    }
}