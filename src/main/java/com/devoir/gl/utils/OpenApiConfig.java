package com.devoir.gl.utils;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;

import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI customOpenAPI() {
      return new OpenAPI()
                .info(new Info()
                		.title("API Banking System")
                		.version("1.0")
                		.description("Documentation de l'API bancaire"));
    
    }
}
