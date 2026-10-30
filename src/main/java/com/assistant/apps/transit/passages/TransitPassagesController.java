package com.assistant.apps.transit.passages;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/transit-passages")
@RequireSession
@Validated
@Tag(name = "Prochains passages", description = "Arrêts favoris et prochains passages PRIM du profil connecté")
public class TransitPassagesController {

    private final NextPassagesService service;

    public TransitPassagesController(NextPassagesService service) {
        this.service = service;
    }

    @GetMapping("/stops/search")
    @Operation(summary = "Recherche d'arrêts dans l'API publique Île-de-France Mobilités")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vingt arrêts au plus : identifiant, nom, ville et type"),
            @ApiResponse(responseCode = "400", description = "Recherche de moins de 3 ou plus de 100 caractères",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "API de recherche indisponible",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public List<TransitStop> search(@RequestParam @NotBlank @Size(max = 100) String query) {
        return service.searchStops(query);
    }

    @GetMapping("/stops")
    @Operation(summary = "Récupère les arrêts enregistrés par le profil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arrêts enregistrés (trois au maximum)"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application Prochains passages introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public StopsResponse getStops(AuthenticatedSession session) {
        return new StopsResponse(service.stops(session.profileId()));
    }

    @PutMapping("/stops")
    @Operation(summary = "Enregistre jusqu'à trois arrêts pour le profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Arrêts enregistrés"),
            @ApiResponse(responseCode = "400", description = "Arrêt invalide, doublon ou plus de trois arrêts",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application ou profil introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public StopsResponse saveStops(@Valid @RequestBody StopsRequest request, AuthenticatedSession session) {
        return new StopsResponse(service.saveStops(session.profileId(), request.stops()));
    }

    @GetMapping("/next")
    @Operation(summary = "Récupère les quatre prochains passages de chaque arrêt du profil")
    @ApiResponses({
            @ApiResponse(responseCode = "200",
                    description = "Passages en minutes restantes, message de pause, de budget ou de clé absente"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Widget Prochains passages désactivé",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public NextPassagesService.Overview next(AuthenticatedSession session) {
        return service.next(session.profileId());
    }

    public record StopsRequest(@NotNull @Size(max = 3) List<@Valid TransitStop> stops) {
    }

    public record StopsResponse(List<TransitStop> stops) {
    }
}
