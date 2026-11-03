package com.assistant.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.mock.env.MockEnvironment;

class AppVersionTest {

    private static final String SHA = "A1B2C3D4E5F60718293A4B5C6D7E8F9012345678";

    @Test
    void shortensTheEnvironmentCommitToSevenCharacters() {
        assertThat(new AppVersion("1.1.1", SHA, null).display()).isEqualTo("1.1.1-a1b2c3d");
    }

    @Test
    void fallsBackToGitPropertiesWhenTheEnvironmentHasNoCommit() {
        assertThat(new AppVersion("1.1.1", null, SHA).display()).isEqualTo("1.1.1-a1b2c3d");
        assertThat(new AppVersion("1.1.1", " ", SHA).display()).isEqualTo("1.1.1-a1b2c3d");
    }

    @Test
    void ignoresThePlaceholderOfAnImageBuiltWithoutCommit() {
        assertThat(new AppVersion("1.1.1", "unknown", SHA).display()).isEqualTo("1.1.1-a1b2c3d");
        assertThat(new AppVersion("1.1.1", "unknown", null).display()).isEqualTo("1.1.1-dev");
    }

    @Test
    void givesTheDevSuffixWhenNoCommitIsKnownOrItIsInvalid() {
        assertThat(new AppVersion("1.1.1", null, null).display()).isEqualTo("1.1.1-dev");
        assertThat(new AppVersion("1.1.1", "abc", "not-a-sha").display()).isEqualTo("1.1.1-dev");
    }

    @Test
    void usesAPlaceholderVersionWithoutBuildInformation() {
        assertThat(new AppVersion(null, SHA, null).display()).isEqualTo("0-a1b2c3d");
        assertThat(new AppVersion(" ", null, null).display()).isEqualTo("0-dev");
    }

    @Test
    void readsTheSpringBuildAndGitInformationAndTheEnvironmentVariable() {
        Properties build = new Properties();
        build.setProperty("version", "2.0.0");
        Properties git = new Properties();
        git.setProperty("commit.id", "1111111222222233333334444444555555566666");

        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> buildProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<GitProperties> gitProvider = mock(ObjectProvider.class);
        when(buildProvider.getIfAvailable()).thenReturn(new BuildProperties(build));
        when(gitProvider.getIfAvailable()).thenReturn(new GitProperties(git));

        assertThat(new AppVersion(buildProvider, gitProvider, new MockEnvironment()).display())
                .isEqualTo("2.0.0-1111111");
        assertThat(new AppVersion(buildProvider, gitProvider,
                new MockEnvironment().withProperty("GIT_COMMIT", SHA)).display()).isEqualTo("2.0.0-a1b2c3d");
    }

    @Test
    void worksWhenSpringProvidesNoBuildNorGitInformation() {
        @SuppressWarnings("unchecked")
        ObjectProvider<BuildProperties> buildProvider = mock(ObjectProvider.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<GitProperties> gitProvider = mock(ObjectProvider.class);

        assertThat(new AppVersion(buildProvider, gitProvider, new MockEnvironment()).display()).isEqualTo("0-dev");
    }
}
