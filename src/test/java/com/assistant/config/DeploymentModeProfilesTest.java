package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/** Valeurs par defaut de chaque mode de deploiement, chargees depuis les vrais fichiers de propriete. */
class DeploymentModeProfilesTest {

    @Configuration
    static class Empty {
    }

    private static void withProfiles(String[] profiles, Consumer<Environment> check) {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(Empty.class)
                .web(WebApplicationType.NONE)
                .profiles(profiles)
                .properties("spring.main.banner-mode=off")
                .run()) {
            check.accept(context.getEnvironment());
        }
    }

    private static void assertDev(Environment env) {
        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:sqlite:");
        assertThat(env.getProperty("assistant.face-assets.mode")).isEqualTo("cdn");
        assertThat(env.getProperty("assistant.qr.dev-mode")).isEqualTo("true");
        assertThat(env.getProperty("assistant.https.enabled")).isEqualTo("false");
        assertThat(env.getProperty("server.port")).isEqualTo("8080");
    }

    private static void assertRemote(Environment env) {
        assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:postgresql:");
        assertThat(env.getProperty("assistant.face-assets.mode")).isEqualTo("local");
        assertThat(env.getProperty("assistant.qr.dev-mode")).isEqualTo("false");
        assertThat(env.getProperty("assistant.https.enabled")).isEqualTo("true");
        assertThat(env.getProperty("assistant.http.enabled")).isEqualTo("true");
        assertThat(env.getProperty("server.port")).isEqualTo("8443");
        assertThat(env.getProperty("server.ssl.bundle")).isEqualTo("kiosk");
    }

    @Test
    void noProfileFallsBackToDev() {
        withProfiles(new String[0], DeploymentModeProfilesTest::assertDev);
    }

    @Test
    void devUsesSqliteCdnHttpOnlyAndQrDevMode() {
        withProfiles(new String[] {"dev"}, DeploymentModeProfilesTest::assertDev);
    }

    @Test
    void onpremUsesSqliteCdnHttpOnlyWithoutQrDevMode() {
        withProfiles(new String[] {"onprem"}, env -> {
            assertThat(env.getProperty("spring.datasource.url")).startsWith("jdbc:sqlite:");
            assertThat(env.getProperty("assistant.face-assets.mode")).isEqualTo("cdn");
            assertThat(env.getProperty("assistant.qr.dev-mode")).isEqualTo("false");
            assertThat(env.getProperty("assistant.https.enabled")).isEqualTo("false");
            assertThat(env.getProperty("server.port")).isEqualTo("8080");
            assertThat(env.getProperty("server.ssl.bundle")).isNull();
        });
    }

    @Test
    void remoteUsesPostgresLocalAssetsAndHttpsPlusHttp() {
        withProfiles(new String[] {"remote"}, DeploymentModeProfilesTest::assertRemote);
    }

    @Test
    void localAliasBehavesAsDev() {
        withProfiles(new String[] {"local"}, DeploymentModeProfilesTest::assertDev);
    }

    @Test
    void prodAliasBehavesAsRemote() {
        withProfiles(new String[] {"prod"}, DeploymentModeProfilesTest::assertRemote);
    }
}
