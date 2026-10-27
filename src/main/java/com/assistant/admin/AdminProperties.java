package com.assistant.admin;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "assistant.admin")
public record AdminProperties(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Size(min = 12, max = 72) String password,
        @Min(1) @Max(20) int maxFailedAttempts,
        @Min(1) @Max(1440) int lockoutMinutes) {
}
