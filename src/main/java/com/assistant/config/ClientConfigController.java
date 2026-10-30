package com.assistant.config;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/config")
@Tag(name = "Configuration", description = "Reglages exposes au navigateur (issus des variables d'environnement)")
public class ClientConfigController {

    static final int EMBEDDING_SIZE = com.assistant.profile.EmbeddingCodec.SIZE;

    private final AssistantProperties properties;

    public ClientConfigController(AssistantProperties properties) {
        this.properties = properties;
    }

    @Operation(summary = "Reglages de reconnaissance, de session et d'interface utiles au navigateur")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Reglages courants"),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping
    public ClientConfig config() {
        var s = properties.session();
        var scan = properties.scan();
        var r = properties.recognition();
        var e = properties.enrollment();
        var ui = properties.ui();
        var assets = properties.faceAssets();
        return new ClientConfig(
                s.durationMinutes() * 60L, s.heartbeatMinIntervalSeconds(), s.logoutPauseSeconds(),
                scan.intervalDisconnectedMs(), scan.intervalConnectedMs(),
                r.matchThreshold(), r.matchMargin(), r.switchConfirmations(), r.detectorInputSize(),
                r.detectorMinConfidence(), r.minFaceSizePx(), r.cameraWidth(),
                r.backend().name().toLowerCase(java.util.Locale.ROOT), r.debug(),
                e.photos(), ui.typingEnabled(), ui.typingSpeedMs(), EMBEDDING_SIZE,
                assets.mode().name().toLowerCase(java.util.Locale.ROOT), assets.faceApiUrl(), assets.modelsUrl(),
                assets.wasmScriptUrl(), assets.wasmUrl(),
                properties.kiosk().pollIdleMs(), properties.kiosk().pollActiveMs(), properties.qr().devMode(),
                properties.qr().sizeRem());
    }

    public record ClientConfig(
            long sessionDurationSeconds,
            int heartbeatMinIntervalSeconds,
            int logoutPauseSeconds,
            int scanIntervalDisconnectedMs,
            int scanIntervalConnectedMs,
            double matchThreshold,
            double matchMargin,
            int switchConfirmations,
            int detectorInputSize,
            double detectorMinConfidence,
            int minFaceSizePx,
            int cameraWidth,
            String recognitionBackend,
            boolean recognitionDebug,
            int enrollmentPhotos,
            boolean typingEnabled,
            int typingSpeedMs,
            int embeddingSize,
            String faceAssetsMode,
            String faceApiUrl,
            String faceModelsUrl,
            String wasmScriptUrl,
            String wasmUrl,
            int kioskPollIdleMs,
            int kioskPollActiveMs,
            boolean qrDevMode,
            double qrSizeRem) {
    }
}
