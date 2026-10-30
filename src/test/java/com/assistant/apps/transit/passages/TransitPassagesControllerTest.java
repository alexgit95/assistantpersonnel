package com.assistant.apps.transit.passages;

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
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

class TransitPassagesControllerTest {

    private final NextPassagesService service = mock(NextPassagesService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new TransitPassagesController(service))
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
    void searchesSavesAndReadsStopsAndPassagesForTheResolvedProfile() throws Exception {
        var stop = new TransitStop("24377", "Carnot", "Paris", "bus");
        when(service.searchStops("carnot")).thenReturn(List.of(stop));
        when(service.saveStops(42L, List.of(stop))).thenReturn(List.of(stop));
        when(service.stops(42L)).thenReturn(List.of(stop));
        when(service.next(42L)).thenReturn(new NextPassagesService.Overview(false, null, List.of(
                new NextPassagesService.StopPassages(stop, "OK", null, Instant.parse("2026-10-30T08:12:30Z"),
                        List.of(new NextPassagesService.PassageView("Bus 172", "00aa00", "Porte d'Orléans", 5,
                                Instant.parse("2026-10-30T08:17:30Z"), "onTime"))))));

        mvc.perform(get("/api/transit-passages/stops/search").param("query", "carnot"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].arrid").value("24377"));
        mvc.perform(put("/api/transit-passages/stops").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stops\":[{\"arrid\":\"24377\",\"name\":\"Carnot\",\"town\":\"Paris\","
                                + "\"type\":\"bus\"}]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stops[0].name").value("Carnot"));
        mvc.perform(get("/api/transit-passages/stops"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stops[0].town").value("Paris"));
        mvc.perform(get("/api/transit-passages/next"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stops[0].passages[0].minutes").value(5))
                .andExpect(jsonPath("$.stops[0].updatedAt").exists());

        verify(service).saveStops(42L, List.of(stop));
        verify(service).next(42L);
    }

    @Test
    void rejectsMoreThanThreeStopsAndInvalidIdentifiersBeforeReachingTheService() throws Exception {
        String stop = "{\"arrid\":\"1\",\"name\":\"A\",\"town\":\"\",\"type\":\"bus\"}";

        mvc.perform(put("/api/transit-passages/stops").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stops\":[" + String.join(",", stop, stop, stop, stop) + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/transit-passages/stops").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stops\":[{\"arrid\":\"12a\",\"name\":\"A\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void translatesServiceErrorsToTheirHttpStatuses() throws Exception {
        when(service.searchStops("ab")).thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Trop court."));
        when(service.next(anyLong())).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Widget désactivé."))
                .thenThrow(new IllegalStateException("unexpected"));

        mvc.perform(get("/api/transit-passages/stops/search").param("query", "ab"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/transit-passages/next")).andExpect(status().isNotFound());
        mvc.perform(get("/api/transit-passages/next")).andExpect(status().isInternalServerError());
    }
}
