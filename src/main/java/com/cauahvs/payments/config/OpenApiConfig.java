package com.cauahvs.payments.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Payment Platform API",
                version = "v1",
                description = "API for managing payments with hexagonal architecture.",
                contact = @Contact(name = "Cauã Salgado", url = "https://github.com/CauaHvS"),
                license = @License(name = "Cauã Salgado")
        )
)
public class OpenApiConfig {
}