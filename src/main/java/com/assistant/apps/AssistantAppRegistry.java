package com.assistant.apps;

import com.assistant.common.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class AssistantAppRegistry {

    private static final Pattern KEY = Pattern.compile("^[a-z0-9]+(?:-[a-z0-9]+)*$");
    private static final Pattern FRAGMENT = Pattern.compile("^[a-zA-Z0-9/_-]+\\s*::\\s*[a-zA-Z0-9_-]+$");
    private static final Pattern WIDGET_MODULE = Pattern.compile("^/js/widgets/[a-z0-9-]+\\.js$");
    private final Map<String, AssistantApp> apps;

    public AssistantAppRegistry(List<AssistantApp> discoveredApps) {
        List<AssistantApp> ordered = discoveredApps.stream()
                .sorted(Comparator.comparing(AssistantApp::key))
                .toList();
        for (AssistantApp app : ordered) {
            if (app.key() == null || !KEY.matcher(app.key()).matches()) {
                throw new IllegalStateException("Cle d'application invalide : " + app.key());
            }
            String fragmentTemplate = app.fragmentTemplate();
                String widgetModule = app.widgetModule();
            if (app.title() == null || app.title().isBlank() || app.description() == null
                    || fragmentTemplate == null || !FRAGMENT.matcher(fragmentTemplate).matches()
                    || (widgetModule != null && !widgetModule.isBlank()
                        && !WIDGET_MODULE.matcher(widgetModule).matches())) {
                throw new IllegalStateException("Definition incomplete pour l'application " + app.key());
            }
        }
        this.apps = ordered.stream().collect(Collectors.toUnmodifiableMap(AssistantApp::key, app -> app));
    }

    public List<AssistantApp> all() {
        return apps.values().stream().sorted(Comparator.comparing(AssistantApp::key)).toList();
    }

    public AssistantApp require(String key) {
        AssistantApp app = apps.get(key);
        if (app == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Application introuvable.");
        }
        return app;
    }

    public boolean contains(String key) {
        return apps.containsKey(key);
    }
}
