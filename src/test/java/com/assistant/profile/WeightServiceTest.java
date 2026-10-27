package com.assistant.profile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assistant.common.ApiException;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class WeightServiceTest {

    @Mock
    ProfileRepository profiles;

    @Mock
    WeightEntryRepository entries;

    WeightService service;

    @BeforeEach
    void setUp() {
        service = new WeightService(profiles, entries);
    }

    @Test
    void acceptsCommaAndPointAndStoresTenthsOfKilograms() {
        when(profiles.getReferenceById(4L)).thenReturn(new Profile("Alice", null, 0));
        when(entries.save(org.mockito.ArgumentMatchers.any(WeightEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var comma = service.create(4L, LocalDate.of(2026, 10, 27), "72,5");
        var point = service.create(4L, LocalDate.of(2026, 10, 26), "72.5");

        assertThat(comma.kgTenths()).isEqualTo(725);
        assertThat(point.kgTenths()).isEqualTo(725);
    }

    @Test
    void acceptsBoundariesAndWholeKilograms() {
        assertThat(WeightService.parseWeight("20")).isEqualTo(200);
        assertThat(WeightService.parseWeight("400,0")).isEqualTo(4000);
    }

    @Test
    void rejectsValuesOutsideRangeAndMoreThanOneDecimal() {
        for (String value : new String[] {"19.9", "400.1", "72.55", "-1", "abc", ""}) {
            assertThatThrownBy(() -> WeightService.parseWeight(value))
                    .isInstanceOfSatisfying(ApiException.class,
                            error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST));
        }
    }

    @Test
    void cannotModifyAnEntryOwnedByAnotherProfile() {
        when(entries.findByIdAndProfile_Id(25L, 4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(4L, 25L, LocalDate.now(), "72,5"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(entries).findByIdAndProfile_Id(25L, 4L);
    }
}