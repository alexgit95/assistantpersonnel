package com.assistant.qr;

import com.assistant.config.AssistantProperties;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Construit cote serveur les URL encodees dans les QR : jamais depuis la page du Pi quand elle est en localhost.
 * Priorite : ASSISTANT_PUBLIC_BASE_URL ; sinon, en mode developpement, la requete avec localhost et 127.0.0.1 inverses
 * (cookies separes entre la page du Pi et l'onglet qui simule le smartphone) ; sinon, en mode HTTPS + HTTP (remote),
 * http://hote-de-la-requete:port-public ; sinon pas de QR. Les appelants sont reserves au kiosque (cle verifiee).
 */
@Component
public class QrUrlResolver {

    private static final Logger LOG = LoggerFactory.getLogger(QrUrlResolver.class);
    private static final String LOCALHOST = "localhost";
    private static final String LOOPBACK = "127.0.0.1";
    private static final Pattern SAFE_HOST = Pattern.compile(
            "^(?:[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]{2,45}\\])$");

    private final AssistantProperties properties;
    private final AtomicBoolean disabledWarned = new AtomicBoolean();

    public QrUrlResolver(AssistantProperties properties) {
        this.properties = properties;
    }

    /** Base des URL de QR (sans slash final), ou vide si les QR sont desactives. */
    public Optional<String> baseUrl(HttpServletRequest request) {
        String configured = properties.publicBaseUrl();
        if (!configured.isBlank()) {
            return Optional.of(configured);
        }
        if (properties.qr().devMode()) {
            Optional<String> swapped = swapLoopbackHost(request);
            if (swapped.isPresent()) {
                return swapped;
            }
        }
        if (dualProtocol()) {
            Optional<String> derived = deriveHttpBase(request);
            if (derived.isPresent()) {
                return derived;
            }
        }
        if (disabledWarned.compareAndSet(false, true)) {
            LOG.warn("QR desactives : ASSISTANT_PUBLIC_BASE_URL est vide{} (adresse IP du serveur, ex. http://192.168.1.50:8080).",
                    dualProtocol() ? " et l'hote de la requete est local ou invalide" : " (obligatoire hors mode dev)");
        }
        return Optional.empty();
    }

    /** URL complete pour un chemin commencant par « / » (ex. « /handoff?t=... »). */
    public Optional<String> resolve(HttpServletRequest request, String pathAndQuery) {
        return baseUrl(request).map(base -> base + pathAndQuery);
    }

    private boolean dualProtocol() {
        return properties.http().enabled() && properties.https().enabled();
    }

    /** Le kiosque est en HTTPS : les smartphones joignent le meme hote en HTTP sur le port public. */
    private Optional<String> deriveHttpBase(HttpServletRequest request) {
        String host = request.getServerName();
        if (host == null || LOCALHOST.equalsIgnoreCase(host) || LOOPBACK.equals(host) || !SAFE_HOST.matcher(host).matches()) {
            return Optional.empty();
        }
        int port = properties.http().publicPort();
        return Optional.of("http://" + host + (port == 80 ? "" : ":" + port));
    }

    private static Optional<String> swapLoopbackHost(HttpServletRequest request) {
        String host = request.getServerName();
        String other = LOCALHOST.equals(host) ? LOOPBACK : LOOPBACK.equals(host) ? LOCALHOST : null;
        if (other == null) {
            return Optional.empty();
        }
        String scheme = request.getScheme();
        int port = request.getServerPort();
        boolean defaultPort = port <= 0 || ("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443);
        return Optional.of(scheme + "://" + other + (defaultPort ? "" : ":" + port));
    }
}
