package com.psl.oms.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenApiConfig – configures the Springdoc OpenAPI / Swagger UI metadata.
 *
 * <p>Springdoc auto-discovers all {@code @RestController} classes and generates
 * the spec automatically.  This config only enriches the top-level API info
 * (title, description, version, contact, server URL).
 *
 * <p>Access the UI at: {@code http://localhost:8080/swagger-ui.html}
 * Access the raw JSON spec at: {@code http://localhost:8080/v3/api-docs}
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI omsOpenAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Order Management System API")
                .description("""
                    REST API for the OMS backend.
                    
                    Provides endpoints for managing customers, stock items,
                    purchase orders, and reporting.
                    
                    Modernised from the Core Java + JDBC mini-project.
                    """)
                .version("1.0.0")
                .contact(new Contact()
                    .name("PSL OMS Team")
                    .email("oms-support@psl.com"))
                .license(new License()
                    .name("MIT")
                    .url("https://opensource.org/licenses/MIT"))
            )
            .servers(List.of(
                new Server()
                    .url("http://localhost:" + serverPort)
                    .description("Local development server")
            ));
    }
}
