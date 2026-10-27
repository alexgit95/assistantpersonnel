package com.assistant.apps;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import com.assistant.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/apps")
@RequireSession
@Tag(name = "Applications", description = "Catalogue et préférences des applications du profil courant")
public class ProfileAppsController {

    private final ProfileAppService profileAppService;

    public ProfileAppsController(ProfileAppService profileAppService) {
        this.profileAppService = profileAppService;
    }

    @GetMapping
    @Operation(summary = "Catalogue des applications et choix du profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue et préférences du profil"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public List<ProfileAppService.AppPreference> list(AuthenticatedSession session) {
        return profileAppService.listForProfile(session.profileId());
    }

    @PutMapping
    @Operation(summary = "Remplace les préférences d'applications du profil connecté")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Préférences enregistrées"),
            @ApiResponse(responseCode = "400", description = "Clé, position ou configuration invalide",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expirée",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    public List<ProfileAppService.AppPreference> replace(@Valid @RequestBody ReplaceRequest request,
                                                         AuthenticatedSession session) {
        if (request.selections() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La liste des applications est requise.");
        }
        return profileAppService.replaceAll(session.profileId(), request.selections().stream()
                .map(item -> new ProfileAppService.Selection(item.key(), item.enabled(), item.position(), item.configJson()))
                .toList());
    }

    public record ReplaceRequest(@Size(max = 50) List<@Valid SelectionRequest> selections) {
    }

    public record SelectionRequest(@NotBlank @Size(max = 64) String key, boolean enabled,
                                   @Min(-1) @Max(ProfileAppService.SLOT_COUNT - 1) int position,
                                                                   @Size(max = ProfileAppService.MAX_CONFIG_LENGTH) String configJson) {
    }
}
