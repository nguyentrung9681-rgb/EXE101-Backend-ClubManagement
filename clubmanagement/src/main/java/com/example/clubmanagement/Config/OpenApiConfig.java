package com.example.clubmanagement.Config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
//        Server localServer = new Server();
//        localServer.setUrl("http://localhost:" + serverPort);
//        localServer.setDescription("Local Server");
//
//        Server ngrokServer = new Server();
//        ngrokServer.setUrl("https://doorstep-disaster-platonic.ngrok-free.dev");
//        ngrokServer.setDescription("Ngrok Server");
//
//        return new OpenAPI()
//                .servers(List.of(localServer, ngrokServer))
//                .addSecurityItem(
//                        new SecurityRequirement().addList("Bearer Authentication")
//                )
//                .components(
//                        new Components().addSecuritySchemes(
//                                "Bearer Authentication",
//                                createAPIKeyScheme()
//                        )
//                );
        Server currentServer = new Server();
        currentServer.setUrl("/");
        currentServer.setDescription("Current Server");

        return new OpenAPI()
                .servers(List.of(currentServer))
                .addSecurityItem(
                        new SecurityRequirement()
                                .addList("Bearer Authentication")
                )
                .components(
                        new Components().addSecuritySchemes(
                                "Bearer Authentication",
                                createAPIKeyScheme()
                        )
                );
    }

    private SecurityScheme createAPIKeyScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .bearerFormat("JWT")
                .scheme("bearer");
    }
}
