package com.assistant.proxy;

import java.io.IOException;
import java.net.Authenticator;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/** Point d'entree des appels HTTP sortants : la configuration du proxy est relue a chaque requete. */
public final class OutboundHttp {

    private static final String TUNNELING_SCHEMES = "jdk.http.auth.tunneling.disabledSchemes";
    private static final AtomicReference<ProxyConfig> CONFIG = new AtomicReference<>(ProxyConfig.DIRECT);

    private static final ProxySelector SELECTOR = new ProxySelector() {
        @Override
        public List<Proxy> select(URI uri) {
            return List.of(CONFIG.get().proxyFor(uri));
        }

        @Override
        public void connectFailed(URI uri, SocketAddress address, IOException failure) {
            // Rien a faire : l'echec est remonte a l'appelant.
        }
    };

    private static final Authenticator AUTHENTICATOR = new Authenticator() {
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            ProxyConfig config = CONFIG.get();
            if (getRequestorType() != RequestorType.PROXY || !config.hasCredentials()) {
                return null;
            }
            String password = config.password() == null ? "" : config.password();
            return new PasswordAuthentication(config.username(), password.toCharArray());
        }
    };

    static {
        if (System.getProperty(TUNNELING_SCHEMES) == null) {
            System.setProperty(TUNNELING_SCHEMES, "");
        }
        ProxySelector.setDefault(SELECTOR);
        Authenticator.setDefault(AUTHENTICATOR);
    }

    private OutboundHttp() {
    }

    public static void configure(ProxyConfig config) {
        CONFIG.set(config == null ? ProxyConfig.DIRECT : config);
    }

    public static ProxyConfig current() {
        return CONFIG.get();
    }

    public static HttpClient.Builder clientBuilder() {
        return HttpClient.newBuilder().proxy(SELECTOR).authenticator(AUTHENTICATOR);
    }

    /** Client isole pour tester une configuration sans toucher a la configuration active. */
    static HttpClient.Builder clientBuilderFor(ProxyConfig config) {
        ProxySelector selector = new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                return List.of(config.proxyFor(uri));
            }

            @Override
            public void connectFailed(URI uri, SocketAddress address, IOException failure) {
                // Rien a faire.
            }
        };
        Authenticator authenticator = new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() != RequestorType.PROXY || !config.hasCredentials()) {
                    return null;
                }
                String password = config.password() == null ? "" : config.password();
                return new PasswordAuthentication(config.username(), password.toCharArray());
            }
        };
        return HttpClient.newBuilder().proxy(selector).authenticator(authenticator);
    }
}
