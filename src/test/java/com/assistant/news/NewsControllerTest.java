package com.assistant.news;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionInterceptor;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiException;
import com.assistant.common.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
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
class NewsControllerTest {

    @Mock
    NewsService service;
    @Mock
    SessionService sessionService;

    MockMvc mvc;
    final Cookie cookie = new Cookie(SessionCookies.NAME, "tok");

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new NewsController(service))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new SessionInterceptor(sessionService))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private void authenticate() {
        when(sessionService.authenticate("tok")).thenReturn(Optional.of(new AuthenticatedSession("h", 4L, 1L)));
    }

    @Test
    void everyEndpointRequiresASession() throws Exception {
        mvc.perform(get("/api/news/categories")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/news/selection")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/news/selection").contentType(MediaType.APPLICATION_JSON).content("{\"categoryIds\":[]}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/news/feed")).andExpect(status().isUnauthorized());
    }

    @Test
    void listsCategories() throws Exception {
        authenticate();
        when(service.listCategories()).thenReturn(List.of(new NewsService.CategoryInfo(1L, "Tech")));

        mvc.perform(get("/api/news/categories").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Tech"));
    }

    @Test
    void readsAndSavesTheSelectionOfTheConnectedProfile() throws Exception {
        authenticate();
        when(service.selection(4L)).thenReturn(List.of(2L));
        when(service.saveSelection(4L, List.of(1L, 2L))).thenReturn(List.of(1L, 2L));

        mvc.perform(get("/api/news/selection").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[0]").value(2));
        mvc.perform(put("/api/news/selection").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[1,2]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryIds[1]").value(2));
    }

    @Test
    void anUnknownCategoryIsABadRequest() throws Exception {
        authenticate();
        when(service.saveSelection(4L, List.of(99L)))
                .thenThrow(new ApiException(HttpStatus.BAD_REQUEST, "Une catégorie sélectionnée est inconnue."));

        mvc.perform(put("/api/news/selection").cookie(cookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"categoryIds\":[99]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void aMissingListIsRejectedByValidation() throws Exception {
        authenticate();

        mvc.perform(put("/api/news/selection").cookie(cookie).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsTheFeedOfTheSelectedCategories() throws Exception {
        authenticate();
        when(service.overview(4L)).thenReturn(new NewsService.Overview(false, List.of(
                new NewsService.CategoryContent(1L, "Tech", List.of(new NewsEntry("Titre", "Desc")), null))));

        mvc.perform(get("/api/news/feed").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testMode").value(false))
                .andExpect(jsonPath("$.categories[0].name").value("Tech"))
                .andExpect(jsonPath("$.categories[0].entries[0].title").value("Titre"))
                .andExpect(jsonPath("$.categories[0].entries[0].description").value("Desc"));
    }

    @Test
    void aDisabledWidgetIsNotFound() throws Exception {
        authenticate();
        when(service.overview(4L)).thenThrow(new ApiException(HttpStatus.NOT_FOUND, "Application désactivée."));

        mvc.perform(get("/api/news/feed").cookie(cookie)).andExpect(status().isNotFound());
    }
}
