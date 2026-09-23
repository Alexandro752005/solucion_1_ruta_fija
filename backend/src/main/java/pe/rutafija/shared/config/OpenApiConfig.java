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
                                "API privada del CRM administrativo y de la aplicación móvil de conductor. "
                                        + "Los roles activos son SUPER_ADMIN, ADMIN y CONDUCTOR; la operación "
                                        + "por tenant del CRM requiere ADMIN. Las rutas /api/v1/mobile requieren "
                                        + "un JWT de CONDUCTOR emitido para sessionChannel=MOBILE y resuelven "
                                        + "tenant, usuario y conductor en el servidor. "
                                        + "ADMIN_DIRECT crea una asignación SCHEDULED; MOBILE_CONFIRMATION crea "
                                        + "PENDING_RESPONSE y solo su conductor móvil puede aceptarla o rechazarla. "
                                        + "El CRM nunca simula esa respuesta. Los comandos móviles usan clientEventId, "
                                        + "version y occurredAt; un reintento idéntico se identifica con "
                                        + "X-Idempotent-Replay. La ubicación conserva solo el punto vigente, con "
                                        + "consentimiento y sin historial."
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
