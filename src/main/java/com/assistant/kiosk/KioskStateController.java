package com.assistant.kiosk;

import com.assistant.auth.HandoffService;
import com.assistant.enrollment.EnrollmentSessionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Clock;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/kiosk")
@KioskOnly
@Tag(name = "Kiosque", description = "Etat interroge par l'ecran du Raspberry Pi (QR affiches, capture a distance)")
public class KioskStateController {

    private static final int MAX_QR_IDS = 10;

    private final HandoffService handoff;
    private final EnrollmentSessionService enrollment;
    private final Clock clock;

    public KioskStateController(HandoffService handoff, EnrollmentSessionService enrollment, Clock clock) {
        this.handoff = handoff;
        this.enrollment = enrollment;
        this.clock = clock;
    }

    @Parameter(name = KioskKeyInterceptor.HEADER, in = ParameterIn.HEADER,
            description = "Cle de kiosque (appel reserve au navigateur du Pi)")
    @Operation(summary = "Etat des QR affiches et de la session de capture courante",
            description = "qr : identifiants des QR affiches (virgules) ; la reponse donne ACTIVE, CONSUMED ou EXPIRED pour chacun, "
                    + "afin que le Pi regenere un QR consomme. capture : session de capture courante ou null (une recapture "
                    + "demandee depuis un smartphone y apparait).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Etat courant"),
            @ApiResponse(responseCode = "403", description = "Cle de kiosque absente ou invalide", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "500", description = "Erreur interne", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))})
    @GetMapping("/state")
    public ResponseEntity<KioskState> state(@RequestParam(name = "qr", required = false) List<String> qr) {
        List<QrStatus> statuses = (qr == null ? List.<String>of() : qr).stream()
                .filter(id -> !id.isBlank()).limit(MAX_QR_IDS)
                .map(id -> new QrStatus(id, handoff.state(id))).toList();
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new KioskState(clock.millis(), statuses, enrollment.kioskView().orElse(null)));
    }

    public record QrStatus(String qrId, HandoffService.QrState state) {
    }

    public record KioskState(long serverNowMs, List<QrStatus> qr, EnrollmentSessionService.Snapshot capture) {
    }
}
