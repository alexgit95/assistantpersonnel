package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import com.assistant.guest.GuestService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

/** Une session WEIGHT n'ouvre que le poids et sa propre identite : liste blanche, tout le reste est refuse (403). */
class SessionInterceptorScopeTest {

    private final SessionService sessions = mock(SessionService.class);
    private final SessionInterceptor interceptor = new SessionInterceptor(sessions, mock(GuestService.class));
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @RequireSession
    static class Sample {
        public void anything() {
        }
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookies.NAME, "tok"));
        return request;
    }

    private static HandlerMethod handler() throws NoSuchMethodException {
        return new HandlerMethod(new Sample(), Sample.class.getMethod("anything"));
    }

    private void signedInWith(SessionScope scope) {
        when(sessions.authenticate("tok")).thenReturn(
                Optional.of(new AuthenticatedSession("h", 5L, 1L, scope)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/weight", "/api/weight/12", "/mobile/weight", "/mobile/weight/ajout",
            "/api/auth/session", "/api/auth/logout"})
    void weightSessionsReachTheWeightRoutesAndTheirOwnIdentity(String path) throws Exception {
        signedInWith(SessionScope.WEIGHT);
        assertThatCode(() -> interceptor.preHandle(request(path), response, handler())).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/apps", "/configuration", "/api/profiles/me", "/api/auth/heartbeat", "/mobile",
            "/api/weightx", "/mobile/weightx", "/api/session/handoff", "/api/enrollment-sessions/recapture"})
    void weightSessionsAreRefusedEverywhereElse(String path) {
        signedInWith(SessionScope.WEIGHT);
        assertThatThrownBy(() -> interceptor.preHandle(request(path), response, handler()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/apps", "/configuration", "/api/profiles/me", "/mobile", "/api/weight"})
    void fullSessionsAreNotRestricted(String path) throws Exception {
        signedInWith(SessionScope.FULL);
        assertThatCode(() -> interceptor.preHandle(request(path), response, handler())).doesNotThrowAnyException();
    }

    @Test
    void theContextPathIsIgnoredWhenCheckingTheWhitelist() throws Exception {
        signedInWith(SessionScope.WEIGHT);
        MockHttpServletRequest request = request("/assistant/api/weight");
        request.setContextPath("/assistant");
        assertThatCode(() -> interceptor.preHandle(request, response, handler())).doesNotThrowAnyException();
    }

    @Test
    void noSessionStillMeansUnauthorizedBeforeAnyScopeCheck() {
        when(sessions.authenticate(any())).thenReturn(Optional.empty());
        assertThatThrownBy(() -> interceptor.preHandle(request("/api/weight"), response, handler()))
                .isInstanceOfSatisfying(ApiException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
