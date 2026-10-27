package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.assistant.Fixtures;
import com.assistant.config.AssistantProperties.Backend;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

class AssistantPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void defaultsAreValid() {
        assertThat(validator.validate(Fixtures.properties())).isEmpty();
    }

    @Test
    void detectorInputSizeMustBeMultipleOf32() {
        var recognition = new AssistantProperties.Recognition(0.55, 0.05, 2, 200, 0.5, 80, 640, Backend.AUTO, false);
        assertThat(validator.validate(recognition)).hasSize(1);
    }

    @Test
    void thresholdOutOfRangeIsRejected() {
        var recognition = new AssistantProperties.Recognition(5.0, 0.05, 2, 224, 0.5, 80, 640, Backend.AUTO, false);
        assertThat(validator.validate(recognition)).isNotEmpty();
    }

    @Test
    void minPhotosCannotExceedMaxPhotos() {
        var enrollment = new AssistantProperties.Enrollment(9, 8, 0);
        assertThat(validator.validate(enrollment)).hasSize(1);
    }

    @Test
    void negativeMaxProfilesIsRejected() {
        assertThat(validator.validate(new AssistantProperties.Enrollment(5, 8, -1))).isNotEmpty();
    }

    @Test
    void sessionDurationMustBePositive() {
        assertThat(validator.validate(new AssistantProperties.Session(0, 30, 60))).isNotEmpty();
    }
}
