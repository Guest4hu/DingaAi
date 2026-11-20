package br.edu.usjt.dinga.storage;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Persistência local leve em JSON na pasta configurada (padrão: data/) para que
 * documentos docentes do RAG, questões geradas do Quiz e relatos de auditoria MTG-IA
 * sobrevivam a reinicializações do servidor Java.
 */
@Component
public class JsonDataStore {

    private static final Logger log = LoggerFactory.getLogger(JsonDataStore.class);

    private final Path storageDir;
    private final ObjectMapper objectMapper;

    public JsonDataStore(
            @Value("${dinga.storage.dir:data}") String storageDir,
            ObjectMapper objectMapper
    ) {
        String cleanDir = (storageDir == null || storageDir.isBlank()) ? "data" : storageDir.trim();
        this.storageDir = Path.of(cleanDir);
        this.objectMapper = objectMapper;
    }

    public Path getStorageDir() {
        return storageDir;
    }

    public synchronized <T> List<T> readList(String filename, Class<T> elementType) {
        Path file = storageDir.resolve(filename);
        if (!Files.exists(file)) {
            return new ArrayList<>();
        }
        try {
            String raw = Files.readString(file, StandardCharsets.UTF_8);
            if (raw.isBlank()) {
                return new ArrayList<>();
            }
            JavaType listType = objectMapper.getTypeFactory().constructCollectionType(List.class, elementType);
            List<T> parsed = objectMapper.readValue(raw, listType);
            return parsed == null ? new ArrayList<>() : new ArrayList<>(parsed);
        } catch (Exception ex) {
            log.warn("Falha ao ler arquivo de persistência JSON '{}'; iniciando lista vazia: {}", file, ex.getMessage());
            return new ArrayList<>();
        }
    }

    public synchronized <T> void writeList(String filename, List<T> items) {
        Path file = storageDir.resolve(filename);
        try {
            Files.createDirectories(storageDir);
            String json = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(items == null ? List.of() : items);
            Path tmp = storageDir.resolve(filename + ".tmp");
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException ex) {
            // Fallback caso o filesystem não suporte ATOMIC_MOVE
            try {
                String json = objectMapper.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(items == null ? List.of() : items);
                Files.writeString(file, json, StandardCharsets.UTF_8);
            } catch (Exception fallbackEx) {
                log.warn("Falha ao gravar arquivo de persistência JSON '{}': {}", file, fallbackEx.getMessage());
            }
        } catch (Exception ex) {
            log.warn("Falha ao serializar arquivo de persistência JSON '{}': {}", file, ex.getMessage());
        }
    }
}
