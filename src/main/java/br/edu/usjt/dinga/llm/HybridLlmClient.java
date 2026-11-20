package br.edu.usjt.dinga.llm;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.model.ChatTurn;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente LLM híbrido via java.net.http.HttpClient (suporta família Gemini 3.8 Flash com SSE streaming,
 * Groq, OpenAI-compatível e Ollama), com cache leve em memória (LRU + TTL) para evitar chamadas duplicadas.
 * Retorna Optional.empty() sem falhar caso não exista chave configurada ou ocorra erro/limite de cota.
 */
@Component
public class HybridLlmClient {

    private static final Logger log = LoggerFactory.getLogger(HybridLlmClient.class);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration STREAM_TIMEOUT = Duration.ofSeconds(25);
    private static final int MAX_CACHE_ENTRIES = 128;
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final List<String> GEMINI_FLASH_FALLBACK_CHAIN = List.of(
            "gemini-3.8-flash",
            "gemini-3.8-flash-lite",
            "gemini-2.5-flash-lite",
            "gemini-3.1-flash-lite",
            "gemini-3-flash-preview"
    );

    private final LlmProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final Map<String, CachedEntry> responseCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_CACHE_ENTRIES + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedEntry> eldest) {
                    return size() > MAX_CACHE_ENTRIES;
                }
            }
    );
    private final AtomicLong cacheHits = new AtomicLong(0L);
    private final AtomicLong cacheMisses = new AtomicLong(0L);

    @Autowired
    public HybridLlmClient(LlmProperties properties, ObjectMapper objectMapper) {
        this(
                properties,
                objectMapper,
                HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(6))
                        .build()
        );
    }

    public HybridLlmClient(LlmProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<String> candidateGeminiModels() {
        List<String> modelsToTry = new ArrayList<>();
        if (LlmProperties.hasText(properties.geminiModel())) {
            modelsToTry.add(properties.geminiModel().trim());
        }
        for (String fallbackModel : GEMINI_FLASH_FALLBACK_CHAIN) {
            if (!modelsToTry.contains(fallbackModel)) {
                modelsToTry.add(fallbackModel);
            }
        }
        return List.copyOf(modelsToTry);
    }

    public Optional<LlmResult> generate(String systemPrompt, String userMessage) {
        return generate(systemPrompt, userMessage, List.of());
    }

    public Optional<LlmResult> generate(String systemPrompt, String userMessage, List<ChatTurn> history) {
        String resolved = properties.resolvedProviderName();
        if ("local".equals(resolved)) {
            return Optional.empty();
        }

        List<ChatTurn> recentTurns = sliceRecentTurns(history);
        String cacheKey = buildCacheKey(resolved, systemPrompt, userMessage, recentTurns);
        Optional<LlmResult> cached = getCached(cacheKey);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<LlmResult> result = invokeProvider(resolved, systemPrompt, userMessage, recentTurns);
        result.ifPresent(r -> putCached(cacheKey, r));
        return result;
    }

    private Optional<LlmResult> invokeProvider(
            String resolved,
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns
    ) {
        try {
            if (resolved.startsWith("Gemini")) {
                return callGemini(systemPrompt, userMessage, recentTurns, resolved);
            } else if (resolved.startsWith("Groq")) {
                return callOpenAiCompatible(
                        "https://api.groq.com/openai/v1/chat/completions",
                        properties.groqApiKey(),
                        properties.groqModel(),
                        systemPrompt,
                        userMessage,
                        recentTurns,
                        resolved
                );
            } else if (resolved.startsWith("OpenAI")) {
                String base = properties.openaiBaseUrl().replaceAll("/+$", "");
                return callOpenAiCompatible(
                        base + "/chat/completions",
                        properties.openaiApiKey(),
                        properties.openaiModel(),
                        systemPrompt,
                        userMessage,
                        recentTurns,
                        resolved
                );
            } else if (resolved.startsWith("Ollama")) {
                return callOllama(systemPrompt, userMessage, recentTurns, resolved);
            }
        } catch (Exception ex) {
            log.warn("Falha ao chamar provedor LLM ({}); aplicando fallback RAG Local: {}", resolved, ex.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Gera resposta com streaming progressivo (SSE nativo no Gemini 3.8 Flash; envio direto para demais provedores ou cache).
     */
    public Optional<LlmResult> streamGenerate(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> history,
            Consumer<String> onToken
    ) {
        String resolved = properties.resolvedProviderName();
        if ("local".equals(resolved)) {
            return Optional.empty();
        }

        List<ChatTurn> recentTurns = sliceRecentTurns(history);
        String cacheKey = buildCacheKey(resolved, systemPrompt, userMessage, recentTurns);
        Optional<LlmResult> cached = getCached(cacheKey);
        if (cached.isPresent()) {
            if (onToken != null) {
                onToken.accept(cached.get().text());
            }
            return cached;
        }

        if (resolved.startsWith("Gemini")) {
            try {
                Optional<StreamOutcome> streamed = callGeminiStream(systemPrompt, userMessage, recentTurns, resolved, onToken);
                if (streamed.isPresent()) {
                    StreamOutcome outcome = streamed.get();
                    if (outcome.complete()) {
                        putCached(cacheKey, outcome.result());
                    }
                    return Optional.of(outcome.result());
                }
                return Optional.empty();
            } catch (Exception ex) {
                log.warn("Falha no streaming Gemini ({}); aplicando fallback RAG Local: {}", resolved, ex.getMessage());
                return Optional.empty();
            }
        }

        Optional<LlmResult> result = invokeProvider(resolved, systemPrompt, userMessage, recentTurns);
        if (result.isPresent()) {
            putCached(cacheKey, result.get());
            if (onToken != null) {
                onToken.accept(result.get().text());
            }
        }
        return result;
    }

    public void clearCache() {
        responseCache.clear();
        cacheHits.set(0L);
        cacheMisses.set(0L);
    }

    public int cacheSize() {
        return responseCache.size();
    }

    public long cacheHits() {
        return cacheHits.get();
    }

    public long cacheMisses() {
        return cacheMisses.get();
    }

    private Optional<LlmResult> getCached(String cacheKey) {
        CachedEntry entry = responseCache.get(cacheKey);
        if (entry != null) {
            if (System.currentTimeMillis() < entry.expiresAtMs()) {
                cacheHits.incrementAndGet();
                return Optional.of(entry.result());
            }
            responseCache.remove(cacheKey);
        }
        cacheMisses.incrementAndGet();
        return Optional.empty();
    }

    private void putCached(String cacheKey, LlmResult result) {
        if (result == null || result.text() == null || result.text().isBlank()) {
            return;
        }
        responseCache.put(cacheKey, new CachedEntry(result, System.currentTimeMillis() + CACHE_TTL_MS));
    }

    private static List<ChatTurn> sliceRecentTurns(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        return history.subList(Math.max(0, history.size() - 3), history.size());
    }

    private static String buildCacheKey(
            String resolvedProvider,
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns
    ) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(resolvedProvider).append('|')
                .append(systemPrompt == null ? "" : systemPrompt).append('|')
                .append(userMessage == null ? "" : userMessage);
        for (ChatTurn turn : recentTurns) {
            sb.append('|').append(turn.question())
                    .append("->").append(turn.reply())
                    .append("::").append(turn.practicePrompt());
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(sb.toString().hashCode());
        }
    }

    private Map<String, Object> buildGeminiBody(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns
    ) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : recentTurns) {
            if (!turn.question().isBlank()) {
                contents.add(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", turn.question()))
                ));
            }
            if (!turn.reply().isBlank() || !turn.practicePrompt().isBlank()) {
                String modelText = turn.reply();
                if (!turn.practicePrompt().isBlank()) {
                    modelText = modelText + "\n\nMicro-desafio proposto ao aluno: " + turn.practicePrompt();
                }
                contents.add(Map.of(
                        "role", "model",
                        "parts", List.of(Map.of("text", modelText.trim()))
                ));
            }
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userMessage))
        ));

        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", contents,
                "generationConfig", Map.of("temperature", 0.4, "maxOutputTokens", 700)
        );
    }

    private Optional<LlmResult> callGemini(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label
    ) throws Exception {
        List<String> modelsToTry = candidateGeminiModels();
        Map<String, Object> body = buildGeminiBody(systemPrompt, userMessage, recentTurns);
        String key = URLEncoder.encode(properties.geminiApiKey(), StandardCharsets.UTF_8);

        for (String candidateModel : modelsToTry) {
            try {
                String model = URLEncoder.encode(candidateModel, StandardCharsets.UTF_8);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
                String responseBody = postJson(url, body, Map.of());
                if (responseBody == null) {
                    continue;
                }
                JsonNode root = objectMapper.readTree(responseBody);
                String text = extractGeminiPartsText(root);
                if (!text.isBlank()) {
                    String effectiveLabel = candidateModel.equals(properties.geminiModel())
                            ? label
                            : "Gemini (" + candidateModel + ")";
                    return Optional.of(new LlmResult(text.trim(), effectiveLabel));
                }
            } catch (Exception ex) {
                log.warn("Modelo Gemini '{}' falhou ({}); tentando próximo modelo se disponível.", candidateModel, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    private Optional<StreamOutcome> callGeminiStream(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label,
            Consumer<String> onToken
    ) throws Exception {
        List<String> modelsToTry = candidateGeminiModels();
        Map<String, Object> body = buildGeminiBody(systemPrompt, userMessage, recentTurns);
        String json = objectMapper.writeValueAsString(body);
        String key = URLEncoder.encode(properties.geminiApiKey(), StandardCharsets.UTF_8);

        for (String candidateModel : modelsToTry) {
            StringBuilder accumulated = new StringBuilder();
            try {
                String model = URLEncoder.encode(candidateModel, StandardCharsets.UTF_8);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":streamGenerateContent?alt=sse&key=" + key;
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(STREAM_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .header("Accept", "text/event-stream")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());
                try (Stream<String> lines = response.body()) {
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        log.warn("Streaming Gemini '{}' retornou HTTP {}; tentando próximo modelo.", candidateModel, response.statusCode());
                        continue;
                    }

                    if (lines != null) {
                        lines.forEach(line -> {
                            if (line == null || !line.startsWith("data:")) {
                                return;
                            }
                            String payload = line.substring(5).trim();
                            if (payload.isEmpty() || "[DONE]".equals(payload)) {
                                return;
                            }
                            try {
                                JsonNode root = objectMapper.readTree(payload);
                                String delta = extractGeminiPartsText(root);
                                if (!delta.isEmpty()) {
                                    accumulated.append(delta);
                                    if (onToken != null) {
                                        onToken.accept(delta);
                                    }
                                }
                            } catch (Exception parseEx) {
                                log.debug("Ignorando evento SSE não-JSON do Gemini: {}", parseEx.getMessage());
                            }
                        });
                    }
                }

                String finalText = accumulated.toString().trim();
                if (!finalText.isBlank()) {
                    String effectiveLabel = candidateModel.equals(properties.geminiModel())
                            ? label
                            : "Gemini (" + candidateModel + ")";
                    return Optional.of(new StreamOutcome(new LlmResult(finalText, effectiveLabel), true));
                }
            } catch (Exception ex) {
                if (!accumulated.isEmpty()) {
                    String effectiveLabel = candidateModel.equals(properties.geminiModel())
                            ? label
                            : "Gemini (" + candidateModel + ")";
                    // Retorna conteúdo parcial já enviado ao cliente, mas marca como incompleto para não envenenar o cache
                    return Optional.of(new StreamOutcome(new LlmResult(accumulated.toString().trim(), effectiveLabel), false));
                }
                log.warn("Streaming Gemini '{}' falhou ({}); tentando próximo modelo se disponível.", candidateModel, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    private static String extractGeminiPartsText(JsonNode root) {
        JsonNode parts = root.path("candidates").path(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return "";
        }
        if (parts.size() == 1) {
            return parts.path(0).path("text").asString("");
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            String piece = part.path("text").asString("");
            if (!piece.isEmpty()) {
                sb.append(piece);
            }
        }
        return sb.toString();
    }

    private Optional<LlmResult> callOpenAiCompatible(
            String endpoint,
            String apiKey,
            String model,
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label
    ) throws Exception {
        List<Map<String, String>> messages = buildOpenAiMessages(systemPrompt, userMessage, recentTurns);
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.4,
                "max_tokens", 700,
                "messages", messages
        );

        String responseBody = postJson(endpoint, body, Map.of("Authorization", "Bearer " + apiKey));
        if (responseBody == null) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("choices").path(0).path("message").path("content").asString("");
        return text.isBlank() ? Optional.empty() : Optional.of(new LlmResult(text.trim(), label));
    }

    private Optional<LlmResult> callOllama(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label
    ) throws Exception {
        String base = properties.ollamaUrl().replaceAll("/+$", "");
        List<Map<String, String>> messages = buildOpenAiMessages(systemPrompt, userMessage, recentTurns);
        Map<String, Object> body = Map.of(
                "model", properties.ollamaModel(),
                "stream", false,
                "messages", messages
        );

        String responseBody = postJson(base + "/api/chat", body, Map.of());
        if (responseBody == null) {
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(responseBody);
        String text = root.path("message").path("content").asString("");
        return text.isBlank() ? Optional.empty() : Optional.of(new LlmResult(text.trim(), label));
    }

    private static List<Map<String, String>> buildOpenAiMessages(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns
    ) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt));
        for (ChatTurn turn : recentTurns) {
            if (!turn.question().isBlank()) {
                messages.add(Map.of("role", "user", "content", turn.question()));
            }
            if (!turn.reply().isBlank() || !turn.practicePrompt().isBlank()) {
                String assistantContent = turn.reply();
                if (!turn.practicePrompt().isBlank()) {
                    assistantContent = assistantContent + "\nMicro-desafio: " + turn.practicePrompt();
                }
                messages.add(Map.of("role", "assistant", "content", assistantContent.trim()));
            }
        }
        messages.add(Map.of("role", "user", "content", userMessage));
        return messages;
    }

    private String postJson(String url, Object payload, Map<String, String> extraHeaders) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        extraHeaders.forEach(builder::header);

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Provedor LLM retornou status HTTP {}; usando fallback RAG Local.", response.statusCode());
            return null;
        }
        return response.body();
    }

    private record StreamOutcome(LlmResult result, boolean complete) {
    }

    private record CachedEntry(LlmResult result, long expiresAtMs) {
    }

    public record LlmResult(String text, String providerLabel) {
    }
}
