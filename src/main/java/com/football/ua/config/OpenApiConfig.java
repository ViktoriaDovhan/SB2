package com.football.ua.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    
    @Bean
    public OpenAPI footballOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        
        return new OpenAPI()
            .info(new Info()
                .title("Football API")
                .description("""
                    REST API для управління футбольними матчами, командами та новинами
                    
                    **Рівні доступу:**
                    - 🌐 PUBLIC: Публічний доступ (без автентифікації)
                    - 🔐 AUTHENTICATED: Автентифіковані користувачі (USER, MODERATOR, EDITOR)
                    - 👮 MODERATOR: Тільки модератори
                    - ✍️ EDITOR: Тільки редактори
                    
                    **Як використовувати:**
                    1. Зареєструйтеся через /api/auth/register
                    2. Увійдіть через /api/auth/login та отримайте JWT токен
                    3. Натисніть кнопку "Authorize" та вставте токен
                    4. Тепер ви можете використовувати захищені endpoint'и
                    """)
                .version("2.0.0")
                .contact(new Contact()
                    .name("Football Team")
                    .email("support@football.ua")))
            .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
            .components(new Components()
                .addSecuritySchemes(securitySchemeName, new SecurityScheme()
                    .name(securitySchemeName)
                    .type(SecurityScheme.Type.HTTP)
                    .scheme("bearer")
                    .bearerFormat("JWT")
                    .description("Введіть JWT токен отриманий після логіну")));
    }
}


