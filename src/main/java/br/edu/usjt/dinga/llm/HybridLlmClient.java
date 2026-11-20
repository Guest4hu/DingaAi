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
import java.util.concurrent.ConcurrentHashMap;
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
    private static final long COOLDOWN_NOT_FOUND_MS = 10 * 60 * 1000L;
    private static final long COOLDOWN_TRANSIENT_MS = 30 * 1000L;
    private static final int MAX_HISTORY_REPLY_CHARS = 450;
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
    private final ConcurrentHashMap<String, Long> modelCooldownUntilMs = new ConcurrentHashMap<>();
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
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(4))
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

    public boolean supportsGenerationOptions() {
        return true;
    }

    public Optional<LlmResult> generate(String systemPrompt, String userMessage) {
        return generate(systemPrompt, userMessage, List.of(), GenerationOptions.defaults());
    }

    public Optional<LlmResult> generate(String systemPrompt, String userMessage, GenerationOptions options) {
        return generate(systemPrompt, userMessage, List.of(), options);
    }

    public Optional<LlmResult> generate(String systemPrompt, String userMessage, List<ChatTurn> history) {
        return generate(systemPrompt, userMessage, history, GenerationOptions.defaults());
    }

    public Optional<LlmResult> generate(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> history,
            GenerationOptions options
    ) {
        String resolved = properties.resolvedProviderName();
        if ("local".equals(resolved)) {
            return Optional.empty();
        }

        GenerationOptions effectiveOptions = options == null ? GenerationOptions.defaults() : options;
        List<ChatTurn> recentTurns = sliceRecentTurns(history);
        String cacheKey = buildCacheKey(resolved, systemPrompt, userMessage, recentTurns, effectiveOptions);
        Optional<LlmResult> cached = getCached(cacheKey);
        if (cached.isPresent()) {
            return cached;
        }

        Optional<LlmResult> result = invokeProvider(resolved, systemPrompt, userMessage, recentTurns, effectiveOptions);
        result.ifPresent(r -> putCached(cacheKey, r));
        return result;
    }

    private Optional<LlmResult> invokeProvider(
            String resolved,
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            GenerationOptions options
    ) {
        try {
            if (resolved.startsWith("Gemini")) {
                return callGemini(systemPrompt, userMessage, recentTurns, resolved, options);
            } else if (resolved.startsWith("Groq")) {
                return callOpenAiCompatible(
                        "https://api.groq.com/openai/v1/chat/completions",
                        properties.groqApiKey(),
                        properties.groqModel(),
                        systemPrompt,
                        userMessage,
                        recentTurns,
                        resolved,
                        options
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
                        resolved,
                        options
                );
            } else if (resolved.startsWith("Ollama")) {
                return callOllama(systemPrompt, userMessage, recentTurns, resolved, options);
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
        return streamGenerate(systemPrompt, userMessage, history, GenerationOptions.defaults(), onToken);
    }

    public Optional<LlmResult> streamGenerate(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> history,
            GenerationOptions options,
            Consumer<String> onToken
    ) {
        String resolved = properties.resolvedProviderName();
        if ("local".equals(resolved)) {
            return Optional.empty();
        }

        GenerationOptions effectiveOptions = options == null ? GenerationOptions.defaults() : options;
        List<ChatTurn> recentTurns = sliceRecentTurns(history);
        String cacheKey = buildCacheKey(resolved, systemPrompt, userMessage, recentTurns, effectiveOptions);
        Optional<LlmResult> cached = getCached(cacheKey);
        if (cached.isPresent()) {
            if (onToken != null) {
                onToken.accept(cached.get().text());
            }
            return cached;
        }

        if (resolved.startsWith("Gemini")) {
            try {
                Optional<StreamOutcome> streamed = callGeminiStream(
                        systemPrompt, userMessage, recentTurns, resolved, effectiveOptions, onToken
                );
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

        Optional<LlmResult> result = invokeProvider(resolved, systemPrompt, userMessage, recentTurns, effectiveOptions);
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
            List<ChatTurn> recentTurns,
            GenerationOptions options
    ) {
        StringBuilder sb = new StringBuilder(256);
        sb.append(resolvedProvider).append('|')
                .append(options.temperature()).append('|')
                .append(options.topP()).append('|')
                .append(options.maxOutputTokens()).append('|')
                .append(options.responseMimeType() == null ? "" : options.responseMimeType()).append('|')
                .append(options.hasStructuredSchema() ? options.responseSchema().toString() : "").append('|')
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

    public boolean isModelOnCooldown(String model) {
        Long until = modelCooldownUntilMs.get(model);
        if (until == null) {
            return false;
        }
        if (System.currentTimeMillis() >= until) {
            modelCooldownUntilMs.remove(model);
            return false;
        }
        return true;
    }

    private void markModelCooldown(String model, int statusCode) {
        if (statusCode == 404) {
            modelCooldownUntilMs.put(model, System.currentTimeMillis() + COOLDOWN_NOT_FOUND_MS);
        } else if (statusCode == 429 || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504) {
            modelCooldownUntilMs.put(model, System.currentTimeMillis() + COOLDOWN_TRANSIENT_MS);
        }
    }

    private List<String> activeGeminiModels() {
        List<String> all = candidateGeminiModels();
        List<String> healthy = new ArrayList<>(all.size());
        for (String model : all) {
            if (!isModelOnCooldown(model)) {
                healthy.add(model);
            }
        }
        // Se todos estiverem em cooldown, tenta a lista completa como rede de segurança
        return healthy.isEmpty() ? all : healthy;
    }

    private static String compactText(String text, int maxChars) {
        if (text == null || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxChars).trim() + "…";
    }

    private Map<String, Object> buildGeminiBody(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            GenerationOptions options
    ) {
        List<Map<String, Object>> contents = new ArrayList<>();
        for (ChatTurn turn : recentTurns) {
            if (!turn.question().isBlank()) {
                contents.add(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", compactText(turn.question(), 300)))
                ));
            }
            if (!turn.reply().isBlank() || !turn.practicePrompt().isBlank()) {
                String modelText = compactText(turn.reply(), MAX_HISTORY_REPLY_CHARS);
                if (!turn.practicePrompt().isBlank()) {
                    modelText = modelText + "\n\nMicro-desafio proposto ao aluno: " + compactText(turn.practicePrompt(), 220);
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

        Map<String, Object> generationConfig = new LinkedHashMap<>();
        generationConfig.put("temperature", options.temperature());
        generationConfig.put("topP", options.topP());
        generationConfig.put("maxOutputTokens", options.maxOutputTokens());
        if (options.isJsonMode()) {
            generationConfig.put("responseMimeType", options.responseMimeType());
            if (options.hasStructuredSchema()) {
                generationConfig.put("responseSchema", options.responseSchema());
            }
        }

        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", systemPrompt))),
                "contents", contents,
                "generationConfig", generationConfig
        );
    }

    private Optional<LlmResult> callGemini(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label,
            GenerationOptions options
    ) throws Exception {
        List<String> modelsToTry = activeGeminiModels();
        String json = objectMapper.writeValueAsString(buildGeminiBody(systemPrompt, userMessage, recentTurns, options));
        String fallbackPlainJson = null;
        String key = URLEncoder.encode(properties.geminiApiKey(), StandardCharsets.UTF_8);

        for (String candidateModel : modelsToTry) {
            try {
                String model = URLEncoder.encode(candidateModel, StandardCharsets.UTF_8);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + key;
                HttpResponse<String> response = sendGeminiGenerateRequest(url, json);
                int status = response.statusCode();

                // Fallback seguro: se o modelo retornar HTTP 400 com responseSchema/responseMimeType, tenta uma vez sem schema
                if (status == 400 && (options.hasStructuredSchema() || options.isJsonMode())) {
                    log.warn("Modelo Gemini '{}' rejeitou Structured Output (HTTP 400); tentando sem responseSchema.", candidateModel);
                    if (fallbackPlainJson == null) {
                        fallbackPlainJson = objectMapper.writeValueAsString(
                                buildGeminiBody(systemPrompt, userMessage, recentTurns, options.withoutStructuredSchema())
                        );
                    }
                    response = sendGeminiGenerateRequest(url, fallbackPlainJson);
                    status = response.statusCode();
                }

                if (status < 200 || status >= 300) {
                    markModelCooldown(candidateModel, status);
                    log.warn("Modelo Gemini '{}' retornou HTTP {}; tentando próximo modelo.", candidateModel, status);
                    continue;
                }
                JsonNode root = objectMapper.readTree(response.body());
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

    private HttpResponse<String> sendGeminiGenerateRequest(String url, String json) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private Optional<StreamOutcome> callGeminiStream(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label,
            GenerationOptions options,
            Consumer<String> onToken
    ) throws Exception {
        List<String> modelsToTry = activeGeminiModels();
        Map<String, Object> body = buildGeminiBody(systemPrompt, userMessage, recentTurns, options);
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
                    int status = response.statusCode();
                    if (status < 200 || status >= 300) {
                        markModelCooldown(candidateModel, status);
                        log.warn("Streaming Gemini '{}' retornou HTTP {}; tentando próximo modelo.", candidateModel, status);
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
            String label,
            GenerationOptions options
    ) throws Exception {
        List<Map<String, String>> messages = buildOpenAiMessages(systemPrompt, userMessage, recentTurns);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", options.temperature());
        body.put("top_p", options.topP());
        body.put("max_tokens", options.maxOutputTokens());
        body.put("messages", messages);
        if (options.isJsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        Map<String, String> headers = Map.of("Authorization", "Bearer " + apiKey);
        HttpResponse<String> response = sendJsonRequest(endpoint, body, headers);
        if (response.statusCode() == 400 && options.isJsonMode()) {
            log.warn("Provedor '{}' rejeitou response_format json_object (HTTP 400); tentando sem response_format.", label);
            body.remove("response_format");
            response = sendJsonRequest(endpoint, body, headers);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Provedor LLM retornou status HTTP {}; usando fallback RAG Local.", response.statusCode());
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body());
        String text = root.path("choices").path(0).path("message").path("content").asString("");
        return text.isBlank() ? Optional.empty() : Optional.of(new LlmResult(text.trim(), label));
    }

    private Optional<LlmResult> callOllama(
            String systemPrompt,
            String userMessage,
            List<ChatTurn> recentTurns,
            String label,
            GenerationOptions options
    ) throws Exception {
        String base = properties.ollamaUrl().replaceAll("/+$", "");
        List<Map<String, String>> messages = buildOpenAiMessages(systemPrompt, userMessage, recentTurns);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.ollamaModel());
        body.put("stream", false);
        body.put("messages", messages);
        body.put("options", Map.of(
                "temperature", options.temperature(),
                "top_p", options.topP()
        ));
        if (options.isJsonMode()) {
            body.put("format", "json");
        }

        HttpResponse<String> response = sendJsonRequest(base + "/api/chat", body, Map.of());
        if (response.statusCode() == 400 && options.isJsonMode()) {
            log.warn("Ollama rejeitou format=json (HTTP 400); tentando sem format.", label);
            body.remove("format");
            response = sendJsonRequest(base + "/api/chat", body, Map.of());
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            log.warn("Provedor LLM retornou status HTTP {}; usando fallback RAG Local.", response.statusCode());
            return Optional.empty();
        }
        JsonNode root = objectMapper.readTree(response.body());
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

    private HttpResponse<String> sendJsonRequest(String url, Object payload, Map<String, String> extraHeaders) throws Exception {
        String json = objectMapper.writeValueAsString(payload);
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        extraHeaders.forEach(builder::header);

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private record StreamOutcome(LlmResult result, boolean complete) {
    }

    private record CachedEntry(LlmResult result, long expiresAtMs) {
    }

    public record GenerationOptions(
            double temperature,
            double topP,
            int maxOutputTokens,
            String responseMimeType,
            Map<String, Object> responseSchema
    ) {
        public GenerationOptions {
            temperature = Math.max(0.0, Math.min(1.5, temperature));
            topP = Math.max(0.1, Math.min(1.0, topP));
            maxOutputTokens = Math.max(64, Math.min(2048, maxOutputTokens));
            responseMimeType = (responseMimeType == null || responseMimeType.isBlank()) ? null : responseMimeType.trim();
            responseSchema = (responseSchema == null || responseSchema.isEmpty()) ? null : responseSchema;
        }

        public static GenerationOptions defaults() {
            return new GenerationOptions(0.35, 0.90, 650, null, null);
        }

        public static GenerationOptions of(double temperature, double topP) {
            return new GenerationOptions(temperature, topP, 650, null, null);
        }

        public static GenerationOptions of(double temperature, double topP, int maxOutputTokens) {
            return new GenerationOptions(temperature, topP, maxOutputTokens, null, null);
        }

        public static GenerationOptions structuredJson(
                double temperature,
                double topP,
                int maxOutputTokens,
                Map<String, Object> responseSchema
        ) {
            return new GenerationOptions(temperature, topP, maxOutputTokens, "application/json", responseSchema);
        }

        public boolean isJsonMode() {
            return "application/json".equalsIgnoreCase(responseMimeType);
        }

        public boolean hasStructuredSchema() {
            return responseSchema != null && !responseSchema.isEmpty();
        }

        public GenerationOptions withoutStructuredSchema() {
            return new GenerationOptions(temperature, topP, maxOutputTokens, null, null);
        }
    }

    public record LlmResult(String text, String providerLabel) {
    }
}
