package com.assistant.apps.transit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.common.ApiException;
import com.assistant.common.ApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.bind.support.WebDataBinderFactory;

class TransitControllerTest {

    private final TransitService service = mock(TransitService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TransitController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new HandlerMethodArgumentResolver() {
                    @Override
                    public boolean supportsParameter(MethodParameter parameter) {
                        return parameter.getParameterType() == AuthenticatedSession.class;
                    }

                    @Override
                    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                            NativeWebRequest request, WebDataBinderFactory binderFactory) {
                        return new AuthenticatedSession("test", 42L, 0L);
                    }
                }).build();
    }

    @Test
    void returnsLinesFavoritesAndAlertOverviewForTheResolvedProfile() throws Exception {
        TransitLine line = new TransitLine("C01742", "A", "A", "rail", "RER", "d5d500", "000000");
        when(service.searchLines("RER A")).thenReturn(List.of(line));
        when(service.saveFavorites(42L, List.of("C01742"))).thenReturn(List.of(line));
        when(service.alerts(42L)).thenReturn(new TransitService.Overview(false, false, null,
                Instant.parse("2026-10-28T11:00:00Z"), List.of(new TransitService.LineAlerts(line, List.of()))));

        mvc.perform(get("/api/transit/lines").param("query", "RER A"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].label").value("RER A"));
        mvc.perform(put("/api/transit/favorites").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lineIds\":[\"C01742\"]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lineIds[0]").value("C01742"));
        mvc.perform(get("/api/transit/alerts"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.lines[0].alerts").isEmpty());

        verify(service).searchLines("RER A");
        verify(service).saveFavorites(42L, List.of("C01742"));
        verify(service).alerts(42L);
    }

    @Test
    void translatesBadRequestNotFoundAndUnexpectedErrorsToTheirHttpStatuses() throws Exception {
        when(service.searchLines("bad")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Recherche invalide."));
        when(service.alerts(anyLong())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Widget désactivé."))
                .thenThrow(new IllegalStateException("unexpected"));

        mvc.perform(get("/api/transit/lines").param("query", "bad"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transit/alerts")).andExpect(status().isNotFound());
        mvc.perform(get("/api/transit/alerts")).andExpect(status().isInternalServerError());
    }
}
