package com.llm.nexusai_gateway.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Configuration
public class WebFluxSpaConfig {

    @Bean
    public RouterFunction<ServerResponse> spaRouter() {
        return route()
            // Exclude API paths
            .route(request -> !request.path().startsWith("/api/") 
                           && !request.path().startsWith("/v1/")
                           && !request.path().contains("."), // simplistic check for files like .js, .css, .html
                   request -> {
                       Resource indexHtml = new ClassPathResource("static/index.html");
                       return ok().contentType(MediaType.TEXT_HTML).bodyValue(indexHtml);
                   })
            // Let Spring WebFlux handle serving actual static files from classpath:/static/ natively
            .build();
    }
}
