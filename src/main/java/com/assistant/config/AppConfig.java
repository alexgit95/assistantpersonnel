package com.assistant.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    OpenAPI assistantOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Assistant personnel")
                .description("API de l'assistant personnel (profils, session faciale, applications)")
                .version("0.1.0"));
    }
}
