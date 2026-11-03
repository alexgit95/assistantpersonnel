package com.assistant.enrollment;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionInterceptor;
import com.assistant.auth.SessionScope;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiExceptionHandler;
import com.assistant.config.AssistantProperties;
import com.assistant.kiosk.KioskAccess;
import com.assistant.kiosk.KioskKeyInterceptor;
import com.assistant.profile.ProfileService;
import com.assistant.qr.QrUrlResolver;
import jakarta.servlet.http.Cookie;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Parcours complet de l'API de capture a distance, du QR invite a la creation du profil. */
class EnrollmentSessionControllerTest {

    private static final String KEY = "secret";
    private static final String BASE = "http://192.168.1.50:8080";
    private static final int PHOTOS = 5;

    ProfileService profiles = mock(ProfileService.class);
    SessionService sessions = mock(SessionService.class);
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        AssistantProperties properties = Fixtures.withQrAndKioskKey(BASE, KEY);
        var enrollment = new EnrollmentSessionService(properties, new TestClock(), profiles, sessions);
        mvc = MockMvcBuilders.standaloneSetup(new EnrollmentSessionController(enrollment, sessions,
                        new QrUrlResolver(properties), new KioskAccess(properties)))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new KioskKeyInterceptor(properties), new SessionInterceptor(sessions))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private static String captureBody(int index) {
        String embedding = IntStream.range(0, 128)
                .mapToObj(i -> String.valueOf(index + i * 0.0001)).collect(Collectors.joining(",", "[", "]"));
        return "{\"status\":\"ACCEPTED\",\"embedding\":" + embedding + ",\"avatar\":\"" + Fixtures.avatar() + "\"}";
    }

    private String json(MvcResult result, String field) throws Exception {
        return com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$." + field);
    }

    @Test
    void piOpensTheSessionAndGetsTheQrUrlBuiltByTheServer() throws Exception {
        mvc.perform(post("/api/enrollment-sessions")).andExpect(status().isForbidden());

        MvcResult result = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.mode").value("CREATE"))
                .andExpect(jsonPath("$.qrUrl", startsWith(BASE + "/mobile?s=")))
                .andReturn();

        mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(jsonPath("$.id").value(json(result, "id")))
                .andExpect(jsonPath("$.pairingToken").value(json(result, "pairingToken")));
    }

    @Test
    void theSmartphoneFindsTheSessionFromTheQrTokenOnly() throws Exception {
        MvcResult opened = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andReturn();

        mvc.perform(get("/api/enrollment-sessions/current")).andExpect(status().isNotFound());
        mvc.perform(get("/api/enrollment-sessions/current").header("X-Pairing-Token", "wrong"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/enrollment-sessions/current").header("X-Pairing-Token", json(opened, "pairingToken")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.id").value(json(opened, "id")))
                .andExpect(jsonPath("$.state").value("WAITING"));
    }

    @Test
    void fullEnrollmentFromTheQrToTheCreatedProfile() throws Exception {
        MvcResult opened = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andReturn();
        String id = json(opened, "id");
        String pairing = json(opened, "pairingToken");

        mvc.perform(get("/api/enrollment-sessions/" + id + "/state")).andExpect(status().isForbidden());
        mvc.perform(get("/api/enrollment-sessions/" + id + "/state")
                        .header(EnrollmentSessionController.PAIRING_HEADER, pairing))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.total").value(PHOTOS));

        MvcResult started = mvc.perform(post("/api/enrollment-sessions/" + id + "/start")
                        .header(EnrollmentSessionController.PAIRING_HEADER, pairing)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"))
                .andExpect(status().isOk())
                .andReturn();
        String claim = json(started, "claimToken");

        mvc.perform(post("/api/enrollment-sessions/" + id + "/start")
                        .header(EnrollmentSessionController.PAIRING_HEADER, pairing)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Bob\"}"))
                .andExpect(status().isConflict());

        for (int i = 0; i < PHOTOS; i++) {
            mvc.perform(post("/api/enrollment-sessions/" + id + "/captures")
                            .header(KioskKeyInterceptor.HEADER, KEY)
                            .contentType(MediaType.APPLICATION_JSON).content(captureBody(i)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.done").value(i + 1));
        }

        mvc.perform(get("/api/enrollment-sessions/" + id + "/state")
                        .header(EnrollmentSessionController.CLAIM_HEADER, claim))
                .andExpect(jsonPath("$.state").value("REVIEW"))
                .andExpect(jsonPath("$.name").value("Alice"));
        mvc.perform(get("/api/enrollment-sessions/" + id + "/avatars")
                        .header(EnrollmentSessionController.CLAIM_HEADER, claim))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatars", hasSize(PHOTOS)));

        when(profiles.create(eq("Alice"), anyList(), eq(Fixtures.avatar())))
                .thenReturn(new ProfileService.ProfileView(7L, "Alice", Fixtures.avatar(), PHOTOS));
        when(sessions.openTransientSession(7L, SessionScope.FULL, null))
                .thenReturn(new SessionService.IssuedSession("phone-token", 7L, "Alice", 1L));
        when(sessions.transientDurationSeconds(SessionScope.FULL)).thenReturn(1800L);

        mvc.perform(post("/api/enrollment-sessions/" + id + "/confirm")
                        .header(EnrollmentSessionController.CLAIM_HEADER, claim)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"avatarIndex\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(header().string("Set-Cookie", containsString(SessionCookies.NAME + "=phone-token")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=1800")));
    }

    @Test
    void capturesAreReservedToThePi() throws Exception {
        MvcResult opened = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andReturn();
        String id = json(opened, "id");

        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures")
                        .contentType(MediaType.APPLICATION_JSON).content(captureBody(0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures")
                        .header(KioskKeyInterceptor.HEADER, "wrong")
                        .contentType(MediaType.APPLICATION_JSON).content(captureBody(0)))
                .andExpect(status().isForbidden());
    }

    @Test
    void aMalformedCaptureIsABadRequest() throws Exception {
        MvcResult opened = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andReturn();
        String id = json(opened, "id");
        String pairing = json(opened, "pairingToken");
        mvc.perform(post("/api/enrollment-sessions/" + id + "/start")
                .header(EnrollmentSessionController.PAIRING_HEADER, pairing)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Alice\"}"));

        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures").header(KioskKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACCEPTED\",\"embedding\":[1,2,3],\"avatar\":\"" + Fixtures.avatar() + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures").header(KioskKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void piCancelNeedsTheKeyAndTheSmartphoneNeedsItsClaim() throws Exception {
        MvcResult opened = mvc.perform(post("/api/enrollment-sessions").header(KioskKeyInterceptor.HEADER, KEY))
                .andReturn();
        String id = json(opened, "id");

        mvc.perform(delete("/api/enrollment-sessions/" + id)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/enrollment-sessions/" + id).header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/enrollment-sessions/unknown").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(status().isNotFound());
    }

    @Test
    void recaptureRequiresAFullSession() throws Exception {
        mvc.perform(post("/api/enrollment-sessions/recapture")).andExpect(status().isUnauthorized());

        when(sessions.authenticate("weight")).thenReturn(
                Optional.of(new AuthenticatedSession("h", 5L, 1L, SessionScope.WEIGHT)));
        mvc.perform(post("/api/enrollment-sessions/recapture").cookie(new Cookie(SessionCookies.NAME, "weight")))
                .andExpect(status().isForbidden());

        when(sessions.authenticate("full")).thenReturn(Optional.of(new AuthenticatedSession("h", 5L, 1L)));
        mvc.perform(post("/api/enrollment-sessions/recapture").cookie(new Cookie(SessionCookies.NAME, "full")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claimToken").isNotEmpty());
    }

    @Test
    void recaptureCapturesCheckTheProfileConnectedOnThePi() throws Exception {
        when(sessions.authenticate("full")).thenReturn(Optional.of(new AuthenticatedSession("h", 5L, 1L)));
        when(sessions.authenticate("pi-other")).thenReturn(Optional.of(new AuthenticatedSession("h", 6L, 1L)));
        MvcResult recapture = mvc.perform(post("/api/enrollment-sessions/recapture")
                .cookie(new Cookie(SessionCookies.NAME, "full"))).andReturn();
        String id = json(recapture, "id");
        String claim = json(recapture, "claimToken");
        mvc.perform(post("/api/enrollment-sessions/" + id + "/start")
                .header(EnrollmentSessionController.CLAIM_HEADER, claim)).andExpect(status().isOk());

        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures").header(KioskKeyInterceptor.HEADER, KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(captureBody(0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures").header(KioskKeyInterceptor.HEADER, KEY)
                        .cookie(new Cookie(SessionCookies.NAME, "pi-other"))
                        .contentType(MediaType.APPLICATION_JSON).content(captureBody(0)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/enrollment-sessions/" + id + "/captures").header(KioskKeyInterceptor.HEADER, KEY)
                        .cookie(new Cookie(SessionCookies.NAME, "full"))
                        .contentType(MediaType.APPLICATION_JSON).content(captureBody(0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.done").value(1));
        when(sessions.authenticate(any())).thenReturn(Optional.empty());
    }
}
