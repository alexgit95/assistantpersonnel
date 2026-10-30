package com.assistant.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.Fixtures;
import com.assistant.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.SliceImpl;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProfileServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    ProfileRepository profiles;
    @Mock
    ApplicationEventPublisher events;

    private ProfileService service(int maxProfiles) {
        return new ProfileService(profiles, Fixtures.properties(maxProfiles), events, CLOCK);
    }

    @BeforeEach
    void noKnownFaces() {
        lenient().when(profiles.findEmbeddingJsonExcluding(any(Long.class), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of()));
        lenient().when(profiles.save(any(Profile.class))).thenAnswer(inv -> {
            Profile p = inv.getArgument(0);
            ReflectionTestUtils.setField(p, "id", 1L);
            return p;
        });
    }

    private static void assertStatus(Runnable action, HttpStatus status) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.getStatus()).isEqualTo(status));
    }

    @Test
    void createStoresProfileWithEmbeddingsAndAvatar() {
        ProfileService.ProfileView view = service(0).create("  Alice ", Fixtures.vectors(5, 0f), Fixtures.avatar());

        ArgumentCaptor<Profile> captor = ArgumentCaptor.forClass(Profile.class);
        verify(profiles).save(captor.capture());
        Profile saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Alice");
        assertThat(saved.getEmbeddings()).hasSize(5);
        assertThat(saved.getAvatar()).isEqualTo(Fixtures.avatar());
        assertThat(saved.getCreatedAtMs()).isEqualTo(CLOCK.millis());
        assertThat(view.embeddingCount()).isEqualTo(5);
    }

    @Test
    void createRejectsTooFewOrTooManyCaptures() {
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(4, 0f), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(9, 0f), null), HttpStatus.BAD_REQUEST);
        verify(profiles, never()).save(any());
    }

    @Test
    void createRejectsInvalidNames() {
        assertStatus(() -> service(0).create("   ", Fixtures.vectors(5, 0f), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create("<script>", Fixtures.vectors(5, 0f), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create(null, Fixtures.vectors(5, 0f), null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create("x".repeat(61), Fixtures.vectors(5, 0f), null), HttpStatus.BAD_REQUEST);
    }

    @Test
    void createRejectsInvalidAvatar() {
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(5, 0f), "http://evil/x.png"),
                HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(5, 0f),
                "data:image/jpeg;base64,<script>"), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(5, 0f),
                "data:image/jpeg;base64," + "A".repeat(ProfileService.MAX_AVATAR_LENGTH)), HttpStatus.BAD_REQUEST);
    }

    @Test
    void createIsUnlimitedWhenMaxProfilesIsZero() {
        service(0).create("Alice", Fixtures.vectors(5, 0f), null);
        verify(profiles, never()).count();
    }

    @Test
    void createRefusedWhenProfileLimitReached() {
        when(profiles.countWithoutGuest()).thenReturn(3L);
        assertStatus(() -> service(3).create("Alice", Fixtures.vectors(5, 0f), null), HttpStatus.CONFLICT);
        verify(profiles, never()).save(any());
    }

    @Test
    void createAllowedBelowProfileLimit() {
        when(profiles.countWithoutGuest()).thenReturn(2L);
        service(3).create("Alice", Fixtures.vectors(5, 0f), null);
        verify(profiles).save(any());
    }

    @Test
    void createRefusedWhenFaceAlreadyKnown() {
        String known = EmbeddingCodec.encode(Fixtures.vector(0f));
        when(profiles.findEmbeddingJsonExcluding(any(Long.class), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(known)));
        assertStatus(() -> service(0).create("Alice", Fixtures.vectors(5, 0f), null), HttpStatus.CONFLICT);
        verify(profiles, never()).save(any());
    }

    @Test
    void createAcceptsFaceFarFromKnownProfiles() {
        String known = EmbeddingCodec.encode(Fixtures.vector(1f));
        when(profiles.findEmbeddingJsonExcluding(any(Long.class), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(known)));
        service(0).create("Alice", Fixtures.vectors(5, 0f), null);
        verify(profiles).save(any());
    }

    private Profile existingProfile(int embeddings) {
        Profile profile = new Profile("Bob", null, 0L);
        ReflectionTestUtils.setField(profile, "id", 7L);
        Fixtures.vectors(embeddings, 2f).forEach(v -> profile.addEmbedding(EmbeddingCodec.encode(v)));
        return profile;
    }

    @Test
    void updateEmbeddingsAddsAndExcludesOwnProfileFromDuplicateCheck() {
        Profile profile = existingProfile(5);
        when(profiles.findByIdWithEmbeddings(7L)).thenReturn(Optional.of(profile));

        ProfileService.ProfileView view = service(0).updateEmbeddings(7L, Fixtures.vectors(3, 0f), false, null);

        assertThat(view.embeddingCount()).isEqualTo(8);
        verify(profiles).findEmbeddingJsonExcluding(eq(7L), any(Pageable.class));
    }

    @Test
    void updateEmbeddingsReplacesAndChangesAvatar() {
        Profile profile = existingProfile(5);
        when(profiles.findByIdWithEmbeddings(7L)).thenReturn(Optional.of(profile));

        ProfileService.ProfileView view = service(0).updateEmbeddings(7L, Fixtures.vectors(2, 0f), true,
                Fixtures.avatar());

        assertThat(view.embeddingCount()).isEqualTo(2);
        assertThat(profile.getAvatar()).isEqualTo(Fixtures.avatar());
    }

    @Test
    void updateEmbeddingsKeepsOnlyTheNewestWhenCapExceeded() {
        Profile profile = existingProfile(ProfileService.MAX_EMBEDDINGS_PER_PROFILE - 1);
        when(profiles.findByIdWithEmbeddings(7L)).thenReturn(Optional.of(profile));

        service(0).updateEmbeddings(7L, Fixtures.vectors(5, 0f), false, null);

        assertThat(profile.getEmbeddings()).hasSize(ProfileService.MAX_EMBEDDINGS_PER_PROFILE);
    }

    @Test
    void updateEmbeddingsRejectsBadCountsAndUnknownProfile() {
        assertStatus(() -> service(0).updateEmbeddings(7L, List.of(), false, null), HttpStatus.BAD_REQUEST);
        assertStatus(() -> service(0).updateEmbeddings(7L, Fixtures.vectors(9, 0f), false, null),
                HttpStatus.BAD_REQUEST);
        when(profiles.findByIdWithEmbeddings(7L)).thenReturn(Optional.empty());
        assertStatus(() -> service(0).updateEmbeddings(7L, Fixtures.vectors(2, 0f), false, null),
                HttpStatus.NOT_FOUND);
    }

    @Test
    void updateEmbeddingsRefusedWhenFaceBelongsToAnotherProfile() {
        when(profiles.findByIdWithEmbeddings(7L)).thenReturn(Optional.of(existingProfile(5)));
        when(profiles.findEmbeddingJsonExcluding(eq(7L), any(Pageable.class)))
                .thenReturn(new SliceImpl<>(List.of(EmbeddingCodec.encode(Fixtures.vector(0f)))));
        assertStatus(() -> service(0).updateEmbeddings(7L, Fixtures.vectors(2, 0f), false, null),
                HttpStatus.CONFLICT);
    }

    @Test
    void updateAvatarRequiresAnAvatarAndAProfile() {
        assertStatus(() -> service(0).updateAvatar(7L, " "), HttpStatus.BAD_REQUEST);
        when(profiles.findById(7L)).thenReturn(Optional.empty());
        assertStatus(() -> service(0).updateAvatar(7L, Fixtures.avatar()), HttpStatus.NOT_FOUND);

        Profile profile = existingProfile(1);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));
        service(0).updateAvatar(7L, Fixtures.avatar());
        assertThat(profile.getAvatar()).isEqualTo(Fixtures.avatar());
    }

    @Test
    void deleteRemovesProfileAndPublishesEvent() {
        Profile profile = existingProfile(1);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));

        service(0).delete(7L);

        verify(profiles).delete(profile);
        verify(events).publishEvent(new ProfileDeletedEvent(7L));
    }

    @Test
    void deleteRefusesTheGuestProfile() {
        when(profiles.findById(7L)).thenReturn(Optional.of(existingProfile(1)));
        when(profiles.isGuest(7L)).thenReturn(true);

        assertStatus(() -> service(0).delete(7L), HttpStatus.BAD_REQUEST);

        verify(profiles, never()).delete(any());
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void deleteUnknownProfileIsNotFound() {
        when(profiles.findById(7L)).thenReturn(Optional.empty());
        assertStatus(() -> service(0).delete(7L), HttpStatus.NOT_FOUND);
        verify(events, never()).publishEvent(any(Object.class));
    }

    @Test
    void listFacesExposesIdNameAvatarAndEmbeddingsOnly() {
        when(profiles.findAllWithEmbeddings()).thenReturn(List.of(existingProfile(2)));

        List<ProfileService.FaceView> faces = service(0).listFaces();

        assertThat(faces).hasSize(1);
        assertThat(faces.getFirst().id()).isEqualTo(7L);
        assertThat(faces.getFirst().name()).isEqualTo("Bob");
        assertThat(faces.getFirst().embeddings()).hasSize(2);
        assertThat(faces.getFirst().embeddings().getFirst()).hasSize(128);
    }

    @Test
    void adminProfileListIsPagedAndContainsOnlyNameAndAvatar() {
        Profile profile = existingProfile(3);
        when(profiles.findAllWithoutGuest(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(profile)));

        var page = service(0).listAdminProfiles(PageRequest.of(0, 50));

        assertThat(page.getContent()).containsExactly(new ProfileService.AdminProfileView(7L, "Bob", null));
        verify(profiles).findAllWithoutGuest(any(Pageable.class));
    }

    @Test
    void getProfileUnknownIsNotFound() {
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        assertStatus(() -> service(0).getProfile(1L), HttpStatus.NOT_FOUND);
    }
}
