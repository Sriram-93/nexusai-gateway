package com.llm.nexusai_gateway.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import org.springframework.web.reactive.config.ResourceHandlerRegistry;
import org.springframework.web.reactive.config.WebFluxConfigurer;

import static org.springframework.web.reactive.function.server.RouterFunctions.route;
import static org.springframework.web.reactive.function.server.ServerResponse.ok;

@Configuration
public class WebFluxSpaConfig implements WebFluxConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/");
    }

    @Bean
    public RouterFunction<ServerResponse> spaRouter() {
        return route()
            // Exclude API paths and static asset files with extensions
            .route(request -> !request.path().startsWith("/api/") 
                           && !request.path().startsWith("/v1/")
                           && !request.path().startsWith("/actuator/")
                           && !request.path().contains("."), 
                   request -> {
                       Resource indexHtml = new ClassPathResource("static/index.html");
                       return ok().contentType(MediaType.TEXT_HTML)
                                .body(org.springframework.web.reactive.function.BodyInserters.fromResource(indexHtml));
                   })
            .build();
    }
}
