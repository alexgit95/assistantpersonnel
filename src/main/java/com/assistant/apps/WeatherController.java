package com.assistant.apps;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weather")
@RequireSession
@Validated
@Tag(name = "Météo", description = "Géocodage et prévisions météo du profil connecté")
public class WeatherController {

    private final WeatherService weatherService;

    public WeatherController(WeatherService weatherService) {
        this.weatherService = weatherService;
    }

    @GetMapping("/cities")
    @Operation(summary = "Recherche une ville dans le service de géocodage Open-Meteo")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Villes correspondantes, éventuellement plusieurs"),
            @ApiResponse(responseCode = "400", description = "Recherche trop courte",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Service météo indisponible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public java.util.List<WeatherLocation> cities(AuthenticatedSession session,
            @RequestParam @NotBlank @Size(min = 2, max = 100) String query) {
        return weatherService.searchCities(query);
    }

    @PutMapping("/location")
    @Operation(summary = "Enregistre la ville choisie pour le profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Ville enregistrée"),
            @ApiResponse(responseCode = "400", description = "Ville invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil ou application introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public WeatherLocation saveLocation(AuthenticatedSession session, @Valid @RequestBody LocationRequest request) {
        return weatherService.saveLocation(session.profileId(), new WeatherLocation(request.id(), request.name(),
                request.region(), request.country(), request.latitude(), request.longitude()));
    }

    @GetMapping
    @Operation(summary = "Récupère la météo actuelle et les prévisions du profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Météo actuelle et prévisions"),
            @ApiResponse(responseCode = "400", description = "Aucune ville configurée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Service météo indisponible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public WeatherForecast forecast(AuthenticatedSession session) {
        return weatherService.forecast(session.profileId());
    }

    public record LocationRequest(@Positive long id, @NotBlank @Size(max = 100) String name,
            @Size(max = 100) String region, @NotBlank @Size(max = 100) String country,
            @DecimalMin("-90.0") @DecimalMax("90.0") double latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") double longitude) {
    }
}