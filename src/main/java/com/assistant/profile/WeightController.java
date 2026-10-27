package com.assistant.profile;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/weight")
@RequireSession
@Validated
@Tag(name = "Poids", description = "Historique de poids du profil connecté")
public class WeightController {

    private final WeightService weightService;

    public WeightController(WeightService weightService) {
        this.weightService = weightService;
    }

    @GetMapping
    @Operation(summary = "Consulte une page de l'historique du poids du profil courant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Page d'entrées de poids"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "400", description = "Pagination invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public WeightService.WeightPage list(AuthenticatedSession session,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) int size) {
        return weightService.list(session.profileId(), page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Enregistre une mesure de poids pour le profil courant")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mesure enregistrée"),
            @ApiResponse(responseCode = "400", description = "Date ou poids invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public WeightService.WeightView create(AuthenticatedSession session, @Valid @RequestBody WeightRequest body) {
        return weightService.create(session.profileId(), body.entryDate(), body.value());
    }

    @PutMapping("/{id}")
    @Operation(summary = "Modifie une mesure appartenant au profil courant")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Mesure modifiée"),
            @ApiResponse(responseCode = "400", description = "Date ou poids invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Mesure introuvable pour ce profil",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public WeightService.WeightView update(@PathVariable long id, AuthenticatedSession session,
            @Valid @RequestBody WeightRequest body) {
        return weightService.update(session.profileId(), id, body.entryDate(), body.value());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Supprime une mesure appartenant au profil courant")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Mesure supprimée"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Mesure introuvable pour ce profil",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public void delete(@PathVariable long id, AuthenticatedSession session) {
        weightService.delete(session.profileId(), id);
    }

    public record WeightRequest(@NotNull LocalDate entryDate, @NotBlank @Size(max = 16) String value) {
    }
}