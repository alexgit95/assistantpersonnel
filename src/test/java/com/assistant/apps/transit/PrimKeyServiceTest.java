package com.assistant.apps.transit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class PrimKeyServiceTest {

    private final PrimSettingRepository repository = mock(PrimSettingRepository.class);
    private final PrimKeyService service = new PrimKeyService(repository);

    @Test
    void noKeyWhenNothingIsStored() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        assertThat(service.currentKey()).isEmpty();
        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void blankStoredKeyIsNotConfigured() {
        PrimSetting setting = new PrimSetting(1L);
        setting.setApiKey("  ");
        when(repository.findById(1L)).thenReturn(Optional.of(setting));

        assertThat(service.isConfigured()).isFalse();
    }

    @Test
    void savesTheTrimmedKey() {
        when(repository.findById(1L)).thenReturn(Optional.empty());

        service.save("  secret  ");

        var captor = org.mockito.ArgumentCaptor.forClass(PrimSetting.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("secret");
    }

    @Test
    void replacesAnExistingKey() {
        PrimSetting setting = new PrimSetting(1L);
        setting.setApiKey("old");
        when(repository.findById(1L)).thenReturn(Optional.of(setting));

        service.save("new");

        assertThat(setting.getApiKey()).isEqualTo("new");
        verify(repository).save(setting);
    }

    @Test
    void emptyInputKeepsTheExistingKey() {
        service.save("   ");
        service.save(null);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsAnOverlongKey() {
        assertThatThrownBy(() -> service.save("x".repeat(201))).isInstanceOf(ApiException.class);
        verify(repository, never()).save(any());
    }

    @Test
    void clearDeletesTheSetting() {
        service.clear();

        verify(repository).deleteById(1L);
    }
}
