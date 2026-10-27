package com.assistant.profile;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.Fixtures;
import com.assistant.auth.AuthenticatedSession;
import com.assistant.auth.AuthenticatedSessionArgumentResolver;
import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionInterceptor;
import com.assistant.auth.SessionService;
import com.assistant.common.ApiExceptionHandler;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class ProfileControllerTest {

    @Mock
    ProfileService profileService;
    @Mock
    SessionService sessionService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new ProfileController(profileService))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new SessionInterceptor(sessionService))
                .setCustomArgumentResolvers(new AuthenticatedSessionArgumentResolver())
                .build();
    }

    private static Cookie cookie() {
        return new Cookie(SessionCookies.NAME, "tok");
    }

    private void loggedInAs(long profileId) {
        when(sessionService.authenticate("tok"))
                .thenReturn(Optional.of(new AuthenticatedSession("h", profileId, 1L)));
    }

    private static String embeddingsJson(int count) {
        String vector = Fixtures.list(Fixtures.vector(0f)).stream().map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
        return IntStream.range(0, count).mapToObj(i -> vector).collect(Collectors.joining(",", "[", "]"));
    }

    @Test
    void facesIsPublicAndNotCached() throws Exception {
        when(profileService.listFaces()).thenReturn(List.of(
                new ProfileService.FaceView(1L, "Alice", null, List.of(Fixtures.list(Fixtures.vector(0f))))));

        mvc.perform(get("/api/faces"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].name").value("Alice"))
                .andExpect(jsonPath("$[0].embeddings[0].length()").value(128));
    }

    @Test
    void createReturns201WithoutRequiringASession() throws Exception {
        when(profileService.create(eq("Alice"), any(), eq(null)))
                .thenReturn(new ProfileService.ProfileView(1L, "Alice", null, 5));

        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"embeddings\":" + embeddingsJson(5) + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.embeddingCount").value(5));
    }

    @Test
    void createWithBlankNameOrNoEmbeddingsIsABadRequest() throws Exception {
        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" \",\"embeddings\":" + embeddingsJson(5) + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"embeddings\":[]}"))
                .andExpect(status().isBadRequest());
        verify(profileService, never()).create(any(), any(), any());
    }

    @Test
    void createWithWrongEmbeddingSizeIsABadRequest() throws Exception {
        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alice\",\"embeddings\":[[0.1,0.2]]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meRequiresASession() throws Exception {
        when(sessionService.authenticate(null)).thenReturn(Optional.empty());
        mvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void meReturnsOnlyTheProfileOfTheSession() throws Exception {
        loggedInAs(5L);
        when(profileService.getProfile(5L)).thenReturn(new ProfileService.ProfileView(5L, "Alice", null, 5));

        mvc.perform(get("/api/profiles/me").cookie(cookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5));
    }

    @Test
    void updatingEmbeddingsOfAnotherProfileIsForbidden() throws Exception {
        loggedInAs(5L);
        mvc.perform(put("/api/profiles/6/embeddings").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embeddings\":" + embeddingsJson(2) + ",\"replace\":false}"))
                .andExpect(status().isForbidden());
        verify(profileService, never()).updateEmbeddings(any(Long.class), any(), any(Boolean.class), any());
    }

    @Test
    void updatingOwnEmbeddingsIsAllowed() throws Exception {
        loggedInAs(5L);
        when(profileService.updateEmbeddings(eq(5L), any(), eq(true), eq(null)))
                .thenReturn(new ProfileService.ProfileView(5L, "Alice", null, 2));

        mvc.perform(put("/api/profiles/5/embeddings").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embeddings\":" + embeddingsJson(2) + ",\"replace\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingCount").value(2));
    }

    @Test
    void updatingEmbeddingsWithoutSessionIsUnauthorized() throws Exception {
        when(sessionService.authenticate(null)).thenReturn(Optional.empty());
        mvc.perform(put("/api/profiles/5/embeddings").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embeddings\":" + embeddingsJson(2) + ",\"replace\":false}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void avatarOfAnotherProfileCannotBeChanged() throws Exception {
        loggedInAs(5L);
        mvc.perform(put("/api/profiles/6/avatar").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatar\":\"" + Fixtures.avatar() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownAvatarCanBeChanged() throws Exception {
        loggedInAs(5L);
        when(profileService.updateAvatar(5L, Fixtures.avatar()))
                .thenReturn(new ProfileService.ProfileView(5L, "Alice", Fixtures.avatar(), 5));

        mvc.perform(put("/api/profiles/5/avatar").cookie(cookie()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avatar\":\"" + Fixtures.avatar() + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void deletingAnotherProfileIsForbidden() throws Exception {
        loggedInAs(5L);
        mvc.perform(delete("/api/profiles/6").cookie(cookie())).andExpect(status().isForbidden());
        verify(profileService, never()).delete(6L);
    }

    @Test
    void deletingOwnProfileReturns204() throws Exception {
        loggedInAs(5L);
        mvc.perform(delete("/api/profiles/5").cookie(cookie())).andExpect(status().isNoContent());
        verify(profileService).delete(5L);
    }
}
