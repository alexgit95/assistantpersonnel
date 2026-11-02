package com.assistant.config;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Mode remote : le port principal est en HTTPS (kiosque), ce connecteur additionnel sert les smartphones en HTTP. */
@Configuration(proxyBeanMethods = false)
public class HttpConnectorConfiguration {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> additionalHttpConnector(AssistantProperties properties) {
        return factory -> {
            AssistantProperties.Https https = properties.https();
            if (https.enabled() && !Files.isReadable(Path.of(https.keystorePath()))) {
                throw new IllegalStateException("Keystore HTTPS illisible : " + https.keystorePath()
                        + ". Fournir ASSISTANT_HTTPS_KEYSTORE_PATH, ou monter le volume des certificats"
                        + " (certificat genere par le script d'entrypoint Docker).");
            }
            if (properties.http().enabled() && https.enabled()) {
                Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
                connector.setScheme("http");
                connector.setSecure(false);
                connector.setPort(properties.http().port());
                factory.addAdditionalConnectors(connector);
            }
        };
    }
}
