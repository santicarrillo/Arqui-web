package ventas.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Documenta en OpenAPI el requisito de seguridad de la Entrega 2: todos los endpoints
 * (salvo POST /usuarios/login y POST /usuarios, que son publicos) requieren un JWT.
 * El acceso es a traves del API Gateway (http://localhost:8080), que valida el token.
 */
@Configuration
public class OpenApiConfig {

    private static final String ESQUEMA = "bearer-jwt";

    @Bean
    public OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("TicketYa - ms-ventas")
                        .version("Entrega 2")
                        .description("Acceso a traves del API Gateway (http://localhost:8080). "
                                + "Autenticacion JWT: obtene el token en POST /usuarios/login y envialo como "
                                + "'Authorization: Bearer <token>'. El gateway valida el token e inyecta la "
                                + "identidad (X-Usuario-Id / X-Rol). Rutas publicas: login y alta de usuario."))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA))
                .components(new Components().addSecuritySchemes(ESQUEMA,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Token JWT emitido por POST /usuarios/login")));
    }
}
