package com.assistant.profile;

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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Profils", description = "Creation et gestion des profils et de leurs empreintes faciales")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "Liste des visages connus (public sur le LAN)",
            description = "Identifiant, nom, avatar et empreintes de chaque profil, pour la comparaison cote navigateur. "
                    + "Ne contient aucune donnee d'application.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Liste des profils"),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/faces")
    public ResponseEntity<List<ProfileService.FaceView>> faces() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profileService.listFaces());
    }

    @Operation(summary = "Cree un profil a partir des captures du visage",
            description = "Les empreintes sont calculees dans le navigateur ; seul l'avatar choisi est conserve comme image.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Profil cree"),
            @ApiResponse(responseCode = "400", description = "Nom, empreintes ou avatar invalides", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Visage deja enregistre ou limite de profils atteinte", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/profiles")
    @ResponseStatus(HttpStatus.CREATED)
    public ProfileService.ProfileView create(@Valid @RequestBody CreateProfileRequest body) {
        return profileService.create(body.name(), toVectors(body.embeddings()), body.avatar());
    }

    @RequireSession
    @Operation(summary = "Profil de la session courante")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil courant"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/profiles/me")
    public ProfileService.ProfileView me(AuthenticatedSession session) {
        return profileService.getProfile(session.profileId());
    }

    @RequireSession
    @Operation(summary = "Ajoute ou remplace les empreintes du profil connecte (refaire ses captures)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Empreintes mises a jour"),
            @ApiResponse(responseCode = "400", description = "Empreintes ou avatar invalides", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Profil d'un autre utilisateur", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Visage correspondant a un autre profil", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PutMapping("/profiles/{id}/embeddings")
    public ProfileService.ProfileView updateEmbeddings(@PathVariable long id, AuthenticatedSession session,
                                                       @Valid @RequestBody UpdateEmbeddingsRequest body) {
        requireOwner(session, id);
        return profileService.updateEmbeddings(id, toVectors(body.embeddings()), body.replace(), body.avatar());
    }

    @RequireSession
    @Operation(summary = "Change l'avatar du profil connecte")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avatar mis a jour"),
            @ApiResponse(responseCode = "400", description = "Avatar invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Profil d'un autre utilisateur", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PutMapping("/profiles/{id}/avatar")
    public ProfileService.ProfileView updateAvatar(@PathVariable long id, AuthenticatedSession session,
                                                   @Valid @RequestBody UpdateAvatarRequest body) {
        requireOwner(session, id);
        return profileService.updateAvatar(id, body.avatar());
    }

    @RequireSession
    @Operation(summary = "Supprime le profil connecte et toutes ses donnees")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Profil supprime"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Profil d'un autre utilisateur", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @DeleteMapping("/profiles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, AuthenticatedSession session) {
        requireOwner(session, id);
        profileService.delete(id);
    }

    private static void requireOwner(AuthenticatedSession session, long profileId) {
        if (session.profileId() != profileId) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Acces refuse : ce profil n'est pas le votre.");
        }
    }

    private static List<float[]> toVectors(List<List<Double>> embeddings) {
        return embeddings.stream().map(EmbeddingCodec::fromList).toList();
    }

    public record CreateProfileRequest(
            @NotBlank @Size(max = 60) String name,
            @NotNull @Size(min = 1, max = 30) List<@NotNull List<Double>> embeddings,
            @Size(max = ProfileService.MAX_AVATAR_LENGTH) String avatar) {
    }

    public record UpdateEmbeddingsRequest(
            @NotNull @Size(min = 1, max = 30) List<@NotNull List<Double>> embeddings,
            boolean replace,
            @Size(max = ProfileService.MAX_AVATAR_LENGTH) String avatar) {
    }

    public record UpdateAvatarRequest(
            @NotBlank @Size(max = ProfileService.MAX_AVATAR_LENGTH) String avatar) {
    }
}
