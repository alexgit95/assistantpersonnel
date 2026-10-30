package com.assistant.auth;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Page ouverte par le scan d'un QR : echange le jeton contre une session transitoire puis redirige. */
@Controller
@Hidden
public class HandoffPageController {

    private final HandoffService handoff;
    private final SessionService sessionService;

    public HandoffPageController(HandoffService handoff, SessionService sessionService) {
        this.handoff = handoff;
        this.sessionService = sessionService;
    }

    @GetMapping("/handoff")
    public String redeem(@RequestParam(name = "t", required = false) String token,
                         HttpServletRequest request, HttpServletResponse response) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        Optional<HandoffService.Redeemed> redeemed = handoff.redeem(token);
        if (redeemed.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_GONE);
            return "handoff-expired";
        }
        SessionScope scope = redeemed.get().scope();
        SessionService.IssuedSession issued = sessionService.openTransientSession(
                redeemed.get().profileId(), scope, SessionCookies.read(request));
        response.addHeader(HttpHeaders.SET_COOKIE,
                SessionCookies.build(request, issued.token(), sessionService.transientDurationSeconds(scope)));
        return "redirect:" + (scope == SessionScope.WEIGHT ? "/mobile/weight" : "/mobile");
    }
}
