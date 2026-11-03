package com.assistant.auth;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.common.ApiExceptionHandler;
import com.assistant.config.AssistantProperties;
import com.assistant.kiosk.KioskKeyInterceptor;
import com.assistant.qr.QrUrlResolver;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Emission du QR par le Pi puis echange du jeton par le smartphone. */
class HandoffControllerTest {

    private static final String KEY = "secret";
    private static final String BASE = "http://192.168.1.50:8080";

    SessionService sessions = mock(SessionService.class);
    HandoffService handoff;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        setUp(Fixtures.withQrAndKioskKey(BASE, KEY));
    }

    private void setUp(AssistantProperties properties) {
        handoff = new HandoffService(properties, new TestClock());
        mvc = MockMvcBuilders.standaloneSetup(
                        new HandoffController(handoff, new QrUrlResolver(properties)),
                        new HandoffPageController(handoff, sessions))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new KioskKeyInterceptor(properties), new SessionInterceptor(sessions))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private void piSessionOf(long profileId, SessionScope scope) {
        when(sessions.authenticate("pi")).thenReturn(
                Optional.of(new AuthenticatedSession("h", profileId, 1L, scope)));
    }

    @Test
    void issuingNeedsTheKioskKeyAndASession() throws Exception {
        mvc.perform(post("/api/session/handoff").cookie(new Cookie(SessionCookies.NAME, "pi")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/session/handoff").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void theQrIsBoundToTheProfileOfThePiSessionAndBuiltFromThePublicUrl() throws Exception {
        piSessionOf(5L, SessionScope.FULL);

        String body = mvc.perform(post("/api/session/handoff").param("scope", "WEIGHT")
                        .header(KioskKeyInterceptor.HEADER, KEY).cookie(new Cookie(SessionCookies.NAME, "pi")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url", containsString(BASE + "/handoff?t=")))
                .andExpect(jsonPath("$.expiresInSeconds").value(120))
                .andExpect(jsonPath("$.qrId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.<String>read(body, "$.url").substring((BASE + "/handoff?t=").length());
        org.assertj.core.api.Assertions.assertThat(handoff.redeem(token)).hasValueSatisfying(r -> {
            org.assertj.core.api.Assertions.assertThat(r.profileId()).isEqualTo(5L);
            org.assertj.core.api.Assertions.assertThat(r.scope()).isEqualTo(SessionScope.WEIGHT);
        });
    }

    @Test
    void aSmartphoneSessionCannotIssueQrCodes() throws Exception {
        when(sessions.authenticate("phone")).thenReturn(
                Optional.of(new AuthenticatedSession("h", 5L, 1L, SessionScope.WEIGHT)));
        mvc.perform(post("/api/session/handoff").header(KioskKeyInterceptor.HEADER, KEY)
                        .cookie(new Cookie(SessionCookies.NAME, "phone")))
                .andExpect(status().isForbidden());
    }

    @Test
    void issuingIsUnavailableWhenQrCodesAreDisabled() throws Exception {
        setUp(Fixtures.withQrAndKioskKey("", KEY));
        piSessionOf(5L, SessionScope.FULL);
        mvc.perform(post("/api/session/handoff").header(KioskKeyInterceptor.HEADER, KEY)
                        .cookie(new Cookie(SessionCookies.NAME, "pi")))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void scanningAFullTokenOpensATransientSessionAndRedirectsToTheMenu() throws Exception {
        HandoffService.IssuedQr qr = handoff.issue(5L, SessionScope.FULL);
        when(sessions.openTransientSession(5L, SessionScope.FULL, null))
                .thenReturn(new SessionService.IssuedSession("phone", 5L, "Alice", 1L));
        when(sessions.transientDurationSeconds(SessionScope.FULL)).thenReturn(1800L);

        mvc.perform(get("/handoff").param("t", qr.token()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/mobile"))
                .andExpect(header().string("Set-Cookie", containsString(SessionCookies.NAME + "=phone")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=1800")))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
    }

    @Test
    void scanningAWeightTokenRedirectsToTheWeightPage() throws Exception {
        HandoffService.IssuedQr qr = handoff.issue(5L, SessionScope.WEIGHT);
        when(sessions.openTransientSession(5L, SessionScope.WEIGHT, "old"))
                .thenReturn(new SessionService.IssuedSession("phone", 5L, "Alice", 1L));
        when(sessions.transientDurationSeconds(SessionScope.WEIGHT)).thenReturn(600L);

        mvc.perform(get("/handoff").param("t", qr.token()).cookie(new Cookie(SessionCookies.NAME, "old")))
                .andExpect(redirectedUrl("/mobile/weight"));
    }

    @Test
    void aTokenWorksOnlyOnce() throws Exception {
        HandoffService.IssuedQr qr = handoff.issue(5L, SessionScope.FULL);
        when(sessions.openTransientSession(5L, SessionScope.FULL, null))
                .thenReturn(new SessionService.IssuedSession("phone", 5L, "Alice", 1L));
        when(sessions.transientDurationSeconds(SessionScope.FULL)).thenReturn(1800L);

        mvc.perform(get("/handoff").param("t", qr.token())).andExpect(status().is3xxRedirection());
        mvc.perform(get("/handoff").param("t", qr.token()))
                .andExpect(status().isGone())
                .andExpect(view().name("handoff-expired"))
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    void unknownOrMissingTokensGetTheExpiredPage() throws Exception {
        mvc.perform(get("/handoff").param("t", "nope")).andExpect(status().isGone());
        mvc.perform(get("/handoff")).andExpect(status().isGone());
    }
}
