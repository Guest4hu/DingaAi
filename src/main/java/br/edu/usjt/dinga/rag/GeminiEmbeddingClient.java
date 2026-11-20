package br.edu.usjt.dinga.rag;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.storage.JsonDataStore;
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
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Cliente leve de Embeddings da API Gemini com cache persistente local em JSON (data/embeddings-cache.json),
 * pré-aquecimento assíncrono em background (virtual thread) e fallback transparente offline.
 */
@Component
public class GeminiEmbeddingClient {

    public static final String CACHE_FILE = "embeddings-cache.json";
    public static final String DEFAULT_EMBEDDING_MODEL = "gemini-embedding-001";

    private static final Logger log = LoggerFactory.getLogger(GeminiEmbeddingClient.class);
    private static final Duration QUERY_TIMEOUT = Duration.ofMillis(1400);
    private static final Duration BATCH_TIMEOUT = Duration.ofSeconds(8);
    private static final int BATCH_SIZE = 24;
    private static final int MAX_QUERY_CACHE = 128;
    private static final long MODEL_COOLDOWN_MS = 5 * 60 * 1000L;
    private static final List<String> FALLBACK_EMBEDDING_MODELS = List.of(
            "gemini-embedding-001",
            "text-embedding-004"
    );

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final JsonDataStore dataStore;
    private final HttpClient httpClient;
    private final String embeddingModel;

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, float[]>> cacheByModel = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> modelCooldownUntilMs = new ConcurrentHashMap<>();
    private final Map<String, QueryEmbedding> queryCache = Collections.synchronizedMap(
            new LinkedHashMap<>(MAX_QUERY_CACHE + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, QueryEmbedding> eldest) {
                    return size() > MAX_QUERY_CACHE;
                }
            }
    );
    private final AtomicBoolean prewarmInFlight = new AtomicBoolean(false);

    @Autowired
    public GeminiEmbeddingClient(
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            JsonDataStore dataStore,
            @Value("${dinga.rag.embedding-model:" + DEFAULT_EMBEDDING_MODEL + "}") String embeddingModel
    ) {
        this(
                llmProperties,
                objectMapper,
                dataStore,
                embeddingModel,
                HttpClient.newBuilder()
                        .version(HttpClient.Version.HTTP_2)
                        .connectTimeout(Duration.ofSeconds(3))
                        .build()
        );
    }

    public GeminiEmbeddingClient(
            LlmProperties llmProperties,
            ObjectMapper objectMapper,
            JsonDataStore dataStore,
            String embeddingModel,
            HttpClient httpClient
    ) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.dataStore = dataStore;
        this.embeddingModel = LlmProperties.hasText(embeddingModel) ? embeddingModel.trim() : DEFAULT_EMBEDDING_MODEL;
        this.httpClient = httpClient;
        loadCacheFromDisk();
    }

    /**
     * Embeddings remotos só ficam ativos quando há GEMINI_API_KEY e o provedor não está forçado para "local".
     */
    public boolean isEnabled() {
        if (llmProperties == null || !LlmProperties.hasText(llmProperties.geminiApiKey())) {
            return false;
        }
        String provider = llmProperties.provider().toLowerCase(Locale.ROOT);
        return "auto".equals(provider) || "gemini".equals(provider);
    }

    public String embeddingModel() {
        return embeddingModel;
    }

    /**
     * Retorna o modelo efetivo que possui embeddings em cache (priorizando o modelo configurado
     * e modelos fora de cooldown), garantindo que consulta e documento usem o mesmo espaço vetorial.
     */
    public String activeCachedModel() {
        for (String candidate : candidateModels()) {
            if (!isOnCooldown(candidate) && cachedChunkCountForModel(candidate) > 0) {
                return candidate;
            }
        }
        for (String candidate : candidateModels()) {
            if (cachedChunkCountForModel(candidate) > 0) {
                return candidate;
            }
        }
        return embeddingModel;
    }

    public int cachedChunkCount() {
        return cachedChunkCountForModel(activeCachedModel());
    }

    public int cachedChunkCountForModel(String model) {
        if (model == null || model.isBlank()) {
            return 0;
        }
        ConcurrentHashMap<String, float[]> map = cacheByModel.get(model);
        return map == null ? 0 : map.size();
    }

    public Optional<float[]> getCachedChunkEmbedding(KnowledgeChunk chunk) {
        return getCachedChunkEmbedding(chunk, activeCachedModel());
    }

    public Optional<float[]> getCachedChunkEmbedding(KnowledgeChunk chunk, String model) {
        if (chunk == null || model == null || model.isBlank()) {
            return Optional.empty();
        }
        ConcurrentHashMap<String, float[]> map = cacheByModel.get(model);
        if (map == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(map.get(chunkKey(model, chunk)));
    }

    /**
     * Dispara indexação assíncrona de chunks ainda ausentes no cache local sem bloquear startup ou chat.
     */
    public void prewarmChunksAsync(List<KnowledgeChunk> chunks) {
        if (!isEnabled() || chunks == null || chunks.isEmpty()) {
            return;
        }
        String targetModel = selectIndexingModel();
        List<KnowledgeChunk> missing = findMissingChunks(chunks, targetModel);
        if (missing.isEmpty()) {
            return;
        }
        if (!prewarmInFlight.compareAndSet(false, true)) {
            return;
        }
        Thread.startVirtualThread(() -> {
            try {
                indexMissingChunks(chunks);
            } catch (Exception ex) {
                log.debug("Pré-aquecimento assíncrono de embeddings interrompido; mantendo BM25 local: {}", ex.getMessage());
            } finally {
                prewarmInFlight.set(false);
            }
        });
    }

    /**
     * Indexa chunks ausentes de forma síncrona (útil em testes automatizados e benchmark).
     */
    public int indexChunksSync(List<KnowledgeChunk> chunks) {
        if (!isEnabled() || chunks == null || chunks.isEmpty()) {
            return 0;
        }
        return indexMissingChunks(chunks);
    }

    /**
     * Gera (ou retorna do cache LRU) o embedding vetorial de uma consulta com timeout curto para baixa latência.
     */
    public Optional<float[]> embedQuery(String query) {
        return embedQueryWithModel(query).map(QueryEmbedding::vector);
    }

    /**
     * Gera o embedding da consulta usando prioritariamente o mesmo modelo que possui chunks em cache,
     * garantindo compatibilidade de dimensão e espaço vetorial na similaridade de cosseno.
     */
    public Optional<QueryEmbedding> embedQueryWithModel(String query) {
        if (!isEnabled() || query == null || query.isBlank()) {
            return Optional.empty();
        }
        String normalizedQuery = query.trim();
        List<String> modelsToTry = queryModelsWithCachedChunks();
        if (modelsToTry.isEmpty()) {
            modelsToTry = candidateModels();
        }

        for (String model : modelsToTry) {
            String cacheKey = sha256(model + "|Q|" + normalizedQuery);
            QueryEmbedding cached = queryCache.get(cacheKey);
            if (cached != null) {
                return Optional.of(cached);
            }
        }

        String apiKey = URLEncoder.encode(llmProperties.geminiApiKey(), StandardCharsets.UTF_8);
        for (String model : modelsToTry) {
            if (isOnCooldown(model)) {
                continue;
            }
            try {
                String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + encodedModel + ":embedContent?key=" + apiKey;
                Map<String, Object> body = Map.of(
                        "model", "models/" + model,
                        "content", Map.of("parts", List.of(Map.of("text", normalizedQuery))),
                        "taskType", "RETRIEVAL_QUERY"
                );
                String json = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(QUERY_TIMEOUT)
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = response.statusCode();
                if (status >= 200 && status < 300) {
                    JsonNode root = objectMapper.readTree(response.body());
                    float[] vector = parseVector(root.path("embedding").path("values"));
                    if (vector != null && vector.length > 0) {
                        QueryEmbedding result = new QueryEmbedding(model, vector);
                        queryCache.put(sha256(model + "|Q|" + normalizedQuery), result);
                        return Optional.of(result);
                    }
                } else if (status == 404 || status == 429 || status == 503) {
                    markCooldown(model);
                }
            } catch (Exception ex) {
                log.debug("Embedding de consulta falhou no modelo '{}'; usando fallback BM25 local: {}", model, ex.getMessage());
            }
        }
        return Optional.empty();
    }

    private List<String> queryModelsWithCachedChunks() {
        List<String> result = new ArrayList<>();
        for (String candidate : candidateModels()) {
            if (cachedChunkCountForModel(candidate) > 0) {
                result.add(candidate);
            }
        }
        return result;
    }

    private String selectIndexingModel() {
        for (String candidate : candidateModels()) {
            if (!isOnCooldown(candidate)) {
                return candidate;
            }
        }
        return embeddingModel;
    }

    private List<KnowledgeChunk> findMissingChunks(List<KnowledgeChunk> chunks, String model) {
        ConcurrentHashMap<String, float[]> modelMap = cacheByModel.get(model);
        List<KnowledgeChunk> missing = new ArrayList<>();
        for (KnowledgeChunk chunk : chunks) {
            if (chunk != null && (modelMap == null || !modelMap.containsKey(chunkKey(model, chunk)))) {
                missing.add(chunk);
            }
        }
        return missing;
    }

    private int indexMissingChunks(List<KnowledgeChunk> allChunks) {
        String apiKey = URLEncoder.encode(llmProperties.geminiApiKey(), StandardCharsets.UTF_8);

        for (String model : candidateModels()) {
            if (isOnCooldown(model)) {
                continue;
            }
            List<KnowledgeChunk> missing = findMissingChunks(allChunks, model);
            if (missing.isEmpty()) {
                return 0;
            }

            int added = 0;
            boolean modelFailed = false;
            ConcurrentHashMap<String, float[]> modelCache = cacheByModel.computeIfAbsent(model, k -> new ConcurrentHashMap<>());

            for (int start = 0; start < missing.size(); start += BATCH_SIZE) {
                List<KnowledgeChunk> batch = missing.subList(start, Math.min(missing.size(), start + BATCH_SIZE));
                try {
                    String encodedModel = URLEncoder.encode(model, StandardCharsets.UTF_8);
                    String url = "https://generativelanguage.googleapis.com/v1beta/models/" + encodedModel + ":batchEmbedContents?key=" + apiKey;

                    List<Map<String, Object>> requests = new ArrayList<>(batch.size());
                    for (KnowledgeChunk chunk : batch) {
                        requests.add(Map.of(
                                "model", "models/" + model,
                                "content", Map.of("parts", List.of(Map.of("text", buildChunkText(chunk)))),
                                "taskType", "RETRIEVAL_DOCUMENT"
                        ));
                    }

                    String json = objectMapper.writeValueAsString(Map.of("requests", requests));
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url))
                            .timeout(BATCH_TIMEOUT)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    int status = response.statusCode();
                    if (status >= 200 && status < 300) {
                        JsonNode embeddingsNode = objectMapper.readTree(response.body()).path("embeddings");
                        if (embeddingsNode.isArray() && embeddingsNode.size() == batch.size()) {
                            for (int i = 0; i < batch.size(); i++) {
                                float[] vec = parseVector(embeddingsNode.path(i).path("values"));
                                if (vec != null && vec.length > 0) {
                                    modelCache.put(chunkKey(model, batch.get(i)), vec);
                                    added++;
                                }
                            }
                        } else {
                            modelFailed = true;
                            break;
                        }
                    } else {
                        if (status == 404 || status == 429 || status == 503) {
                            markCooldown(model);
                        }
                        modelFailed = true;
                        break;
                    }
                } catch (Exception ex) {
                    log.debug("Batch embedding falhou no modelo '{}': {}", model, ex.getMessage());
                    modelFailed = true;
                    break;
                }
            }

            if (added > 0) {
                persistCacheToDisk();
                return added;
            }
            if (!modelFailed) {
                return 0;
            }
        }
        return 0;
    }

    private List<String> candidateModels() {
        List<String> models = new ArrayList<>();
        models.add(embeddingModel);
        for (String fallback : FALLBACK_EMBEDDING_MODELS) {
            if (!models.contains(fallback)) {
                models.add(fallback);
            }
        }
        return models;
    }

    private boolean isOnCooldown(String model) {
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

    private void markCooldown(String model) {
        modelCooldownUntilMs.put(model, System.currentTimeMillis() + MODEL_COOLDOWN_MS);
    }

    private void loadCacheFromDisk() {
        if (dataStore == null) {
            return;
        }
        List<CachedEmbedding> saved = dataStore.readList(CACHE_FILE, CachedEmbedding.class);
        for (CachedEmbedding entry : saved) {
            if (entry == null || entry.hash() == null || entry.hash().isBlank() || entry.values() == null || entry.values().isEmpty()) {
                continue;
            }
            String entryModel = (entry.model() == null || entry.model().isBlank()) ? embeddingModel : entry.model().trim();
            float[] vec = new float[entry.values().size()];
            for (int i = 0; i < vec.length; i++) {
                vec[i] = entry.values().get(i).floatValue();
            }
            cacheByModel.computeIfAbsent(entryModel, k -> new ConcurrentHashMap<>()).put(entry.hash(), vec);
        }
    }

    private synchronized void persistCacheToDisk() {
        if (dataStore == null) {
            return;
        }
        List<CachedEmbedding> entries = new ArrayList<>();
        for (Map.Entry<String, ConcurrentHashMap<String, float[]>> modelEntry : cacheByModel.entrySet()) {
            String modelName = modelEntry.getKey();
            for (Map.Entry<String, float[]> e : modelEntry.getValue().entrySet()) {
                float[] vec = e.getValue();
                List<Float> list = new ArrayList<>(vec.length);
                for (float v : vec) {
                    list.add(v);
                }
                entries.add(new CachedEmbedding(e.getKey(), modelName, list));
            }
        }
        dataStore.writeList(CACHE_FILE, entries);
    }

    private static float[] parseVector(JsonNode valuesNode) {
        if (valuesNode == null || !valuesNode.isArray() || valuesNode.isEmpty()) {
            return null;
        }
        float[] vec = new float[valuesNode.size()];
        for (int i = 0; i < vec.length; i++) {
            vec[i] = (float) valuesNode.path(i).asDouble(0.0);
        }
        return vec;
    }

    public String chunkKey(KnowledgeChunk chunk) {
        return chunkKey(activeCachedModel(), chunk);
    }

    public String chunkKey(String model, KnowledgeChunk chunk) {
        String effectiveModel = (model == null || model.isBlank()) ? embeddingModel : model.trim();
        return sha256(effectiveModel + "|D|" + buildChunkText(chunk));
    }

    private static String buildChunkText(KnowledgeChunk chunk) {
        String base = chunk.title() + " — " + chunk.section() + " (" + chunk.level() + ", " + chunk.category() + ")";
        if (chunk.tags() != null && !chunk.tags().isEmpty()) {
            base += "\nTags: " + String.join(", ", chunk.tags());
        }
        return base + "\n" + chunk.content();
    }

    /**
     * Similaridade de cosseno entre dois vetores de mesma dimensão.
     */
    public static double cosineSimilarity(float[] a, float[] b) {
        if (a == null || b == null || a.length == 0 || a.length != b.length) {
            return 0.0;
        }
        double dot = 0.0;
        double normA = 0.0;
        double normB = 0.0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0.0 || normB == 0.0) {
            return 0.0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private static String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            return Integer.toHexString(input.hashCode());
        }
    }

    public record QueryEmbedding(String model, float[] vector) {
    }

    public record CachedEmbedding(String hash, String model, List<Float> values) {
    }
}
