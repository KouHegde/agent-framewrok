package com.agentframework.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI/Swagger configuration for API documentation.
 */
@Configuration
public class OpenApiConfig {

    @Value("${server.port:8080}")
    private String serverPort;

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
                .info(new Info()
                        .title("Agent Framework API")
                        .description("""
                                REST API for the Agent Framework - an AI agent orchestration platform.
                                
                                ## Features
                                - **Agent Management**: Create, list, update, and delete AI agents
                                - **Agent Execution**: Run agents with queries and manage sessions
                                - **Tool Registry**: Manage MCP tools available to agents
                                - **Authentication**: JWT-based authentication with role and scope-based access control
                                
                                ## Authentication
                                Most endpoints require JWT authentication. Use the `/api/auth/login` endpoint to get a token,
                                then include it in the `Authorization` header as `Bearer <token>`.
                                
                                ## Roles & Scopes
                                - **USER**: agents:read, agents:write, agents:execute, sessions:read, sessions:write
                                - **ADMIN**: All permissions including agents:delete, users:*, system:*
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Agent Framework Team")
                                .email("support@agentframework.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:" + serverPort)
                                .description("Local Development Server")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Enter your JWT token")));
    }
}
