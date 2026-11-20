package br.edu.usjt.dinga;

import br.edu.usjt.dinga.config.LlmProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Ponto de entrada da aplicação Dinga AI — Tutor de Idiomas com Skills, RAG e Governança MTG-IA.
 */
@SpringBootApplication
@EnableConfigurationProperties(LlmProperties.class)
public class DingaAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(DingaAiApplication.class, args);
    }
}
