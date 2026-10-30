package com.assistant.kiosk;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.Fixtures;
import com.assistant.auth.AuthController;
import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionInterceptor;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Les appels reserves au Pi (login, heartbeat) exigent la cle de kiosque configuree. */
@ExtendWith(MockitoExtension.class)
class AuthKioskAccessTest {

    private static final String KEY = "secret";

    @Mock
    SessionService sessionService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(sessionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new KioskKeyInterceptor(Fixtures.withKioskKey(KEY)),
                        new SessionInterceptor(sessionService))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private static final String LOGIN_BODY = "{\"profileId\":5}";

    @Test
    void loginWithoutKeyIsForbiddenAndOpensNoSession() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginWithWrongKeyIsForbidden() throws Exception {
        mvc.perform(post("/api/auth/login").header(KioskKeyInterceptor.HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessionService);
    }

    @Test
    void loginWithKeyOpensTheSession() throws Exception {
        when(sessionService.login(5L, null))
                .thenReturn(new SessionService.IssuedSession("tok", 5L, "Alice", 1_000L));
        when(sessionService.durationSeconds()).thenReturn(600L);
        when(sessionService.remainingSeconds(1_000L)).thenReturn(600L);

        mvc.perform(post("/api/auth/login").header(KioskKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(LOGIN_BODY))
                .andExpect(status().isOk());
    }

    @Test
    void heartbeatWithoutKeyIsForbiddenEvenWithAValidSession() throws Exception {
        mvc.perform(post("/api/auth/heartbeat").cookie(new Cookie(SessionCookies.NAME, "tok")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(sessionService);
    }

    @Test
    void heartbeatWithKeyExtendsTheSession() throws Exception {
        when(sessionService.authenticate("tok"))
                .thenReturn(Optional.of(new AuthenticatedSession("h", 5L, 500L)));
        when(sessionService.heartbeat("tok"))
                .thenReturn(Optional.of(new SessionService.IssuedSession("tok", 5L, "Alice", 2_000L)));
        when(sessionService.durationSeconds()).thenReturn(600L);
        when(sessionService.remainingSeconds(2_000L)).thenReturn(600L);

        mvc.perform(post("/api/auth/heartbeat").header(KioskKeyInterceptor.HEADER, KEY)
                        .cookie(new Cookie(SessionCookies.NAME, "tok")))
                .andExpect(status().isOk());
    }

    @Test
    void logoutStaysOpenToEveryClient() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }
}
