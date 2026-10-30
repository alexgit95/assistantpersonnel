package com.assistant.enrollment;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.RequireSession;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionScope;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiException;
import com.assistant.kiosk.KioskAccess;
import com.assistant.kiosk.KioskKeyInterceptor;
import com.assistant.kiosk.KioskOnly;
import com.assistant.profile.EmbeddingCodec;
import com.assistant.profile.ProfileService;
import com.assistant.qr.QrUrlResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/enrollment-sessions")
@Tag(name = "Capture a distance", description = "Enrolement ou recapture : le smartphone pilote, la camera du Pi prend les photos")
public class EnrollmentSessionController {

    static final String PAIRING_HEADER = "X-Pairing-Token";
    static final String CLAIM_HEADER = "X-Claim-Token";

    private final EnrollmentSessionService enrollment;
    private final SessionService sessionService;
    private final QrUrlResolver qrUrls;
    private final KioskAccess kioskAccess;

    public EnrollmentSessionController(EnrollmentSessionService enrollment, SessionService sessionService,
                                       QrUrlResolver qrUrls, KioskAccess kioskAccess) {
        this.enrollment = enrollment;
        this.sessionService = sessionService;
        this.qrUrls = qrUrls;
        this.kioskAccess = kioskAccess;
    }

    @KioskOnly
    @Parameter(name = KioskKeyInterceptor.HEADER, in = ParameterIn.HEADER,
            description = "Cle de kiosque (appel reserve au navigateur du Pi)")
    @Operation(summary = "Cree ou reprend la session de capture (Pi)",
            description = "Renvoie l'identifiant, le jeton du QR invite (absent pour une recapture) et l'URL complete du QR.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session courante"),
            @ApiResponse(responseCode = "403", description = "Cle de kiosque absente ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping
    public OpenedResponse open(HttpServletRequest request) {
        EnrollmentSessionService.Opened opened = enrollment.openOrResume();
        String qrUrl = opened.pairingToken() == null ? null
                : qrUrls.resolve(request, "/mobile?s=" + opened.pairingToken()).orElse(null);
        return new OpenedResponse(opened.id(), opened.mode(), opened.state(), opened.pairingToken(), qrUrl);
    }

    @RequireSession
    @Operation(summary = "Demande la recapture du profil de la session (smartphone)",
            description = "Cree une session RECAPTURE liee au profil de la session ; la capture demarre ensuite par /start.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session de recapture creee"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Session limitee a la saisie du poids", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Une capture est deja en cours", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/recapture")
    public EnrollmentSessionService.Recapture recapture(AuthenticatedSession session) {
        if (session.scope() != SessionScope.FULL) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette session est limitee a la saisie du poids.");
        }
        return enrollment.createRecapture(session.profileId());
    }

    @Operation(summary = "Retrouve la session en attente grace au jeton du QR invite (smartphone)",
            description = "Le QR ne contient que le jeton d'appairage ; cet appel renvoie l'identifiant de la session et son etat.")
    @Parameter(name = PAIRING_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Jeton d'appairage lu dans le QR")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session en attente"),
            @ApiResponse(responseCode = "404", description = "Jeton inconnu, session terminee ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/current")
    public ResponseEntity<EnrollmentSessionService.Snapshot> byPairing(
            @RequestHeader(value = PAIRING_HEADER, required = false) String pairingToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(enrollment.findByPairing(pairingToken));
    }

    @Operation(summary = "Etat de la session de capture (smartphone)",
            description = "Authentifie par X-Pairing-Token (QR) ou X-Claim-Token (smartphone qui a lance la capture).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Etat courant"),
            @ApiResponse(responseCode = "403", description = "Jeton absent ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/{id}/state")
    public ResponseEntity<EnrollmentSessionService.Snapshot> state(
            @PathVariable String id,
            @RequestHeader(value = PAIRING_HEADER, required = false) String pairingToken,
            @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(enrollment.state(id, pairingToken, claimToken));
    }

    @Operation(summary = "Lance la capture (smartphone)",
            description = "WAITING vers COUNTDOWN ; nom valide exige en creation. Le premier smartphone recoit un claimToken.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Capture lancee"),
            @ApiResponse(responseCode = "400", description = "Nom invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Jeton absent ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Enrolement deja en cours", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/{id}/start")
    public StartResponse start(@PathVariable String id,
                               @RequestHeader(value = PAIRING_HEADER, required = false) String pairingToken,
                               @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken,
                               @RequestBody(required = false) StartRequest body) {
        String name = body == null ? null : body.name();
        return new StartResponse(enrollment.start(id, pairingToken, claimToken, name));
    }

    @KioskOnly
    @Parameter(name = KioskKeyInterceptor.HEADER, in = ParameterIn.HEADER,
            description = "Cle de kiosque (appel reserve au navigateur du Pi)")
    @Operation(summary = "Depose une capture ou un rejet (Pi)",
            description = "Empreinte calculee et miniature d'avatar d'une capture valide, ou motif d'un rejet. "
                    + "En recapture, le profil connecte sur le Pi doit etre celui de la session.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Etat mis a jour"),
            @ApiResponse(responseCode = "400", description = "Capture invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Cle de kiosque invalide ou profil du Pi different", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Aucune capture attendue", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/{id}/captures")
    public EnrollmentSessionService.Snapshot capture(@PathVariable String id, @Valid @RequestBody CaptureRequest body,
                                                      HttpServletRequest request) {
        boolean accepted = body.status() == CaptureStatus.ACCEPTED;
        float[] embedding = accepted ? EmbeddingCodec.fromList(body.embedding()) : null;
        Long piProfileId = sessionService.authenticate(SessionCookies.read(request))
                .map(AuthenticatedSession::profileId).orElse(null);
        return enrollment.submit(id, new EnrollmentSessionService.Submission(accepted, body.reason(), embedding,
                body.avatar()), piProfileId);
    }

    @Operation(summary = "Miniatures d'avatar de la revue (smartphone)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Miniatures des captures valides"),
            @ApiResponse(responseCode = "403", description = "Jeton de prise en main absent ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La session n'est pas en revue", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/{id}/avatars")
    public ResponseEntity<AvatarsResponse> avatars(@PathVariable String id,
                                                   @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new AvatarsResponse(enrollment.avatars(id, claimToken)));
    }

    @Operation(summary = "Efface les captures et relance le compte a rebours (smartphone)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Capture relancee"),
            @ApiResponse(responseCode = "403", description = "Jeton de prise en main absent ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La session n'est pas en revue", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restart(@PathVariable String id,
                        @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken) {
        enrollment.restart(id, claimToken);
    }

    @Operation(summary = "Valide la revue : cree le profil ou met a jour ses empreintes (smartphone)",
            description = "En creation, ouvre aussi une session d'acces complet pour le smartphone (cookie).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Profil cree ou mis a jour"),
            @ApiResponse(responseCode = "400", description = "Avatar ou captures invalides", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Jeton de prise en main absent ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Visage deja enregistre, limite de profils ou session pas en revue", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/{id}/confirm")
    public ProfileService.ProfileView confirm(@PathVariable String id,
                                              @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken,
                                              @Valid @RequestBody ConfirmRequest body,
                                              HttpServletRequest request, HttpServletResponse response) {
        EnrollmentSessionService.Confirmation confirmation = enrollment.confirm(id, claimToken,
                body.avatarIndex(), Boolean.TRUE.equals(body.replace()), SessionCookies.read(request));
        if (confirmation.session() != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, SessionCookies.build(request, confirmation.session().token(),
                    sessionService.transientDurationSeconds(SessionScope.FULL)));
        }
        return confirmation.profile();
    }

    @Operation(summary = "Annule la session de capture (Pi avec la cle de kiosque, ou smartphone avec son jeton)")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Session annulee"),
            @ApiResponse(responseCode = "403", description = "Ni cle de kiosque ni jeton de prise en main valides", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "Session introuvable ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancel(@PathVariable String id,
                       @RequestHeader(value = CLAIM_HEADER, required = false) String claimToken,
                       HttpServletRequest request) {
        enrollment.cancel(id, kioskAccess.isAuthorized(request), claimToken);
    }

    public enum CaptureStatus { ACCEPTED, REJECTED }

    public record OpenedResponse(String id, EnrollmentMode mode, EnrollmentState state, String pairingToken,
                                 String qrUrl) {
    }

    public record StartRequest(@Size(max = 60) String name) {
    }

    public record StartResponse(String claimToken) {
    }

    public record CaptureRequest(
            @NotNull CaptureStatus status,
            @Size(max = 32) String reason,
            @Size(max = EmbeddingCodec.SIZE) List<Double> embedding,
            @Size(max = ProfileService.MAX_AVATAR_LENGTH) String avatar) {
    }

    public record ConfirmRequest(@NotNull Integer avatarIndex, Boolean replace) {
    }

    public record AvatarsResponse(List<String> avatars) {
    }
}
