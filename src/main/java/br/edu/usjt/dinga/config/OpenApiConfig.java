package br.edu.usjt.dinga.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração da documentação interativa OpenAPI 3 / Swagger UI (/swagger-ui.html).
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI dingaOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Dinga AI — API REST")
                        .version("1.1.0")
                        .description("API do tutor inteligente de inglês Dinga AI (USJT — Caso 3 Microsoft Learn): "
                                + "Chat Socrático com Skills e RAG BM25, Simulador Daily/Entrevista, Quiz assistido por Gemini 3.8 Flash, "
                                + "Curadoria Docente ao vivo (.md/.txt), TTS híbrido e Governança MTG-IA / LGPD.")
                        .contact(new Contact().name("Equipe Dinga AI — USJT")));
    }
}
