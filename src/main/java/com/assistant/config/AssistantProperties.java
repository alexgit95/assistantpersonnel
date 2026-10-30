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
        @Valid @NotNull Prim prim,
        @NotNull @Pattern(regexp = "^$|^https?://[^\\s/]+(/\\S*[^\\s/])?$",
                message = "URL http(s) attendue, sans slash final") String publicBaseUrl,
        @Valid @NotNull Kiosk kiosk,
        @Valid @NotNull Handoff handoff,
        @Valid @NotNull Qr qr) {

    /** Cle partagee avec le navigateur du Pi (vide = controle desactive, refuse en production) et cadence de son poll d'etat. */
    public record Kiosk(
            @NotNull String key,
            @Min(250) @Max(60_000) int pollIdleMs,
            @Min(250) @Max(60_000) int pollActiveMs) {
    }

    /** Jetons de transfert de session vers un smartphone et duree de ses sessions transitoires. */
    public record Handoff(
            @Min(10) @Max(600) int tokenTtlSeconds,
            @Min(1) @Max(1440) int fullSessionMinutes,
            @Min(1) @Max(1440) int weightSessionMinutes) {
    }

    /** Aides de developpement des QR (lien cliquable, URL affichee, bascule localhost / 127.0.0.1) et taille d'affichage en rem. */
    public record Qr(boolean devMode, @DecimalMin("2.0") @DecimalMax("30.0") double sizeRem) {
    }

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
            @Min(1) @Max(30) int photos,
            @Min(0) int maxProfiles,
            @Valid @NotNull Remote remote) {

        /** Capture a distance : le Pi prend les photos, le smartphone pilote. */
        public record Remote(
                @Min(1) @Max(60) int countdownSeconds,
                @Min(1) @Max(60) int intervalSeconds,
                @Min(1) @Max(120) int sessionTtlMinutes,
                @Min(1) @Max(20) int maxConsecutiveFailures) {
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
