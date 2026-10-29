package com.assistant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.assistant.auth.SessionCookies;
import com.assistant.auth.SessionService;
import com.assistant.apps.AssistantApp;
import com.assistant.profile.ProfileApp;
import com.assistant.profile.ProfileAppRepository;
import com.assistant.profile.ProfileRepository;
import com.assistant.apps.ProfileAppService;
import com.assistant.quota.ApiQuotaService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

/** Parcours complet sur SQLite reel : schema Hibernate, creation de profil, session glissante, isolation. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(SessionFlowIntegrationTest.TestApps.class)
class SessionFlowIntegrationTest {

    static final String DB_FILE = "target/it-" + UUID.randomUUID() + ".db";

        @TestConfiguration(proxyBeanMethods = false)
        static class TestApps {
                @Bean
                AssistantApp testWidget() {
                        return new AssistantApp() {
                                @Override
                                public String key() {
                                        return "sample-widget";
                                }

                                @Override
                                public String title() {
                                        return "Widget d’intégration";
                                }

                                @Override
                                public String description() {
                                        return "Widget utilisé pour vérifier le registre et l’isolation.";
                                }

                                @Override
                                public String fragmentTemplate() {
                                        return "fragments/test-widget :: content";
                                }
                        };
                }
        }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE + "?foreign_keys=on");
                registry.add("assistant.admin.username", () -> "admin");
                registry.add("assistant.admin.password", () -> "integration-admin-password");
                registry.add("assistant.admin.max-failed-attempts", () -> "5");
                registry.add("assistant.admin.lockout-minutes", () -> "1");
                registry.add("assistant.backup.max-upload-mb", () -> "1");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    SessionService sessionService;
        @Autowired
        tools.jackson.databind.ObjectMapper objectMapper;
        @Autowired
        ProfileRepository profileRepository;
        @Autowired
        ProfileAppRepository profileAppRepository;
        @Autowired
        JdbcTemplate jdbcTemplate;
        @Autowired
        ProfileAppService profileAppService;
        @Autowired
        ApiQuotaService apiQuotaService;

    private static String embeddings(int count, float base) {
        return IntStream.range(0, count)
                .mapToObj(i -> Fixtures.list(Fixtures.vector(base + i * 0.0001f)).stream().map(String::valueOf)
                        .collect(Collectors.joining(",", "[", "]")))
                .collect(Collectors.joining(",", "[", "]"));
    }

    private long createProfile(String name, float base) throws Exception {
        MvcResult result = mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"avatar\":\"" + Fixtures.avatar()
                                + "\",\"embeddings\":" + embeddings(5, base) + "}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private Cookie login(long profileId) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + "}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andReturn();
        return result.getResponse().getCookie(SessionCookies.NAME);
    }

        private tools.jackson.databind.JsonNode appPreference(Cookie cookie, String key) throws Exception {
                String body = mvc.perform(get("/api/apps").cookie(cookie))
                                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
                for (var preference : objectMapper.readTree(body)) {
                        if (key.equals(preference.path("key").asString())) {
                                return preference;
                        }
                }
                throw new AssertionError("Application introuvable : " + key);
        }

        private MockHttpSession adminSession() throws Exception {
                MvcResult result = mvc.perform(post("/admin/login").with(csrf())
                                                .param("username", "admin")
                                                .param("password", "integration-admin-password"))
                                .andExpect(status().is3xxRedirection())
                                .andReturn();
                return (MockHttpSession) result.getRequest().getSession(false);
        }

    @Test
    void fullFlowWithIsolationBetweenProfiles() throws Exception {
        long alice = createProfile("Alice", 0f);
        long bob = createProfile("Bob", 3f);

        mvc.perform(get("/api/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].embeddings.length()").value(5));

        mvc.perform(post("/api/profiles").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Clone\",\"embeddings\":" + embeddings(5, 0f) + "}"))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/profiles/me")).andExpect(status().isUnauthorized());

        Cookie aliceCookie = login(alice);
        mvc.perform(get("/api/profiles/me").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"));
        mvc.perform(post("/api/auth/heartbeat").cookie(aliceCookie)).andExpect(status().isOk());

        mvc.perform(delete("/api/profiles/" + bob).cookie(aliceCookie)).andExpect(status().isForbidden());
        mvc.perform(put("/api/profiles/" + bob + "/embeddings").cookie(aliceCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embeddings\":" + embeddings(2, 0f) + ",\"replace\":true}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/profiles/" + alice + "/embeddings").cookie(aliceCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"embeddings\":" + embeddings(2, 0.5f) + ",\"replace\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.embeddingCount").value(7));

        Cookie bobCookie = login(bob);
        mvc.perform(get("/api/profiles/me").cookie(bobCookie)).andExpect(jsonPath("$.name").value("Bob"));

        mvc.perform(post("/api/auth/logout").cookie(aliceCookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profiles/me").cookie(aliceCookie)).andExpect(status().isUnauthorized());

        mvc.perform(delete("/api/profiles/" + bob).cookie(bobCookie)).andExpect(status().isNoContent());
        mvc.perform(get("/api/profiles/me").cookie(bobCookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + bob + "}"))
                .andExpect(status().isNotFound());

        assertThat(sessionService.purgeExpired()).isZero();
    }

    @Test
    void clientConfigExposesTheEnvironmentSettings() throws Exception {
        mvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionDurationSeconds").value(600))
                .andExpect(jsonPath("$.matchThreshold").value(0.55))
                .andExpect(jsonPath("$.recognitionBackend").value("auto"))
                .andExpect(jsonPath("$.embeddingSize").value(128))
                .andExpect(jsonPath("$.faceAssetsMode").value("cdn"))
                .andExpect(jsonPath("$.faceApiUrl").value(org.hamcrest.Matchers.containsString("@1.7.15")))
                .andExpect(jsonPath("$.faceModelsUrl").value(org.hamcrest.Matchers.containsString("/model")))
                .andExpect(jsonPath("$.typingEnabled").value(true))
                .andExpect(jsonPath("$.typingSpeedMs").value(25));
    }

    @Test
    void homePageAndFrontendModulesAreServed() throws Exception {
        mvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("lang=\"fr\"")))
                .andExpect(content().string(containsString("id=\"camera\"")))
                .andExpect(content().string(containsString("id=\"logout\"")))
                .andExpect(content().string(containsString("id=\"create-profile\"")))
                .andExpect(content().string(containsString("id=\"recapture-profile\"")))
                .andExpect(content().string(containsString("id=\"asset-progress\"")))
                .andExpect(content().string(containsString("id=\"asset-loading-label\"")))
                .andExpect(content().string(containsString("id=\"clock\"")))
                .andExpect(content().string(containsString("id=\"profile-menu-trigger\"")))
                .andExpect(content().string(containsString("id=\"menu-configuration\"")))
                .andExpect(content().string(containsString("profile-avatar-button avatar-hex")))
                .andExpect(content().string(containsString("/admin\"")))
                .andExpect(content().string(containsString("/enroll")))
                .andExpect(content().string(containsString("/js/home.js")));
        for (String module : List.of("home", "api", "admin", "camera", "enrollment", "enrollment-policy",
                                "face-assets", "matcher", "recognizer", "session-machine", "typewriter",
                                "profile-menu", "configuration", "configuration-state")) {
            mvc.perform(get("/js/" + module + ".js")).andExpect(status().isOk());
        }
                mvc.perform(get("/js/widgets/weight.js")).andExpect(status().isOk());
                mvc.perform(get("/js/widgets/weather.js")).andExpect(status().isOk());
                mvc.perform(get("/js/widgets/announcements.js")).andExpect(status().isOk());
        mvc.perform(get("/css/theme.css")).andExpect(status().isOk())
                .andExpect(content().string(containsString(".avatar-hex")))
                .andExpect(content().string(containsString("polygon(25% 0, 75% 0, 100% 50%, 75% 100%, 25% 100%, 0 50%)")));
        mvc.perform(get("/css/home.css"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString(".profile-menu-panel [hidden]")))
                .andExpect(content().string(containsString(".identity {")))
                .andExpect(content().string(containsString(".dashboard-grid {")))
                .andExpect(content().string(containsString(".webcam-frame {")));
        mvc.perform(get("/css/admin.css")).andExpect(status().isOk());
    }

        @Test
        void certificateHelpIsPublicAndRootCertificateIsMissingInLocalMode() throws Exception {
                mvc.perform(get("/certificat"))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("Télécharger le certificat racine")))
                                .andExpect(content().string(containsString("iPhone et iPad")));
                mvc.perform(get("/root.crt")).andExpect(status().isNotFound());
        }

        @Test
        void configurationPageIsProtectedByTheFaceSession() throws Exception {
                mvc.perform(get("/configuration")).andExpect(status().isUnauthorized());

                long profileId = createProfile("Configuration utilisateur", 1.8f);
                Cookie cookie = login(profileId);
                mvc.perform(get("/configuration").cookie(cookie))
                                .andExpect(status().isOk())
                                .andExpect(content().string(containsString("id=\"widget-pool\"")))
                                .andExpect(content().string(containsString("id=\"configuration-grid\"")))
                                .andExpect(content().string(containsString("id=\"profile-menu-trigger\"")));
        }

        @Test
        void enrollmentPageIsServedWithGuidedCaptureAndAvatarSelection() throws Exception {
                mvc.perform(get("/enroll"))
                        .andExpect(status().isOk())
                        .andExpect(content().string(containsString("id=\"enroll-camera\"")))
                        .andExpect(content().string(containsString("id=\"capture-button\"")))
                        .andExpect(content().string(containsString("id=\"avatar-gallery\"")))
                        .andExpect(content().string(containsString("name=\"embedding-mode\"")))
                        .andExpect(content().string(containsString("lang=\"fr\"")));
                mvc.perform(get("/js/enrollment.js")).andExpect(status().isOk());
                mvc.perform(get("/js/enrollment-policy.js")).andExpect(status().isOk());
                mvc.perform(get("/css/enrollment.css")).andExpect(status().isOk());
            }

    @Test
    void swaggerDocumentationIsExposed() throws Exception {
        String response = mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var paths = objectMapper.readTree(response).path("paths");
        String[][] operations = {
                {"/api/auth/login", "post"}, {"/api/auth/heartbeat", "post"},
                {"/api/auth/session", "get"}, {"/api/auth/logout", "post"},
                {"/api/config", "get"}, {"/api/faces", "get"}, {"/api/profiles", "post"},
                {"/api/profiles/me", "get"}, {"/api/profiles/{id}/embeddings", "put"},
                {"/api/profiles/{id}/avatar", "put"}, {"/api/profiles/{id}", "delete"},
                {"/api/apps", "get"}, {"/api/apps", "put"},
                {"/api/weight", "get"}, {"/api/weight", "post"},
                {"/api/weight/{id}", "put"}, {"/api/weight/{id}", "delete"},
                {"/api/weather/cities", "get"}, {"/api/weather/location", "put"},
                {"/api/weather", "get"}, {"/root.crt", "get"}};

        for (String[] operation : operations) {
            var documented = paths.path(operation[0]).path(operation[1]);
            assertThat(documented.path("summary").asString()).as(operation[1] + " " + operation[0]).isNotBlank();
            assertThat(documented.path("tags").isArray()).as(operation[1] + " " + operation[0] + " tags").isTrue();
            assertThat(documented.path("responses").size()).as(operation[1] + " " + operation[0] + " responses")
                    .isPositive();
        }
    }

    @Test
    void adminAuthenticationIsSeparateAndPurgeOnlyShowsSummaryData() throws Exception {
        long profileId = createProfile("Admin cible", 1.5f);
        Cookie faceCookie = login(profileId);

        mvc.perform(get("/admin").cookie(faceCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/admin/login")));
        mvc.perform(get("/admin/login"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"password\"")))
                .andExpect(content().string(containsString("/css/theme.css")));
        mvc.perform(get("/admin/backup").cookie(faceCookie))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/admin/login").with(csrf())
                        .param("username", "admin")
                        .param("password", "incorrect-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/admin/login?error")));

        MvcResult loginResult = mvc.perform(post("/admin/login").with(csrf())
                        .param("username", "admin")
                        .param("password", "integration-admin-password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"))
                .andReturn();
        MockHttpSession adminSession = (MockHttpSession) loginResult.getRequest().getSession(false);
        assertThat(adminSession).isNotNull();

        String page = mvc.perform(get("/admin").session(adminSession))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(page).contains("Admin cible").doesNotContain("embedding_json").doesNotContain("embeddingCount");
        assertThat(page).contains("Télécharger la sauvegarde").contains("backup-restore-form")
                .contains("name=\"confirmed\" value=\"false\"");

        mvc.perform(post("/admin/profiles/" + profileId + "/purge").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"));
        mvc.perform(get("/api/profiles/me").cookie(faceCookie)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profileId\":" + profileId + "}"))
                .andExpect(status().isNotFound());

        long otherProfileId = createProfile("Session toujours active", 4f);
        Cookie otherFaceCookie = login(otherProfileId);

        mvc.perform(post("/admin/logout").session(adminSession).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").session(adminSession))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/admin/login")));
        mvc.perform(get("/api/profiles/me").cookie(otherFaceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Session toujours active"));
    }

    @Test
    void adminBackupStreamsAndRestoresAllDataWhileInvalidatingFaceSessions() throws Exception {
        long profileId = createProfile("Sauvegarde round-trip", 2.1f);
        Cookie faceCookie = login(profileId);
        mvc.perform(post("/api/weight").cookie(faceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-10-27\",\"value\":\"72,5\"}"))
                .andExpect(status().isCreated());

        MockHttpSession admin = adminSession();
        MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();
        MvcResult download = mvc.perform(asyncDispatch(pendingDownload))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("assistant-backup-")))
                .andReturn();
        byte[] backup = download.getResponse().getContentAsByteArray();
        var json = objectMapper.readTree(backup);
        assertThat(json.path("version").intValue()).isEqualTo(1);
        assertThat(json.path("tables").has("profile")).isTrue();
        assertThat(json.path("tables").has("weight_entry")).isTrue();
        assertThat(json.path("tables").has("app_session")).isFalse();

        jdbcTemplate.update("update profile set name = ? where id = ?", "Donnée modifiée", profileId);
        MockMultipartFile file = new MockMultipartFile("backupFile", "assistant.json", "application/json", backup);
        mvc.perform(multipart("/admin/backup").file(file).param("confirmed", "true")
                        .session(admin).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"));

        mvc.perform(get("/api/profiles/me").cookie(faceCookie)).andExpect(status().isUnauthorized());
        Cookie restoredCookie = login(profileId);
        mvc.perform(get("/api/profiles/me").cookie(restoredCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sauvegarde round-trip"));
        mvc.perform(get("/api/weight").cookie(restoredCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].kgTenths").value(725));
    }

    @Test
    void backupRejectsInvalidAndOversizedFilesWithoutChangingData() throws Exception {
        long profileId = createProfile("Sauvegarde protégée", 2.8f);
        Cookie faceCookie = login(profileId);
        MockHttpSession admin = adminSession();

        MockMultipartFile invalid = new MockMultipartFile("backupFile", "invalid.json", "application/json",
                "{\"version\":99,\"exportedAt\":\"2026-10-27T00:00:00Z\",\"tables\":{}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(multipart("/admin/backup").file(invalid).param("confirmed", "true")
                        .session(admin).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/profiles/me").cookie(faceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sauvegarde protégée"));

        MockMultipartFile oversized = new MockMultipartFile("backupFile", "large.json", "application/json",
                new byte[1_048_577]);
        mvc.perform(multipart("/admin/backup").file(oversized).param("confirmed", "true")
                        .session(admin).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
        mvc.perform(get("/api/profiles/me").cookie(faceCookie)).andExpect(status().isOk());
    }

    @Test
    void adminProxyIsSavedWithoutExposingPasswordAndExcludedFromBackup() throws Exception {
        MockHttpSession admin = adminSession();
        try {
            mvc.perform(post("/admin/proxy").session(admin).with(csrf())
                            .param("enabled", "true").param("url", "http://proxy.lan:3128")
                            .param("username", "svc").param("password", "secret-proxy-pass")
                            .param("noProxy", "intranet.local"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "/admin"));
            assertThat(jdbcTemplate.queryForObject("select password from proxy_setting", String.class))
                    .isEqualTo("secret-proxy-pass");

            String page = mvc.perform(get("/admin").session(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains("http://proxy.lan:3128").contains("Tester la connexion")
                    .doesNotContain("secret-proxy-pass");

            MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                    .andExpect(request().asyncStarted()).andReturn();
            byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(backup, java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("secret-proxy-pass").doesNotContain("proxy_setting");

            MockMultipartFile file = new MockMultipartFile("backupFile", "assistant.json", "application/json", backup);
            mvc.perform(multipart("/admin/backup").file(file).param("confirmed", "true")
                            .session(admin).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select url from proxy_setting", String.class))
                    .isEqualTo("http://proxy.lan:3128");

            mvc.perform(post("/admin/proxy").with(csrf()).param("url", "http://proxy.lan:3128"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("/admin/login")));
        } finally {
            mvc.perform(post("/admin/proxy").session(admin).with(csrf()).param("enabled", "false"));
        }
    }

    @Test
    void adminPrimKeyIsSavedHiddenBackedUpAndClearedByAnOldDump() throws Exception {
        MockHttpSession admin = adminSession();
        try {
            mvc.perform(post("/admin/prim").session(admin).with(csrf()).param("apiKey", "prim-secret-key"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "/admin"));
            assertThat(jdbcTemplate.queryForObject("select api_key from prim_setting", String.class))
                    .isEqualTo("prim-secret-key");

            String page = mvc.perform(get("/admin").session(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains("Clé PRIM configurée").doesNotContain("prim-secret-key");

            mvc.perform(post("/admin/prim").session(admin).with(csrf()).param("apiKey", " "))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select api_key from prim_setting", String.class))
                    .isEqualTo("prim-secret-key");

            MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                    .andExpect(request().asyncStarted()).andReturn();
            byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            var json = objectMapper.readTree(backup);
            assertThat(json.path("tables").path("prim_setting").get(0).path("apiKey").stringValue())
                    .isEqualTo("prim-secret-key");

            jdbcTemplate.update("update prim_setting set api_key = ?", "autre");
            restore(admin, backup).andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select api_key from prim_setting", String.class))
                    .isEqualTo("prim-secret-key");

            ((tools.jackson.databind.node.ObjectNode) json.path("tables")).remove("prim_setting");
            restore(admin, objectMapper.writeValueAsBytes(json)).andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select count(*) from prim_setting", Integer.class)).isZero();

            ((tools.jackson.databind.node.ObjectNode) json.path("tables")).remove("profile");
            restore(admin, objectMapper.writeValueAsBytes(json)).andExpect(status().isBadRequest());

            mvc.perform(post("/admin/prim").with(csrf()).param("apiKey", "x"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("/admin/login")));
        } finally {
            mvc.perform(post("/admin/prim/delete").session(admin).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
        assertThat(jdbcTemplate.queryForObject("select count(*) from prim_setting", Integer.class)).isZero();
    }

    @Test
    void adminNewsCategoriesAreValidatedBackedUpAndClearedByAnOldDump() throws Exception {
        MockHttpSession admin = adminSession();
        try {
            mvc.perform(post("/admin/news-categories").session(admin).with(csrf())
                            .param("name", "Tech").param("feedUrl", "https://example.org/rss").param("position", "2"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", "/admin"));
            mvc.perform(post("/admin/news-categories").session(admin).with(csrf())
                            .param("name", "Local").param("feedUrl", "http://example.org/rss"))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(post("/admin/news-categories").session(admin).with(csrf())
                            .param("name", "Prive").param("feedUrl", "https://192.168.1.10/rss"))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select count(*) from news_category", Integer.class)).isEqualTo(1);

            String page = mvc.perform(get("/admin").session(admin)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(page).contains("Tech").contains("https://example.org/rss");

            MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                    .andExpect(request().asyncStarted()).andReturn();
            byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            var json = objectMapper.readTree(backup);
            assertThat(json.path("tables").path("news_category")).hasSize(1);
            assertThat(json.path("tables").path("news_category").get(0).path("feedUrl").stringValue())
                    .isEqualTo("https://example.org/rss");

            jdbcTemplate.update("delete from news_category");
            restore(admin, backup).andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select count(*) from news_category", Integer.class)).isEqualTo(1);

            ((tools.jackson.databind.node.ObjectNode) json.path("tables")).remove("news_category");
            restore(admin, objectMapper.writeValueAsBytes(json)).andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select count(*) from news_category", Integer.class)).isZero();

            mvc.perform(post("/admin/news-categories").with(csrf()).param("name", "x")
                            .param("feedUrl", "https://example.org/rss"))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Location", containsString("/admin/login")));
        } finally {
            jdbcTemplate.update("delete from news_category");
        }
    }

    private org.springframework.test.web.servlet.ResultActions restore(MockHttpSession admin, byte[] content)
            throws Exception {
        MockMultipartFile file = new MockMultipartFile("backupFile", "assistant.json", "application/json", content);
        return mvc.perform(multipart("/admin/backup").file(file).param("confirmed", "true")
                .session(admin).with(csrf()));
    }

    @Test
    void backupInsertionFailureRollsBackDeletedRows() throws Exception {
        long profileId = createProfile("Rollback source", 3.8f);
        MockHttpSession admin = adminSession();
        MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                .andExpect(request().asyncStarted())
                .andReturn();
        byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        var json = objectMapper.readTree(backup);
        ((tools.jackson.databind.node.ObjectNode) json.path("tables").path("profile").get(0))
                .put("name", "Rollback failure");
        byte[] failingBackup = objectMapper.writeValueAsBytes(json);

        jdbcTemplate.execute("CREATE TRIGGER reject_backup_failure BEFORE INSERT ON profile "
                + "WHEN NEW.name = 'Rollback failure' BEGIN SELECT RAISE(ABORT, 'rejected'); END");
        try {
            MockMultipartFile file = new MockMultipartFile("backupFile", "failing.json", "application/json", failingBackup);
            mvc.perform(multipart("/admin/backup").file(file).param("confirmed", "true")
                            .session(admin).with(csrf()))
                    .andExpect(status().isInternalServerError())
                    .andExpect(content().string(containsString("données précédentes ont été conservées")));
            assertThat(jdbcTemplate.queryForObject("select name from profile where id = ?", String.class, profileId))
                    .isEqualTo("Rollback source");
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS reject_backup_failure");
        }
    }

    @Test
    void adminManagesAnnouncementsSharedByAllProfilesAndBackupIncludesThem() throws Exception {
        long profileId = createProfile("Annonces lecteur", 0.3f);
        Cookie faceCookie = login(profileId);
        mvc.perform(get("/api/announcements")).andExpect(status().isUnauthorized());
        mvc.perform(post("/admin/announcements").with(csrf()).param("title", "Interdit")
                        .param("startsAt", "2020-01-01T00:00").param("endsAt", "2999-01-01T00:00")
                        .cookie(faceCookie))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/admin/login")));

        MockHttpSession admin = adminSession();
        mvc.perform(post("/admin/announcements").session(admin).with(csrf()).param("title", "Annonce IT")
                        .param("startsAt", "2020-01-01T00:00").param("endsAt", "2999-01-01T00:00")
                        .param("countdown", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/admin"));
        mvc.perform(post("/admin/announcements").session(admin).with(csrf()).param("title", "Incohérente")
                        .param("startsAt", "2999-01-01T00:00").param("endsAt", "2020-01-01T00:00"))
                .andExpect(status().is3xxRedirection())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .flash().attribute("notice", "La date de fin doit être postérieure à la date de début."));
        mvc.perform(post("/admin/announcements").session(admin).with(csrf()).param("title", "Expirée")
                        .param("startsAt", "2019-01-01T00:00").param("endsAt", "2020-01-01T00:00"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(get("/admin").session(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Annonce IT")))
                .andExpect(content().string(containsString("En cours")))
                .andExpect(content().string(containsString("Expirée")));

        mvc.perform(get("/api/announcements").cookie(faceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serverNow").isNumber())
                .andExpect(jsonPath("$.announcements.length()").value(1))
                .andExpect(jsonPath("$.announcements[0].title").value("Annonce IT"))
                .andExpect(jsonPath("$.announcements[0].countdown").value(true));

        MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                .andExpect(request().asyncStarted()).andReturn();
        byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(objectMapper.readTree(backup).path("tables").path("announcement").size()).isEqualTo(2);

        for (Long id : jdbcTemplate.queryForList("select id from announcement", Long.class)) {
            mvc.perform(post("/admin/announcements/" + id + "/delete").session(admin).with(csrf()))
                    .andExpect(status().is3xxRedirection());
        }
        mvc.perform(get("/api/announcements").cookie(faceCookie))
                .andExpect(jsonPath("$.announcements.length()").value(0));
    }

    @Test
    void appRegistryDiscoversWidgetsAndIsolatesPreferencesAndFragments() throws Exception {
        long alice = createProfile("Widget Alice", 2.3f);
        long bob = createProfile("Widget Bob", 4.3f);
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);

        mvc.perform(get("/api/apps"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/apps").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='sample-widget')].enabled").value(false))
                .andExpect(jsonPath("$[?(@.key=='news')].title").value("Actualités"));

        mvc.perform(put("/api/apps").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[{\"key\":\"sample-widget\",\"enabled\":true,"
                                + "\"position\":0,\"configJson\":\"{\\\"message\\\":\\\"Alice\\\"}\"}]}"))
                .andExpect(status().isOk());
        var samplePreference = appPreference(aliceCookie, "sample-widget");
        assertThat(samplePreference.path("enabled").asBoolean()).isTrue();
        assertThat(samplePreference.path("position").asInt()).isZero();

        mvc.perform(get("/api/apps").cookie(bobCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enabled").value(false));
        mvc.perform(get("/apps/sample-widget/fragment").cookie(bobCookie))
                .andExpect(status().isNotFound());
        mvc.perform(get("/apps/sample-widget/fragment"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/apps/sample-widget/fragment").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Widget d’intégration")))
                .andExpect(content().string(containsString("Alice")));

        mvc.perform(put("/api/apps").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[{\"key\":\"sample-widget\",\"enabled\":false,"
                                + "\"position\":-1,\"configJson\":\"{}\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/apps/sample-widget/fragment").cookie(aliceCookie))
                .andExpect(status().isNotFound());
    }

    @Test
    void transitCatalogFavoritesAndTestModeUseTheLocalFixtureWithoutQuota() throws Exception {
        long alice = createProfile("Transit Alice", -4.4f);
        long bob = createProfile("Transit Bob", -3.2f);
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);
        MockHttpSession admin = adminSession();
        List<String> favoriteIds = List.of("C01739", "C01742", "C01727", "C01729");
        try {
            mvc.perform(get("/api/transit/lines").param("query", "RER A"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get("/api/transit/lines").cookie(aliceCookie).param("query", "RER A"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("C01742"))
                    .andExpect(jsonPath("$[0].label").value("RER A"));
            mvc.perform(get("/api/transit/lines").cookie(aliceCookie).param("query", "Ligne J"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value("C01739"));

            mvc.perform(put("/api/transit/favorites").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(java.util.Map.of("lineIds", favoriteIds))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineIds.length()").value(4));
            mvc.perform(put("/api/transit/favorites").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lineIds\":[\"C01739\",\"C01742\",\"C01727\",\"C01729\",\"C01730\"]}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(put("/api/transit/favorites").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"lineIds\":[\"C01739\",\"C01739\"]}"))
                    .andExpect(status().isBadRequest());
            mvc.perform(get("/api/transit/favorites").cookie(aliceCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineIds.length()").value(4));
            mvc.perform(get("/api/transit/favorites").cookie(bobCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.lineIds.length()").value(0));
            mvc.perform(get("/api/transit/alerts").cookie(bobCookie))
                    .andExpect(status().isNotFound());

            profileAppService.replaceAll(alice, List.of(
                    new ProfileAppService.Selection("weight", true, 0, "{}"),
                    new ProfileAppService.Selection("transit", true, 1,
                            objectMapper.writeValueAsString(java.util.Map.of("favoriteLineIds", favoriteIds))),
                    new ProfileAppService.Selection("weather", true, 2, objectMapper.writeValueAsString(
                            java.util.Map.of("location", java.util.Map.of("id", 2996944, "name", "Lyon",
                                    "region", "Auvergne-Rhône-Alpes", "country", "France",
                                    "latitude", 45.75, "longitude", 4.85))))));
            mvc.perform(post("/admin/external-api-mode").session(admin).with(csrf()).param("testMode", "true"))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get("/apps/transit/fragment").cookie(aliceCookie))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("MODE TEST")));
            mvc.perform(get("/apps/weather/fragment").cookie(aliceCookie))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("MODE TEST")));
            mvc.perform(get("/api/weather/cities").cookie(aliceCookie).param("query", "Tokyo"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$[0].name").value("Paris"));
            mvc.perform(get("/api/weather").cookie(aliceCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.location.name").value("Paris"))
                    .andExpect(jsonPath("$.testMode").value(true))
                    .andExpect(jsonPath("$.days.length()").value(4))
                    .andExpect(jsonPath("$.days[0].morning.code").value(2))
                    .andExpect(jsonPath("$.days[2].windAlert").value(true))
                    .andExpect(jsonPath("$.days[1].advice").value("Prends un parapluie et une veste."));
            assertThat(profileAppService.listForProfile(alice).stream()
                    .filter(app -> "weather".equals(app.key())).findFirst().orElseThrow().configJson())
                    .contains("Lyon");
            mvc.perform(get("/api/transit/alerts").cookie(aliceCookie))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.testMode").value(true))
                    .andExpect(jsonPath("$.lines.length()").value(4))
                    .andExpect(jsonPath("$.fetchedAt").isNotEmpty())
                    .andExpect(jsonPath("$.notice").value(containsString("TEST")));
            assertThat(apiQuotaService.used("prim")).isZero();
            assertThat(jdbcTemplate.queryForObject("select test_mode from external_api_mode where id = 1", Boolean.class))
                    .isTrue();

            MvcResult pendingDownload = mvc.perform(get("/admin/backup").session(admin))
                    .andExpect(request().asyncStarted()).andReturn();
            byte[] backup = mvc.perform(asyncDispatch(pendingDownload)).andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsByteArray();
            assertThat(new String(backup, java.nio.charset.StandardCharsets.UTF_8))
                    .doesNotContain("external_api_mode");
            MockMultipartFile backupFile = new MockMultipartFile("backupFile", "assistant.json",
                    "application/json", backup);
            mvc.perform(multipart("/admin/backup").file(backupFile).param("confirmed", "true")
                            .session(admin).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            assertThat(jdbcTemplate.queryForObject("select test_mode from external_api_mode where id = 1", Boolean.class))
                    .isTrue();
        } finally {
            mvc.perform(post("/admin/external-api-mode").session(admin).with(csrf()).param("testMode", "false"));
        }
    }

    @Test
    void configurationPageRequiresSessionAndContainsFiveFixedTargets() throws Exception {
        mvc.perform(get("/configuration")).andExpect(status().isUnauthorized());

        long profileId = createProfile("Configuration", 1.2f);
        Cookie cookie = login(profileId);
        String page = mvc.perform(get("/configuration").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"widget-pool\"")))
                .andExpect(content().string(containsString("id=\"profile-menu-trigger\"")))
                .andReturn().getResponse().getContentAsString();

        assertThat(java.util.regex.Pattern.compile("data-slot-index=\\\"[0-4]\\\"")
                .matcher(page).results().count()).isEqualTo(5);
        mvc.perform(get("/js/configuration.js")).andExpect(status().isOk());
        mvc.perform(get("/css/home.css")).andExpect(status().isOk());
    }

    @Test
    void fixedSlotAssignmentsPersistPerProfileAndNormalizeLegacyPositions() throws Exception {
        long alice = createProfile("Slots Alice", 1.3f);
        long bob = createProfile("Slots Bob", 3.3f);
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);
        var aliceProfile = profileRepository.findById(alice).orElseThrow();
        profileAppRepository.save(new ProfileApp(aliceProfile, "weather", 9, true,
                "{\"location\":{\"name\":\"Lyon\"}}"));

        var weatherPreference = appPreference(aliceCookie, "weather");
        var weightPreference = appPreference(aliceCookie, "weight");
        assertThat(weatherPreference.path("position").asInt()).isZero();
        assertThat(weatherPreference.path("configJson").asString()).isEqualTo("{\"location\":{\"name\":\"Lyon\"}}");
        assertThat(weightPreference.path("position").asInt()).isEqualTo(1);

        mvc.perform(put("/api/apps").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":["
                                + "{\"key\":\"sample-widget\",\"enabled\":false,\"position\":-1,\"configJson\":\"{}\"},"
                                + "{\"key\":\"weather\",\"enabled\":true,\"position\":2,\"configJson\":\"{}\"},"
                                + "{\"key\":\"weight\",\"enabled\":true,\"position\":4,\"configJson\":\"{}\"}]}"))
                .andExpect(status().isOk());
        assertThat(appPreference(aliceCookie, "weather").path("position").asInt()).isEqualTo(2);
        assertThat(appPreference(aliceCookie, "weight").path("position").asInt()).isEqualTo(4);
        assertThat(appPreference(bobCookie, "weight").path("position").asInt()).isZero();

        mvc.perform(put("/api/apps").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":["
                                + "{\"key\":\"sample-widget\",\"enabled\":true,\"position\":1,\"configJson\":\"{}\"},"
                                + "{\"key\":\"weather\",\"enabled\":true,\"position\":1,\"configJson\":\"{}\"}]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weightHistoryIsPagedAndOnlyAccessibleToItsOwner() throws Exception {
        long alice = createProfile("Poids Alice", 1.1f);
        long bob = createProfile("Poids Bob", 3.1f);
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);

        mvc.perform(get("/api/weight")).andExpect(status().isUnauthorized());
        MvcResult created = mvc.perform(post("/api/weight").cookie(aliceCookie)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-10-27\",\"value\":\"72,5\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.kgTenths").value(725))
                .andReturn();
        long entryId = Long.parseLong(created.getResponse().getContentAsString()
                .replaceAll(".*\\\"id\\\":(\\d+).*", "$1"));

        mvc.perform(post("/api/weight").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-10-26\",\"value\":\"71.2\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/weight?page=0&size=1").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/weight").cookie(bobCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.entries.length()").value(0));
        mvc.perform(put("/api/weight/" + entryId).cookie(bobCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-10-27\",\"value\":\"73\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/weight/" + entryId).cookie(bobCookie))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/weight").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"entryDate\":\"2026-10-25\",\"value\":\"72,55\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void weightWidgetIsEnabledByDefaultAndLoadsItsChartForANewProfile() throws Exception {
        long profileId = createProfile("Poids par défaut", 1.7f);
        Cookie cookie = login(profileId);

        assertThat(appPreference(cookie, "weight").path("enabled").asBoolean()).isTrue();
        mvc.perform(get("/apps/weight/fragment").cookie(cookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("data-weight-chart")))
                .andExpect(content().string(containsString("data-weight-labels")))
                .andExpect(content().string(containsString("data-weight-open")))
                .andExpect(content().string(containsString("data-weight-form")));
    }

    @Test
        void weatherLocationCanBeConfiguredBeforePlacementAndIsStoredPerProfile() throws Exception {
        long alice = createProfile("Météo Alice", 0.6f);
        long bob = createProfile("Météo Bob", 2.6f);
        Cookie aliceCookie = login(alice);
        Cookie bobCookie = login(bob);

        mvc.perform(get("/api/weather/cities?query=Lyon")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/weather/location").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"Lyon\",\"region\":\"Auvergne-Rhône-Alpes\","
                                + "\"country\":\"France\",\"latitude\":45.75,\"longitude\":4.85}"))
                .andExpect(status().isOk());

        mvc.perform(put("/api/apps").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"selections\":[{\"key\":\"weather\",\"enabled\":true,"
                                + "\"position\":0,\"configJson\":\"{}\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/weather").cookie(aliceCookie)).andExpect(status().isBadRequest());
        mvc.perform(put("/api/weather/location").cookie(aliceCookie).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"Lyon\",\"region\":\"Auvergne-Rhône-Alpes\","
                                + "\"country\":\"France\",\"latitude\":45.75,\"longitude\":4.85}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lyon"));
        String aliceApps = mvc.perform(get("/api/apps").cookie(aliceCookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(aliceApps).contains("Lyon");
        String bobApps = mvc.perform(get("/api/apps").cookie(bobCookie))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(bobApps).doesNotContain("Lyon");
        mvc.perform(get("/api/weather").cookie(bobCookie)).andExpect(status().isNotFound());
        mvc.perform(get("/apps/weather/fragment").cookie(aliceCookie))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Lyon")))
                .andExpect(content().string(containsString("data-weather-current")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/configuration"))));
    }
}
