package com.assistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import com.assistant.guest.GuestService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

class SessionInterceptorGuestTest {

    private final SessionService sessions = mock(SessionService.class);
    private final GuestService guests = mock(GuestService.class);
    private final SessionInterceptor interceptor = new SessionInterceptor(sessions, guests);
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();

    @RequireSession
    static class Sample {
        @AllowGuest
        public void read() {
        }

        public void write() {
        }
    }

    private static HandlerMethod handler(String method) throws NoSuchMethodException {
        return new HandlerMethod(new Sample(), Sample.class.getMethod(method));
    }

    private AuthenticatedSession resolved() {
        return (AuthenticatedSession) request.getAttribute(AuthenticatedSession.REQUEST_ATTRIBUTE);
    }

    @Test
    void resolvesTheGuestWithNoStoreOnAGuestReadWithoutSession() throws Exception {
        when(sessions.authenticate(any())).thenReturn(Optional.empty());
        when(guests.guestProfileId()).thenReturn(Optional.of(5L));

        assertThat(interceptor.preHandle(request, response, handler("read"))).isTrue();

        assertThat(resolved().isGuest()).isTrue();
        assertThat(resolved().profileId()).isEqualTo(5L);
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
    }

    @Test
    void anExpiredOrUnknownCookieIsTreatedAsNoSession() throws Exception {
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookies.NAME, "expired"));
        when(sessions.authenticate("expired")).thenReturn(Optional.empty());
        when(guests.guestProfileId()).thenReturn(Optional.of(5L));

        assertThat(interceptor.preHandle(request, response, handler("read"))).isTrue();

        assertThat(resolved().isGuest()).isTrue();
    }

    @Test
    void aValidSessionAlwaysWinsOverTheGuest() throws Exception {
        request.setCookies(new jakarta.servlet.http.Cookie(SessionCookies.NAME, "tok"));
        when(sessions.authenticate("tok")).thenReturn(Optional.of(new AuthenticatedSession("h", 4L, 1L)));

        interceptor.preHandle(request, response, handler("read"));

        assertThat(resolved().isGuest()).isFalse();
        assertThat(resolved().profileId()).isEqualTo(4L);
        assertThat(response.getHeader("Cache-Control")).isNull();
        verifyNoInteractions(guests);
    }

    @Test
    void otherEndpointsStayUnauthorizedWithoutSession() throws Exception {
        when(sessions.authenticate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("write")))
                .isInstanceOfSatisfying(ApiException.class,
                        exception -> assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verifyNoInteractions(guests);
    }

    @Test
    void isUnauthorizedWhenNoGuestExists() throws Exception {
        when(sessions.authenticate(any())).thenReturn(Optional.empty());
        when(guests.guestProfileId()).thenReturn(Optional.empty());

        assertThatThrownBy(() -> interceptor.preHandle(request, response, handler("read")))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void theGuestSessionIsMarkedAndCarriesNoRealSessionId() {
        assertThat(AuthenticatedSession.guest(7L).isGuest()).isTrue();
        assertThat(new AuthenticatedSession("abc", 7L, 1L).isGuest()).isFalse();
    }
}
