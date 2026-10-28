package com.assistant.proxy;

import com.assistant.common.ApiException;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProxySettingsService {

    static final long SETTING_ID = 1L;
    static final String DEFAULT_TEST_URL = "https://geocoding-api.open-meteo.com/v1/search?name=Paris&count=1";

    private static final int MAX_URL = 300;
    private static final int MAX_CREDENTIAL = 200;
    private static final int MAX_NO_PROXY = 500;

    private final ProxySettingRepository repository;
    private final String testUrl;

    @Autowired
    public ProxySettingsService(ProxySettingRepository repository) {
        this(repository, DEFAULT_TEST_URL);
    }

    ProxySettingsService(ProxySettingRepository repository, String testUrl) {
        this.repository = repository;
        this.testUrl = testUrl;
    }

    /** Saisie du formulaire ; un mot de passe vide conserve la valeur enregistree. */
    public record ProxyInput(boolean enabled, String url, String username, String password, String noProxy) {
    }

    /** Vue sans le mot de passe, destinee a l'affichage. */
    public record ProxyView(boolean enabled, String url, String username, boolean hasPassword, String noProxy) {
    }

    public record TestResult(boolean success, String message) {
    }

    @PostConstruct
    void applyStoredSetting() {
        OutboundHttp.configure(repository.findById(SETTING_ID).map(ProxySettingsService::toConfig)
                .orElse(ProxyConfig.DIRECT));
    }

    @Transactional(readOnly = true)
    public ProxyView current() {
        return repository.findById(SETTING_ID)
                .map(setting -> new ProxyView(setting.isEnabled(), nullToEmpty(setting.getUrl()),
                        nullToEmpty(setting.getUsername()), notBlank(setting.getPassword()),
                        nullToEmpty(setting.getNoProxy())))
                .orElse(new ProxyView(false, "", "", false, ""));
    }

    @Transactional
    public void save(ProxyInput input) {
        ProxySetting setting = repository.findById(SETTING_ID).orElseGet(() -> new ProxySetting(SETTING_ID));
        ProxyConfig config = buildConfig(input, setting.getPassword());
        setting.setEnabled(input.enabled());
        setting.setUrl(strip(input.url()));
        setting.setUsername(strip(input.username()));
        setting.setPassword(notBlank(input.password()) ? input.password() : setting.getPassword());
        setting.setNoProxy(strip(input.noProxy()));
        repository.save(setting);
        OutboundHttp.configure(config);
    }

    /** Appelle un point public via la configuration saisie, sans l'enregistrer ni toucher au budget PRIM. */
    public TestResult test(ProxyInput input) {
        ProxyConfig config;
        try {
            String stored = repository.findById(SETTING_ID).map(ProxySetting::getPassword).orElse(null);
            config = buildConfig(new ProxyInput(true, input.url(), input.username(), input.password(),
                    input.noProxy()), stored);
        } catch (ApiException exception) {
            return new TestResult(false, exception.getMessage());
        }
        try {
            var client = OutboundHttp.clientBuilderFor(config).connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(testUrl)).timeout(Duration.ofSeconds(5)).GET()
                    .build();
            int status = client.send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            if (status == 407) {
                return new TestResult(false, "Le proxy a refusé l'authentification.");
            }
            return status < 400
                    ? new TestResult(true, "Connexion réussie via le proxy.")
                    : new TestResult(false, "Le proxy a répondu avec le code " + status + ".");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return new TestResult(false, "Le test a été interrompu.");
        } catch (Exception exception) {
            String detail = String.valueOf(exception.getMessage()).toLowerCase(Locale.ROOT);
            if (detail.contains("407") || detail.contains("authenticat") || detail.contains("credential")) {
                return new TestResult(false, "Le proxy a refusé l'authentification.");
            }
            return new TestResult(false, "Proxy injoignable ou délai dépassé.");
        }
    }

    static ProxyConfig buildConfig(ProxyInput input, String storedPassword) {
        String url = strip(input.url());
        String username = strip(input.username());
        String noProxy = strip(input.noProxy());
        if (url.length() > MAX_URL || username.length() > MAX_CREDENTIAL || noProxy.length() > MAX_NO_PROXY
                || (input.password() != null && input.password().length() > MAX_CREDENTIAL)) {
            throw invalid("Une valeur du proxy dépasse la longueur autorisée.");
        }
        if (hasControlCharacters(url) || hasControlCharacters(username) || hasControlCharacters(noProxy)
                || hasControlCharacters(input.password())) {
            throw invalid("Les caractères de contrôle ne sont pas autorisés.");
        }
        if (url.isEmpty()) {
            if (input.enabled()) {
                throw invalid("L'URL du proxy est obligatoire pour l'activer.");
            }
            return ProxyConfig.DIRECT;
        }
        URI uri = parseUrl(url);
        int port = uri.getPort() == -1 ? ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80) : uri.getPort();
        String password = notBlank(input.password()) ? input.password() : storedPassword;
        return new ProxyConfig(input.enabled(), uri.getHost(), port, username.isEmpty() ? null : username,
                username.isEmpty() ? null : password, parseNoProxy(noProxy));
    }

    private static URI parseUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException exception) {
            throw invalid("L'URL du proxy est invalide.");
        }
        String scheme = uri.getScheme();
        boolean schemeValid = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        boolean pathValid = uri.getRawPath() == null || uri.getRawPath().isEmpty() || "/".equals(uri.getRawPath());
        boolean portValid = uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535);
        if (!schemeValid || uri.getHost() == null || !pathValid || !portValid || uri.getRawUserInfo() != null
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw invalid("L'URL du proxy doit être de la forme http://hôte:port.");
        }
        return uri;
    }

    private static List<String> parseNoProxy(String value) {
        List<String> entries = new ArrayList<>();
        for (String raw : value.split("[,;\\s]+")) {
            String entry = raw.strip().toLowerCase(Locale.ROOT);
            while (entry.startsWith("*") || entry.startsWith(".")) {
                entry = entry.substring(1);
            }
            if (!entry.isEmpty()) {
                entries.add(entry);
            }
        }
        return List.copyOf(entries);
    }

    private static ProxyConfig toConfig(ProxySetting setting) {
        try {
            return buildConfig(new ProxyInput(setting.isEnabled(), setting.getUrl(), setting.getUsername(),
                    setting.getPassword(), setting.getNoProxy()), setting.getPassword());
        } catch (ApiException exception) {
            return ProxyConfig.DIRECT;
        }
    }

    private static boolean hasControlCharacters(String value) {
        return value != null && value.chars().anyMatch(Character::isISOControl);
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String strip(String value) {
        return value == null ? "" : value.strip();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
