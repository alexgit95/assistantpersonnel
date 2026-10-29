package com.assistant.apps.transit;

import com.assistant.common.ApiException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrimKeyService {

    static final long SETTING_ID = 1L;
    static final int MAX_KEY_LENGTH = 200;

    private final PrimSettingRepository repository;

    public PrimKeyService(PrimSettingRepository repository) {
        this.repository = repository;
    }

    /** Lue a chaque appel pour prendre en compte une saisie ou une restauration sans redemarrage. */
    @Transactional(readOnly = true)
    public Optional<String> currentKey() {
        return repository.findById(SETTING_ID).map(PrimSetting::getApiKey).filter(key -> !key.isBlank());
    }

    public boolean isConfigured() {
        return currentKey().isPresent();
    }

    /** Une valeur vide conserve la cle existante. */
    @Transactional
    public void save(String apiKey) {
        String value = apiKey == null ? "" : apiKey.strip();
        if (value.isEmpty()) {
            return;
        }
        if (value.length() > MAX_KEY_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "La clé PRIM est trop longue (200 caractères maximum).");
        }
        PrimSetting setting = repository.findById(SETTING_ID).orElseGet(() -> new PrimSetting(SETTING_ID));
        setting.setApiKey(value);
        repository.save(setting);
    }

    @Transactional
    public void clear() {
        repository.deleteById(SETTING_ID);
    }
}
