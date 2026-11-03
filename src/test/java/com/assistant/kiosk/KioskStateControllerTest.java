package com.assistant.kiosk;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.Fixtures;
import com.assistant.TestClock;
import com.assistant.auth.HandoffService;
import com.assistant.auth.SessionScope;
import com.assistant.common.ApiExceptionHandler;
import com.assistant.config.AssistantProperties;
import com.assistant.enrollment.EnrollmentSessionService;
import com.assistant.profile.ProfileService;
import com.assistant.auth.SessionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class KioskStateControllerTest {

    private static final String KEY = "secret";

    TestClock clock = new TestClock();
    HandoffService handoff;
    EnrollmentSessionService enrollment;
    MockMvc mvc;

    @BeforeEach
    void setUp() {
        AssistantProperties properties = Fixtures.withKioskKey(KEY);
        handoff = new HandoffService(properties, clock);
        enrollment = new EnrollmentSessionService(properties, clock, Mockito.mock(ProfileService.class),
                Mockito.mock(SessionService.class));
        mvc = MockMvcBuilders.standaloneSetup(new KioskStateController(handoff, enrollment, clock))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new KioskKeyInterceptor(properties))
                .build();
    }

    @Test
    void stateIsReservedToThePi() throws Exception {
        mvc.perform(get("/api/kiosk/state")).andExpect(status().isForbidden());
        mvc.perform(get("/api/kiosk/state").header(KioskKeyInterceptor.HEADER, "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test
    void stateWithoutQrAndWithoutCaptureIsEmpty() throws Exception {
        mvc.perform(get("/api/kiosk/state").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverNowMs").value(clock.millis()))
                .andExpect(jsonPath("$.qr", hasSize(0)))
                .andExpect(jsonPath("$.capture").doesNotExist());
    }

    @Test
    void stateReportsEachQrAndTheCurrentCapture() throws Exception {
        HandoffService.IssuedQr active = handoff.issue(5L, SessionScope.FULL);
        HandoffService.IssuedQr consumed = handoff.issue(5L, SessionScope.WEIGHT);
        handoff.redeem(consumed.token());
        enrollment.openOrResume();

        mvc.perform(get("/api/kiosk/state").header(KioskKeyInterceptor.HEADER, KEY)
                        .param("qr", active.qrId() + "," + consumed.qrId() + ",unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qr", hasSize(3)))
                .andExpect(jsonPath("$.qr[0].qrId").value(active.qrId()))
                .andExpect(jsonPath("$.qr[0].state").value("ACTIVE"))
                .andExpect(jsonPath("$.qr[1].state").value("CONSUMED"))
                .andExpect(jsonPath("$.qr[2].state").value("EXPIRED"))
                .andExpect(jsonPath("$.capture.state").value("WAITING"))
                .andExpect(jsonPath("$.capture.mode").value("CREATE"));
    }

    @Test
    void aRecaptureRequestedFromASmartphoneAppearsInTheState() throws Exception {
        enrollment.createRecapture(5L);

        mvc.perform(get("/api/kiosk/state").header(KioskKeyInterceptor.HEADER, KEY))
                .andExpect(jsonPath("$.capture.mode").value("RECAPTURE"))
                .andExpect(jsonPath("$.capture.profileId").value(5));
    }

    @Test
    void theNumberOfTrackedQrCodesIsBounded() throws Exception {
        StringBuilder ids = new StringBuilder();
        for (int i = 0; i < 25; i++) {
            ids.append("id").append(i).append(',');
        }
        mvc.perform(get("/api/kiosk/state").header(KioskKeyInterceptor.HEADER, KEY).param("qr", ids.toString()))
                .andExpect(jsonPath("$.qr", hasSize(10)));
    }
}
