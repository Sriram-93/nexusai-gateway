package com.llm.nexusai_gateway.Config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3.0 Specification & Swagger UI Configuration.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("NexusAI Control Plane Gateway API")
                        .version("v3.0.0")
                        .description("""
                                **NexusAI** is an Enterprise AI Control Plane & Proxy Gateway.
                                
                                Features:
                                - **OpenAI-Compatible Ingress** (`/v1/chat/completions`, `/v1/embeddings`, `/v1/models`)
                                - **Multi-Armed Bandit Adaptive Routing** (LinUCB with latency, cost, and LLM-as-a-Judge quality feedback)
                                - **Multi-Hop Provider Fallback Cascade** (OpenAI, Groq, Gemini, Anthropic, Ollama)
                                - **Zero-Trust Telemetry & Real-Time SSE Stream** (`/api/telemetry/stream`)
                                - **Prompt Caching & Governance Hard-Stop Budgets**
                                """)
                        .contact(new Contact().name("NexusAI Engineering").email("support@nexusai.io"))
                        .license(new License().name("Apache 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Gateway Server"),
                        new Server().url("https://gateway.nexusai.io").description("Cloud Control Plane")
                ))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT / API Key")
                                .description("Enter your gateway API key (`nx_live_...`) or Bearer token")));
    }
}
