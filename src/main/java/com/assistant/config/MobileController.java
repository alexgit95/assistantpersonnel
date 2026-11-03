package com.assistant.config;

import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** Pages du smartphone : de simples coquilles, les donnees passent par l'API (401/403 traites par le script). */
@Controller
@Hidden
public class MobileController {

    @GetMapping("/mobile")
    public String menu(HttpServletResponse response) {
        return page(response, "mobile");
    }

    @GetMapping("/mobile/enroll")
    public String enroll(HttpServletResponse response) {
        return page(response, "mobile-enroll");
    }

    @GetMapping("/mobile/weight")
    public String weight(HttpServletResponse response) {
        return page(response, "mobile-weight");
    }

    // L'URL du QR invite porte un jeton : ni cache ni referent.
    private static String page(HttpServletResponse response, String template) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        return template;
    }
}
