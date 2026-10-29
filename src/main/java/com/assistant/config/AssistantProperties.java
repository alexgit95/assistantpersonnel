package com.assistant.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Reglages generaux, tous surchargeables par variable d'environnement (voir application.properties). */
@Validated
@ConfigurationProperties(prefix = "assistant")
public record AssistantProperties(
        @Valid @NotNull Session session,
        @Valid @NotNull Scan scan,
        @Valid @NotNull Recognition recognition,
        @Valid @NotNull Enrollment enrollment,
        @Valid @NotNull Weather weather,
        @Valid @NotNull Ui ui,
        @Valid @NotNull Backup backup,
        @Valid @NotNull FaceAssets faceAssets,
        @Valid @NotNull Prim prim) {

    /** Acces PRIM (Ile-de-France Mobilites) : budget journalier d'appels, cache et pause nocturne (heure de Paris). */
    public record Prim(
            @Min(1) @Max(800) int dailyBudget,
            @Min(1) @Max(1440) int cacheMinutes,
            @NotNull @Pattern(regexp = "^$|([01]\\d|2[0-3]):[0-5]\\d$", message = "Heure attendue au format HH:mm") String pauseFrom,
            @NotNull @Pattern(regexp = "^$|([01]\\d|2[0-3]):[0-5]\\d$", message = "Heure attendue au format HH:mm") String pauseTo) {
    }

    public record Session(
            @Min(1) @Max(1440) int durationMinutes,
            @Min(1) @Max(3600) int heartbeatMinIntervalSeconds,
            @Min(0) @Max(3600) int logoutPauseSeconds) {
    }

    public record Scan(
            @Min(100) @Max(600_000) int intervalDisconnectedMs,
            @Min(100) @Max(600_000) int intervalConnectedMs) {
    }

    public enum Backend { AUTO, WEBGL, WASM, CPU }

    public record Recognition(
            @DecimalMin("0.1") @DecimalMax("1.5") double matchThreshold,
            @DecimalMin("0.0") @DecimalMax("1.0") double matchMargin,
            @Min(1) @Max(10) int switchConfirmations,
            @Min(128) @Max(416) int detectorInputSize,
            @DecimalMin("0.1") @DecimalMax("0.99") double detectorMinConfidence,
            @Min(20) @Max(1000) int minFaceSizePx,
            @Min(160) @Max(1920) int cameraWidth,
            @NotNull Backend backend,
            boolean debug) {

        @AssertTrue(message = "ASSISTANT_DETECTOR_INPUT_SIZE doit etre un multiple de 32")
        public boolean isDetectorInputSizeMultipleOf32() {
            return detectorInputSize % 32 == 0;
        }
    }

    public record Enrollment(
            @Min(1) @Max(30) int minPhotos,
            @Min(1) @Max(30) int maxPhotos,
            @Min(0) int maxProfiles) {

        @AssertTrue(message = "ASSISTANT_ENROLLMENT_MIN_PHOTOS doit etre inferieur ou egal a ASSISTANT_ENROLLMENT_MAX_PHOTOS")
        public boolean isPhotoRangeValid() {
            return minPhotos <= maxPhotos;
        }
    }

    public record Weather(
            @Min(1) @Max(1440) int cacheMinutes) {
    }

    public record Ui(
            boolean typingEnabled,
            @Min(1) @Max(1000) int typingSpeedMs) {
    }

    public record Backup(@Min(1) @Max(1024) int maxUploadMb) {
    }

        public enum FaceAssetMode { CDN, LOCAL }

        public record FaceAssets(
                        @NotNull FaceAssetMode mode,
                        @NotNull String faceApiUrl,
                        @NotNull String modelsUrl,
                        @NotNull String wasmScriptUrl,
                        @NotNull String wasmUrl) {
        }
}
