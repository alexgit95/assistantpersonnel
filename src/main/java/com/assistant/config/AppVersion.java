package com.assistant.config;

import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.info.GitProperties;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Version affichee sur le kiosque : version du pom et SHA court du commit, par exemple {@code 1.1.1-a1b2c3d}. */
@Component
public class AppVersion {

    static final int SHA_LENGTH = 7;
    static final String UNKNOWN_SHA = "dev";
    static final String UNKNOWN_VERSION = "0";
    private static final Pattern SHA = Pattern.compile("[0-9a-fA-F]{7,64}");

    private final String display;

    @Autowired
    public AppVersion(ObjectProvider<BuildProperties> build, ObjectProvider<GitProperties> git, Environment environment) {
        BuildProperties buildProperties = build.getIfAvailable();
        GitProperties gitProperties = git.getIfAvailable();
        this.display = compose(buildProperties == null ? null : buildProperties.getVersion(),
                environment.getProperty("GIT_COMMIT"), gitProperties == null ? null : gitProperties.getCommitId());
    }

    AppVersion(String pomVersion, String environmentCommit, String gitPropertiesCommit) {
        this.display = compose(pomVersion, environmentCommit, gitPropertiesCommit);
    }

    public String display() {
        return display;
    }

    /** Variable d'environnement d'abord (image Docker), puis git.properties ; un SHA absent ou invalide donne « dev ». */
    private static String compose(String pomVersion, String environmentCommit, String gitPropertiesCommit) {
        String version = pomVersion == null || pomVersion.isBlank() ? UNKNOWN_VERSION : pomVersion.strip();
        String sha = shortSha(environmentCommit);
        if (sha == null) {
            sha = shortSha(gitPropertiesCommit);
        }
        return version + "-" + (sha == null ? UNKNOWN_SHA : sha);
    }

    private static String shortSha(String commit) {
        if (commit == null || !SHA.matcher(commit.strip()).matches()) {
            return null;
        }
        return commit.strip().substring(0, SHA_LENGTH).toLowerCase(Locale.ROOT);
    }
}
