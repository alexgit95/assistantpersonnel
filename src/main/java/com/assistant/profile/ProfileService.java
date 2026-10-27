package com.assistant.profile;

import com.assistant.common.ApiException;
import com.assistant.config.AssistantProperties;
import java.time.Clock;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    static final int MAX_EMBEDDINGS_PER_PROFILE = 40;
    static final int MAX_AVATAR_LENGTH = 100_000;
    private static final int PAGE_SIZE = 200;
    private static final long NO_PROFILE = -1L;
    private static final Pattern NAME = Pattern.compile("^[\\p{L}\\p{N} '.\\-]{1,60}$");
    private static final Pattern AVATAR_PREFIX = Pattern.compile("^data:image/(jpeg|png|webp);base64,");

    private final ProfileRepository profiles;
    private final AssistantProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ProfileService(ProfileRepository profiles, AssistantProperties properties,
                          ApplicationEventPublisher events, Clock clock) {
        this.profiles = profiles;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<FaceView> listFaces() {
        return profiles.findAllWithEmbeddings().stream()
                .map(p -> new FaceView(p.getId(), p.getName(), p.getAvatar(),
                        p.getEmbeddings().stream()
                                .map(e -> EmbeddingCodec.toList(EmbeddingCodec.decode(e.getEmbeddingJson())))
                                .toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public ProfileView getProfile(long id) {
        return toView(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Page<AdminProfileView> listAdminProfiles(Pageable pageable) {
        return profiles.findAll(pageable)
                .map(profile -> new AdminProfileView(profile.getId(), profile.getName(), profile.getAvatar()));
    }

    @Transactional
    public ProfileView create(String name, List<float[]> embeddings, String avatar) {
        String cleanName = validateName(name);
        String cleanAvatar = validateAvatar(avatar);
        AssistantProperties.Enrollment enrollment = properties.enrollment();
        if (embeddings.size() < enrollment.minPhotos() || embeddings.size() > enrollment.maxPhotos()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Entre " + enrollment.minPhotos() + " et "
                    + enrollment.maxPhotos() + " captures sont necessaires.");
        }
        if (enrollment.maxProfiles() > 0 && profiles.count() >= enrollment.maxProfiles()) {
            throw new ApiException(HttpStatus.CONFLICT, "Le nombre maximal de profils est atteint.");
        }
        rejectKnownFace(embeddings, NO_PROFILE);

        Profile profile = new Profile(cleanName, cleanAvatar, clock.millis());
        embeddings.forEach(e -> profile.addEmbedding(EmbeddingCodec.encode(e)));
        return toView(profiles.save(profile));
    }

    /** Ajoute ou remplace les empreintes d'un profil ; l'avatar est mis a jour s'il est fourni. */
    @Transactional
    public ProfileView updateEmbeddings(long profileId, List<float[]> embeddings, boolean replace, String avatar) {
        String cleanAvatar = validateAvatar(avatar);
        int max = properties.enrollment().maxPhotos();
        if (embeddings.isEmpty() || embeddings.size() > max) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Entre 1 et " + max + " captures sont necessaires.");
        }
        Profile profile = profiles.findByIdWithEmbeddings(profileId).orElseThrow(ProfileService::notFound);
        rejectKnownFace(embeddings, profileId);

        List<FaceEmbedding> current = profile.getEmbeddings();
        if (replace) {
            current.clear();
        }
        embeddings.forEach(e -> profile.addEmbedding(EmbeddingCodec.encode(e)));
        while (current.size() > MAX_EMBEDDINGS_PER_PROFILE) {
            current.removeFirst();
        }
        if (cleanAvatar != null) {
            profile.setAvatar(cleanAvatar);
        }
        return toView(profile);
    }

    @Transactional
    public ProfileView updateAvatar(long profileId, String avatar) {
        String cleanAvatar = validateAvatar(avatar);
        if (cleanAvatar == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Avatar manquant.");
        }
        Profile profile = findOrThrow(profileId);
        profile.setAvatar(cleanAvatar);
        return toView(profile);
    }

    /** Supprime le profil et ses empreintes, puis previent les modules (sessions, applications). */
    @Transactional
    public void delete(long profileId) {
        Profile profile = findOrThrow(profileId);
        profiles.delete(profile);
        events.publishEvent(new ProfileDeletedEvent(profileId));
    }

    public boolean exists(long profileId) {
        return profiles.existsById(profileId);
    }

    private void rejectKnownFace(List<float[]> candidates, long excludedProfileId) {
        double threshold = properties.recognition().matchThreshold();
        int page = 0;
        Slice<String> slice;
        do {
            slice = profiles.findEmbeddingJsonExcluding(excludedProfileId, PageRequest.of(page++, PAGE_SIZE));
            for (String json : slice.getContent()) {
                float[] known = EmbeddingCodec.decode(json);
                for (float[] candidate : candidates) {
                    if (EmbeddingCodec.distance(candidate, known) < threshold) {
                        throw new ApiException(HttpStatus.CONFLICT,
                                "Ce visage correspond deja a un profil existant.");
                    }
                }
            }
        } while (slice.hasNext());
    }

    private Profile findOrThrow(long id) {
        return profiles.findById(id).orElseThrow(ProfileService::notFound);
    }

    private static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, "Profil introuvable.");
    }

    static String validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!NAME.matcher(trimmed).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Le nom doit contenir de 1 a 60 caracteres (lettres, chiffres, espace, apostrophe, tiret, point).");
        }
        return trimmed;
    }

    static String validateAvatar(String avatar) {
        if (avatar == null || avatar.isBlank()) {
            return null;
        }
        var prefix = AVATAR_PREFIX.matcher(avatar);
        if (avatar.length() > MAX_AVATAR_LENGTH || !prefix.find()) {
            throw invalidAvatar();
        }
        for (int i = prefix.end(); i < avatar.length(); i++) {
            char c = avatar.charAt(i);
            boolean base64 = (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '+' || c == '/' || c == '=';
            if (!base64) {
                throw invalidAvatar();
            }
        }
        return avatar;
    }

    private static ApiException invalidAvatar() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "Avatar invalide : image JPEG, PNG ou WebP reduite (data URL) attendue.");
    }

    private static ProfileView toView(Profile profile) {
        return new ProfileView(profile.getId(), profile.getName(), profile.getAvatar(),
                profile.getEmbeddings().size());
    }

    public record FaceView(Long id, String name, String avatar, List<List<Double>> embeddings) {
    }

    public record ProfileView(Long id, String name, String avatar, int embeddingCount) {
    }

    public record AdminProfileView(Long id, String name, String avatar) {
    }
}
