package com.assistant.announcements;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionInterceptor;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class AnnouncementControllerTest {

    @Mock
    AnnouncementService service;
    @Mock
    SessionService sessionService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AnnouncementController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new SessionInterceptor(sessionService))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    @Test
    void requiresASession() throws Exception {
        mvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsServerNowAndActiveAnnouncements() throws Exception {
        when(sessionService.authenticate("tok"))
                .thenReturn(Optional.of(new AuthenticatedSession("h", 1L, 1L)));
        when(service.listActive()).thenReturn(new AnnouncementService.ActiveAnnouncements(1000L,
                List.of(new AnnouncementService.ActiveView(5L, "Fête", 500L, 9000L, true))));

        mvc.perform(get("/api/announcements").cookie(new Cookie(SessionCookies.NAME, "tok")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverNow").value(1000))
                .andExpect(jsonPath("$.announcements[0].title").value("Fête"))
                .andExpect(jsonPath("$.announcements[0].startsAt").value(500))
                .andExpect(jsonPath("$.announcements[0].endsAt").value(9000))
                .andExpect(jsonPath("$.announcements[0].countdown").value(true));
    }
}
