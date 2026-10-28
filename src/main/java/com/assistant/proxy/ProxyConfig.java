package com.assistant.proxy;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URI;
import java.util.List;
import java.util.Locale;

/** Configuration immuable d'un proxy HTTP sortant. */
public record ProxyConfig(boolean enabled, String host, int port, String username, String password,
                          List<String> noProxy) {

    public static final ProxyConfig DIRECT = new ProxyConfig(false, null, 0, null, null, List.of());

    public boolean hasCredentials() {
        return username != null && !username.isBlank();
    }

    /** Proxy a utiliser pour la cible, ou {@link Proxy#NO_PROXY}. */
    public Proxy proxyFor(URI target) {
        if (!enabled || host == null || target == null || target.getHost() == null) {
            return Proxy.NO_PROXY;
        }
        String scheme = target.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            return Proxy.NO_PROXY;
        }
        return excluded(target.getHost()) ? Proxy.NO_PROXY
                : new Proxy(Proxy.Type.HTTP, new InetSocketAddress(host, port));
    }

    private boolean excluded(String targetHost) {
        String name = targetHost.toLowerCase(Locale.ROOT);
        if (name.equals("localhost") || name.equals("127.0.0.1") || name.equals("[::1]") || name.equals("::1")) {
            return true;
        }
        for (String entry : noProxy) {
            if (name.equals(entry) || name.endsWith("." + entry)) {
                return true;
            }
        }
        return false;
    }
}
