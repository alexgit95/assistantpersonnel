package com.assistant.auth;

import com.assistant.common.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentification", description = "Session faciale glissante (cookie HttpOnly)")
public class AuthController {

    private final SessionService sessionService;

    public AuthController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Operation(summary = "Ouvre une session pour le profil reconnu par le navigateur",
            description = "Le navigateur a identifie le visage localement ; seul l'identifiant du profil est transmis. "
                    + "Une session existante de l'appareil est remplacee (bascule de profil).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session ouverte"),
            @ApiResponse(responseCode = "400", description = "Requete invalide", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Profil introuvable", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))})
    @PostMapping("/login")
    public SessionResponse login(@Valid @RequestBody LoginRequest body, HttpServletRequest request,
                                 HttpServletResponse response) {
        SessionService.IssuedSession issued = sessionService.login(body.profileId(), SessionCookies.read(request));
        return respond(issued, request, response);
    }

    @RequireSession
    @Operation(summary = "Prolonge la session du profil courant",
            description = "A appeler quand le visage du profil connecte est revu ; repousse l'expiration de la duree configuree.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session prolongee"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))})
    @PostMapping("/heartbeat")
    public SessionResponse heartbeat(HttpServletRequest request, HttpServletResponse response) {
        SessionService.IssuedSession issued = sessionService.heartbeat(SessionCookies.read(request))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session absente ou expiree."));
        return respond(issued, request, response);
    }

    @RequireSession
    @Operation(summary = "Etat de la session courante")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session valide"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))})
    @GetMapping("/session")
    public SessionResponse current(AuthenticatedSession session, HttpServletRequest request) {
        String name = sessionService.profileName(session.profileId());
        return new SessionResponse(session.profileId(), name, session.expiresAtMs(),
                sessionService.remainingSeconds(session.expiresAtMs()));
    }

    @Operation(summary = "Deconnexion", description = "Supprime la session cote serveur et le cookie ; idempotent.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session fermee"),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = org.springframework.http.ProblemDetail.class)))})
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        sessionService.logout(SessionCookies.read(request));
        response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.clear(request));
    }

    private SessionResponse respond(SessionService.IssuedSession issued, HttpServletRequest request,
                                    HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                SessionCookies.build(request, issued.token(), sessionService.durationSeconds()));
        return new SessionResponse(issued.profileId(), issued.profileName(), issued.expiresAtMs(),
                sessionService.remainingSeconds(issued.expiresAtMs()));
    }

    public record LoginRequest(@NotNull Long profileId) {
    }

    public record SessionResponse(long profileId, String profileName, long expiresAtMs, long remainingSeconds) {
    }
}
