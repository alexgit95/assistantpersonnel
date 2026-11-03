package com.assistant.auth;

import com.assistant.common.ApiException;
import com.assistant.guest.GuestService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Resout la session faciale des endpoints annotes @RequireSession, ou l'invite sur les lectures @AllowGuest. */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;
    private final GuestService guests;

    @Autowired
    public SessionInterceptor(SessionService sessionService, GuestService guests) {
        this.sessionService = sessionService;
        this.guests = guests;
    }

    public SessionInterceptor(SessionService sessionService) {
        this(sessionService, null);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !requiresSession(method)) {
            return true;
        }
        Optional<AuthenticatedSession> session = sessionService.authenticate(SessionCookies.read(request));
        if (session.isEmpty() && allowsGuest(method) && guests != null) {
            session = guests.guestProfileId().map(AuthenticatedSession::guest);
            session.ifPresent(ignored -> response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store"));
        }
        request.setAttribute(AuthenticatedSession.REQUEST_ATTRIBUTE, session
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session absente ou expiree.")));
        session.filter(s -> s.scope() == SessionScope.WEIGHT && !weightAllows(request)).ifPresent(s -> {
            throw new ApiException(HttpStatus.FORBIDDEN, "Cette session est limitee a la saisie du poids.");
        });
        return true;
    }

    /** Une session WEIGHT n'ouvre que le poids et sa propre identite (liste blanche, tout le reste est refuse). */
    private static boolean weightAllows(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return underPath(path, "/api/weight") || underPath(path, "/mobile/weight")
                || path.equals("/api/auth/session") || path.equals("/api/auth/logout");
    }

    private static boolean underPath(String path, String prefix) {
        return path.equals(prefix) || path.startsWith(prefix + "/");
    }

    private static boolean requiresSession(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequireSession.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireSession.class);
    }

    private static boolean allowsGuest(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), AllowGuest.class);
    }
}
