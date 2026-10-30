package com.assistant.apps.transit.passages;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Arrêt IDFM choisi par le profil ; {@code arrid} sert à construire le MonitoringRef PRIM. */
public record TransitStop(
        @NotBlank @Pattern(regexp = "\\d{1,12}", message = "Identifiant d'arrêt invalide") String arrid,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 100) String town,
        @Size(max = 30) String type) {
}
