package com.assistant.news;

import com.assistant.auth.AllowGuest;
import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/news")
@RequireSession
@Validated
@Tag(name = "Actualités", description = "Catégories de flux RSS et actualités du profil connecté")
public class NewsController {

    private final NewsService service;

    public NewsController(NewsService service) {
        this.service = service;
    }

    @GetMapping("/categories")
    @Operation(summary = "Liste les catégories définies par l'administrateur, dans l'ordre d'affichage")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catégories (identifiant et nom)"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public List<NewsService.CategoryInfo> categories() {
        return service.listCategories();
    }

    @GetMapping("/selection")
    @Operation(summary = "Récupère les catégories cochées par le profil")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Identifiants des catégories cochées"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application Actualités introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public SelectionResponse getSelection(AuthenticatedSession session) {
        return new SelectionResponse(service.selection(session.profileId()));
    }

    @PutMapping("/selection")
    @Operation(summary = "Enregistre les catégories cochées par le profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Sélection enregistrée"),
            @ApiResponse(responseCode = "400", description = "Catégorie inconnue ou sélection invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Application ou profil introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public SelectionResponse saveSelection(@Valid @RequestBody SelectionRequest request,
                                           AuthenticatedSession session) {
        return new SelectionResponse(service.saveSelection(session.profileId(), request.categoryIds()));
    }

    @GetMapping("/feed")
    @AllowGuest
    @Operation(summary = "Récupère les actualités des catégories cochées (10 entrées maximum par catégorie)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catégories et entrées en texte brut, avec notice éventuelle"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Widget Actualités désactivé",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public NewsService.Overview feed(AuthenticatedSession session) {
        return service.overview(session.profileId(), session.isGuest());
    }

    public record SelectionRequest(@NotNull @Size(max = NewsService.MAX_SELECTION) List<@NotNull Long> categoryIds) {
    }

    public record SelectionResponse(List<Long> categoryIds) {
    }
}
