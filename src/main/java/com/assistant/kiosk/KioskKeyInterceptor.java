package com.assistant.kiosk;

import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Refuse (403) les endpoints {@link KioskOnly} sans la cle de kiosque ; cle vide = controle desactive (local). */
@Component
public class KioskKeyInterceptor implements HandlerInterceptor {

    public static final String HEADER = KioskAccess.HEADER;

    private final KioskAccess access;

    public KioskKeyInterceptor(AssistantProperties properties) {
        this.access = new KioskAccess(properties);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method) || !kioskOnly(method)) {
            return true;
        }
        if (!access.isAuthorized(request)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Appel reserve au kiosque.");
        }
        return true;
    }

    private static boolean kioskOnly(HandlerMethod method) {
        return AnnotatedElementUtils.hasAnnotation(method.getMethod(), KioskOnly.class)
                || AnnotatedElementUtils.hasAnnotation(method.getBeanType(), KioskOnly.class);
    }
}
