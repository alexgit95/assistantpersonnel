package com.assistant.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;

public final class SessionCookies {

    public static final String NAME = "ASSISTANT_SESSION";

    private SessionCookies() {
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public static String build(HttpServletRequest request, String token, long maxAgeSeconds) {
        return ResponseCookie.from(NAME, token)
                .httpOnly(true)
                .secure(request.isSecure())
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAgeSeconds)
                .build()
                .toString();
    }

    public static String clear(HttpServletRequest request) {
        return build(request, "", 0);
    }
}
