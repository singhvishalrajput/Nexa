package com.nexa.api.config;


import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class TomcatConfiguration {

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> tomcatConnectorCustomizer() {
        return factory -> factory.setProtocol("org.apache.coyote.http11.Http11Nio2Protocol");
    }
}
