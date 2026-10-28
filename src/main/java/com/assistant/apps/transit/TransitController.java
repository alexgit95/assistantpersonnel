package com.assistant.apps.transit;

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
@RequestMapping("/api/transit")
@RequireSession
@Validated
@Tag(name = "Transports", description = "Catalogue de lignes et alertes trafic du profil connecté")
public class TransitController {

    private final TransitService service;

    public TransitController(TransitService service) {
        this.service = service;
    }

    @GetMapping("/lines")
    @Operation(summary = "Recherche dans le catalogue local des lignes actives")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lignes correspondantes, sans appel externe"),
            @ApiResponse(responseCode = "400", description = "Recherche invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public List<TransitLine> lines(@RequestParam @NotBlank @Size(max = 100) String query) {
        return service.searchLines(query);
    }

    @GetMapping("/favorites")
    @Operation(summary = "Récupère les lignes favorites configurées par le profil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lignes favorites et identifiants"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application Transports introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public FavoritesResponse getFavorites(AuthenticatedSession session) {
        List<TransitLine> saved = service.favorites(session.profileId());
        return new FavoritesResponse(saved.stream().map(TransitLine::id).toList(), saved);
    }

    @PutMapping("/favorites")
    @Operation(summary = "Enregistre jusqu'à quatre lignes favorites pour le profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lignes favorites enregistrées"),
            @ApiResponse(responseCode = "400", description = "Ligne inconnue, doublon ou plus de quatre favoris",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application ou profil introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public FavoritesResponse favorites(@Valid @RequestBody FavoritesRequest request, AuthenticatedSession session) {
        List<TransitLine> saved = service.saveFavorites(session.profileId(), request.lineIds());
        return new FavoritesResponse(saved.stream().map(TransitLine::id).toList(), saved);
    }

    @GetMapping("/alerts")
    @Operation(summary = "Récupère les alertes des lignes favorites du profil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Alertes, état de pause et horodatage du flux"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Widget Transports désactivé",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "Flux PRIM indisponible sans données en cache",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public TransitService.Overview alerts(AuthenticatedSession session) {
        return service.alerts(session.profileId());
    }

    public record FavoritesRequest(@NotNull @Size(max = 4) List<@NotBlank @Size(max = 16) String> lineIds) {
    }

    public record FavoritesResponse(List<String> lineIds, List<TransitLine> lines) {
    }
}
