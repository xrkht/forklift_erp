package com.example.forklift_erp.config;

import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnClass(TomcatServletWebServerFactory.class)
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpsRedirectConfig {

    @Value("${server.http.port:8080}")
    private int httpPort;

    @Value("${server.port:8443}")
    private int httpsPort;

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsRedirectCustomizer() {
        return factory -> {
            if (httpPort <= 0 || httpPort == httpsPort) {
                return;
            }

            factory.addAdditionalTomcatConnectors(httpConnector());
            factory.addContextCustomizers(this::requireHttps);
        };
    }

    private Connector httpConnector() {
        Connector connector = new Connector(TomcatServletWebServerFactory.DEFAULT_PROTOCOL);
        connector.setScheme("http");
        connector.setPort(httpPort);
        connector.setSecure(false);
        connector.setRedirectPort(httpsPort);
        return connector;
    }

    private void requireHttps(Context context) {
        SecurityConstraint constraint = new SecurityConstraint();
        constraint.setUserConstraint("CONFIDENTIAL");

        SecurityCollection collection = new SecurityCollection();
        collection.addPattern("/*");
        constraint.addCollection(collection);

        context.addConstraint(constraint);
    }
}
