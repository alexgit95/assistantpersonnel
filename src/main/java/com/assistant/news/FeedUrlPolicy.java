package com.assistant.news;

import com.assistant.common.ApiException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Politique d'URL des flux : https uniquement et jamais vers le reseau local. */
@Component
public class FeedUrlPolicy {

    private static final Pattern LITERAL_HOST = Pattern.compile("[0-9a-fA-FxX.:\\[\\]]+");

    @FunctionalInterface
    interface Resolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final Resolver resolver;

    public FeedUrlPolicy() {
        this(InetAddress::getAllByName);
    }

    FeedUrlPolicy(Resolver resolver) {
        this.resolver = resolver;
    }

    /** Validation a la saisie : syntaxe, https et hote litteral local, sans resolution DNS. */
    public URI validateForSave(String raw) {
        URI uri = parse(raw);
        String host = uri.getHost();
        if (isLocalName(host)) {
            throw invalid("L'URL ne doit pas cibler une adresse locale.");
        }
        if (LITERAL_HOST.matcher(host).matches()) {
            try {
                for (InetAddress address : resolver.resolve(stripBrackets(host))) {
                    if (isLocal(address)) {
                        throw invalid("L'URL ne doit pas cibler une adresse locale.");
                    }
                }
            } catch (UnknownHostException exception) {
                // Nom non litteral : il sera resolu et controle a chaque recuperation.
            }
        }
        return uri;
    }

    /** Controle a chaque recuperation : toutes les adresses resolues doivent etre publiques. */
    public URI requirePublic(String raw) {
        URI uri;
        try {
            uri = validateForSave(raw);
        } catch (ApiException exception) {
            throw new NewsUnavailableException(exception.getMessage());
        }
        try {
            InetAddress[] addresses = resolver.resolve(stripBrackets(uri.getHost()));
            if (addresses.length == 0) {
                throw new NewsUnavailableException("Hôte introuvable.");
            }
            for (InetAddress address : addresses) {
                if (isLocal(address)) {
                    throw new NewsUnavailableException("Adresse locale refusée.");
                }
            }
        } catch (UnknownHostException exception) {
            throw new NewsUnavailableException("Hôte introuvable.", exception);
        }
        return uri;
    }

    static boolean isLocal(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == 16) {
            return (bytes[0] & 0xFE) == 0xFC;
        }
        return (bytes[0] & 0xFF) == 100 && (bytes[1] & 0xC0) == 64;
    }

    private static URI parse(String raw) {
        if (raw == null || raw.isBlank() || raw.strip().length() > NewsCategoryService.MAX_URL_LENGTH) {
            throw invalid("L'URL du flux est absente ou trop longue.");
        }
        URI uri;
        try {
            uri = new URI(raw.strip());
        } catch (URISyntaxException exception) {
            throw invalid("L'URL du flux est invalide.");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw invalid("L'URL du flux doit commencer par https://.");
        }
        if (uri.getHost() == null || uri.getHost().isBlank() || uri.getUserInfo() != null) {
            throw invalid("L'URL du flux est invalide.");
        }
        return uri;
    }

    private static boolean isLocalName(String host) {
        String name = host.toLowerCase(Locale.ROOT);
        return name.equals("localhost") || name.endsWith(".localhost") || name.endsWith(".local");
    }

    private static String stripBrackets(String host) {
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    private static ApiException invalid(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
