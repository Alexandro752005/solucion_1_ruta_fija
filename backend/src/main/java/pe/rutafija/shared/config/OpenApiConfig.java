package pe.rutafija.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    public static final String BEARER_SCHEME = "bearerAuth";
    public static final String REFRESH_COOKIE_SCHEME = "refreshCookie";

    @Bean
    OpenAPI rutaFijaOpenApi(SecurityProperties properties) {
        return new OpenAPI()
                .info(new Info()
                        .title("Ruta Fija API")
                        .description(
                                "API privada del CRM web administrativo y reportes. "
                                        + "Los roles activos son SUPER_ADMIN, ADMIN y CONDUCTOR; "
                                        + "la operación de tenant del CRM requiere ADMIN."
                        )
                        .version("v1"))
                .components(new Components()
                        .addSecuritySchemes(
                                BEARER_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        )
                        .addSecuritySchemes(
                                REFRESH_COOKIE_SCHEME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(properties.refreshCookie().name())
                                        .description("Refresh token opaco en cookie HttpOnly")
                        ))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }
}
