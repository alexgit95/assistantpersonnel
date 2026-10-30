package com.assistant.auth;

import com.assistant.common.ApiException;
import com.assistant.kiosk.KioskKeyInterceptor;
import com.assistant.kiosk.KioskOnly;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/session")
@Tag(name = "Transfert de session", description = "Jetons de QR a usage unique pour ouvrir une session sur un smartphone")
public class HandoffController {

    private final HandoffService handoff;
    private final QrUrlResolver qrUrls;

    public HandoffController(HandoffService handoff, QrUrlResolver qrUrls) {
        this.handoff = handoff;
        this.qrUrls = qrUrls;
    }

    @KioskOnly
    @RequireSession
    @Parameter(name = KioskKeyInterceptor.HEADER, in = ParameterIn.HEADER,
            description = "Cle de kiosque (appel reserve au navigateur du Pi)")
    @Operation(summary = "Emet un QR de transfert pour le profil connecte sur le Pi",
            description = "Jeton a usage unique lie au profil de la session du Pi (jamais fourni par le client). "
                    + "FULL ouvre le menu mobile complet, WEIGHT limite le smartphone a la saisie du poids.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "QR emis"),
            @ApiResponse(responseCode = "401", description = "Session absente ou expiree", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "403", description = "Cle de kiosque absente ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "429", description = "Trop de QR actifs", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "503", description = "QR desactives (URL publique absente)", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @PostMapping("/handoff")
    public HandoffResponse issue(@RequestParam(defaultValue = "FULL") SessionScope scope,
                                 AuthenticatedSession session, HttpServletRequest request) {
        if (session.scope() != SessionScope.FULL) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette session ne peut pas emettre de QR.");
        }
        String base = qrUrls.baseUrl(request).orElseThrow(() -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE,
                "QR desactives : renseignez ASSISTANT_PUBLIC_BASE_URL."));
        HandoffService.IssuedQr qr = handoff.issue(session.profileId(), scope);
        return new HandoffResponse(qr.qrId(), base + "/handoff?t=" + qr.token(), handoff.tokenTtlSeconds());
    }

    public record HandoffResponse(String qrId, String url, long expiresInSeconds) {
    }
}
