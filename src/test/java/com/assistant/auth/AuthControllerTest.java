package com.assistant.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.common.ApiException;
import com.assistant.common.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    SessionService sessionService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AuthController(sessionService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new SessionInterceptor(sessionService))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private static Cookie cookie(String token) {
        return new Cookie(SessionCookies.NAME, token);
    }

    @Test
    void loginOpensASessionAndSetsAHardenedCookie() throws Exception {
        when(sessionService.login(5L, null))
                .thenReturn(new SessionService.IssuedSession("tok", 5L, "Alice", 1_000L));
        when(sessionService.durationSeconds()).thenReturn(600L);
        when(sessionService.remainingSeconds(1_000L)).thenReturn(600L);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(5))
                .andExpect(jsonPath("$.profileName").value("Alice"))
                .andExpect(jsonPath("$.remainingSeconds").value(600))
                .andExpect(header().string("Set-Cookie", containsString("ASSISTANT_SESSION=tok")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=600")));
    }

    @Test
    void loginReplacesThePreviousSessionCookie() throws Exception {
        when(sessionService.login(6L, "old"))
                .thenReturn(new SessionService.IssuedSession("new", 6L, "Bob", 1_000L));

        mvc.perform(post("/api/auth/login").cookie(cookie("old")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":6}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("ASSISTANT_SESSION=new")));
    }

    @Test
    void loginWithoutProfileIdIsABadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginForUnknownProfileIsNotFound() throws Exception {
        when(sessionService.login(9L, null)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable."));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"profileId\":9}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Profil introuvable."));
    }

    @Test
    void heartbeatWithoutSessionIsUnauthorized() throws Exception {
        when(sessionService.authenticate(null)).thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/heartbeat")).andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatWithExpiredSessionIsUnauthorized() throws Exception {
        when(sessionService.authenticate("tok")).thenReturn(Optional.empty());
        mvc.perform(post("/api/auth/heartbeat").cookie(cookie("tok"))).andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatExtendsTheSessionAndRefreshesTheCookie() throws Exception {
        when(sessionService.authenticate("tok"))
                .thenReturn(Optional.of(new AuthenticatedSession("h", 5L, 500L)));
        when(sessionService.heartbeat("tok"))
                .thenReturn(Optional.of(new SessionService.IssuedSession("tok", 5L, "Alice", 2_000L)));
        when(sessionService.durationSeconds()).thenReturn(600L);
        when(sessionService.remainingSeconds(2_000L)).thenReturn(600L);

        mvc.perform(post("/api/auth/heartbeat").cookie(cookie("tok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.expiresAtMs").value(2_000))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=600")));
    }

    @Test
    void currentSessionReturnsTheProfileOfTheCookie() throws Exception {
        when(sessionService.authenticate("tok"))
                .thenReturn(Optional.of(new AuthenticatedSession("h", 5L, 500L)));
        when(sessionService.profileName(5L)).thenReturn("Alice");
        when(sessionService.remainingSeconds(500L)).thenReturn(42L);

        mvc.perform(get("/api/auth/session").cookie(cookie("tok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.profileId").value(5))
                .andExpect(jsonPath("$.remainingSeconds").value(42));
    }

    @Test
    void currentSessionWithoutCookieIsUnauthorized() throws Exception {
        when(sessionService.authenticate(any())).thenReturn(Optional.empty());
        mvc.perform(get("/api/auth/session")).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsTheCookieAndIsIdempotent() throws Exception {
        mvc.perform(post("/api/auth/logout").cookie(cookie("tok")))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")));
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent());
    }
}
