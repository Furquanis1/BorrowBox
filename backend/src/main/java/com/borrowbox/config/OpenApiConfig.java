package com.borrowbox.config;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI borrowBoxOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("BorrowBox API")
                        .version("1.0.0")
                        .description("API documentation for the BorrowBox backend"));
    }
}
