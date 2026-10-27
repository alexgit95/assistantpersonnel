package com.assistant.auth;

import com.assistant.common.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Resout la session faciale des endpoints annotes @RequireSession. */
@Component
public class SessionInterceptor implements HandlerInterceptor {

    private final SessionService sessionService;

    public SessionInterceptor(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !requiresSession(method)) {
            return true;
        }
        AuthenticatedSession session = sessionService.authenticate(SessionCookies.read(request))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Session absente ou expiree."));
        request.setAttribute(AuthenticatedSession.REQUEST_ATTRIBUTE, session);
        return true;
    }

    private static boolean requiresSession(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), RequireSession.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), RequireSession.class);
    }
}
