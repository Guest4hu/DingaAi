package br.edu.usjt.dinga.rag;

import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Motor RAG Híbrido em Java puro:
 * - BM25 bilíngue PT-BR ↔ EN com expansão de sinônimos, stemming leve e stopwords cirúrgicas
 * - Tolerância a erros de digitação (Fuzzy Matching Damerau-Levenshtein ≤ 1–2 sobre vocabulário indexado)
 * - Bônus de frase exata e proximidade de termos
 * - Expansão contextual multi-turno (histórico para perguntas curtas/referenciais)
 * - Busca Híbrida BM25 + Embeddings Vetoriais (Gemini Embeddings com cache local) via RRF (Reciprocal Rank Fusion)
 *   com fast-path de baixa latência e fallback automático 100% offline para BM25 local
 * - Diversidade de fontes (limita redundância do mesmo docId quando há fontes complementares relevantes)
 */
@Component
public class Bm25RagRetriever {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double MIN_ABSOLUTE_SCORE = 0.85;
    private static final double RELATIVE_SCORE_THRESHOLD = 0.35;
    private static final double SYNONYM_WEIGHT = 0.75;
    private static final double FUZZY_DIST1_WEIGHT = 0.65;
    private static final double FUZZY_DIST2_WEIGHT = 0.45;
    private static final double FUZZY_SYNONYM_WEIGHT = 0.50;
    private static final double RRF_K = 60.0;
    private static final double RRF_SCALE = 100.0;
    private static final double MIN_COSINE_SIMILARITY = 0.48;
    private static final double HIGH_CONFIDENCE_BM25_FAST_PATH = 7.5;
    private static final int TITLE_META_TOKEN_WEIGHT = 2;
    private static final int TAG_TOKEN_WEIGHT = 3;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");

    private static final Set<String> PROTECTED_STEMS = Set.of(
            "bias", "vies", "ods", "docs", "aws", "https", "status", "class", "pass", "less",
            "this", "focus", "campus", "linux", "redis", "devops", "news", "series",
            "process", "access", "address", "progress", "yes", "bus", "gas", "plus", "tts"
    );

    private static final Pattern REFERENTIAL_CUE_PATTERN = Pattern.compile(
            "\\b(?:disso|nisso|dele|dela|deles|delas|desse|dessa|nesse|nessa|isso|isto"
                    + "|mais exemplos|outro exemplo|outros exemplos|me da mais|me de mais"
                    + "|explica melhor|explique melhor|continua|continue"
                    + "|more examples|explain more|tell me more)\\b"
    );

    private static final Set<String> STOPWORDS = Set.of(
            // Preposições, artigos, pronomes e conectivos (PT-BR)
            "de", "do", "da", "dos", "das", "em", "no", "na", "nos", "nas", "para", "pra",
            "com", "por", "que", "como", "uma", "um", "uns", "umas", "os", "as", "ao", "aos",
            "ou", "se", "eu", "voce", "meu", "minha", "meus", "minhas", "seu", "sua", "seus", "suas",
            "nosso", "nossa", "dele", "dela", "sobre", "qual", "quais", "mais", "muito", "muita",
            "esta", "este", "esse", "essa", "isso", "isto", "aquele", "aquela", "tem", "sao", "ser",
            "entre", "ate", "sem", "nem", "mas", "porem", "entao", "porque", "quando", "onde", "quem",
            "tudo", "todos", "todas", "todo", "toda", "outro", "outra", "outros", "outras",
            "algum", "alguma", "alguns", "algumas", "mesmo", "mesma", "tambem", "ainda", "ja", "so",
            "cada", "qualquer", "antes", "depois", "hoje", "agora", "aqui", "ali", "la", "nao", "sim",
            "disso", "nisso", "desse", "dessa", "nesse", "nessa",
            // Verbos e substantivos genéricos de perguntas conversacionais (PT-BR)
            "funciona", "funcionam", "funcionar", "diferenca", "diferencas", "significa", "significam",
            "explica", "explicar", "exemplo", "exemplos", "frase", "frases", "palavra", "palavras",
            "termo", "termos", "errar", "erro", "erros", "certo", "correto", "correta",
            "usar", "uso", "usa", "usado", "fazer", "faz", "feito", "dizer", "diz", "falar", "fala",
            "quero", "preciso", "gostaria", "posso", "pode", "podem", "devo", "deve",
            "estou", "estao", "fui", "foi", "era", "seria", "tenho", "tinha", "ter",
            "montar", "comecar", "aprender", "entender", "saber", "ajuda", "ajudar", "melhor", "detalhe", "detalhes",
            // Stopwords em inglês (EN)
            "the", "and", "for", "with", "how", "what", "when", "where", "which", "this", "that",
            "these", "those", "are", "was", "were", "can", "could", "you", "your", "from", "into",
            "about", "does", "mean", "between", "difference", "explain", "tell", "please", "have", "has"
    );

    private static final Map<String, List<String>> BILINGUAL_SYNONYMS = buildBilingualSynonyms();

    private static final Map<String, List<String>> PHRASE_SYNONYMS = Map.ofEntries(
            Map.entry("falso amigo", List.of("falso", "cognato", "false", "friend")),
            Map.entry("falsos amigos", List.of("falso", "cognato", "false", "friend")),
            Map.entry("false friend", List.of("falso", "cognato", "actually", "pretend")),
            Map.entry("na verdade", List.of("actually", "falso", "cognato")),
            Map.entry("hoje em dia", List.of("currently", "nowadays", "actually")),
            Map.entry("plano b", List.of("fallback", "contingencia", "local")),
            Map.entry("carga util", List.of("payload", "json", "endpoint")),
            Map.entry("reuniao diaria", List.of("daily", "standup", "blocker")),
            Map.entry("dados pessoais", List.of("lgpd", "privacidade", "privacy")),
            Map.entry("marca dagua", List.of("watermark", "mtg", "rotulagem")),
            Map.entry("marca d'agua", List.of("watermark", "mtg", "rotulagem")),
            Map.entry("leitura rapida", List.of("skimming", "scanning", "documentacao")),
            Map.entry("ajuste fino", List.of("fine", "tuning", "rag")),
            Map.entry("limite de taxa", List.of("rate", "limit", "429", "fallback")),
            Map.entry("baixa conectividade", List.of("ods", "offline", "fallback", "conectividade")),
            Map.entry("revisao de codigo", List.of("code", "review", "pull", "request", "lgtm")),
            Map.entry("phrasal verb", List.of("phrasal", "verbs", "set", "spin", "roll", "figure")),
            Map.entry("causa raiz", List.of("root", "cause", "stack", "trace", "debugging")),
            Map.entry("pilha de chamadas", List.of("stack", "trace", "debugging", "exception")),
            Map.entry("busca hibrida", List.of("hybrid", "rrf", "embed", "embeddings", "bm25", "cosine")),
            Map.entry("repeticao espacada", List.of("anki", "spaced", "repetition", "listening")),
            Map.entry("engenharia de prompt", List.of("prompt", "engineering", "few", "zero", "shot"))
    );

    private static Map<String, List<String>> buildBilingualSynonyms() {
        Map<String, List<String>> map = new HashMap<>();
        // Infraestrutura, Cloud, APIs e Git
        map.put("atraso", List.of("latency", "latencia"));
        map.put("lentidao", List.of("latency", "latencia"));
        map.put("demora", List.of("latency", "latencia"));
        map.put("latencia", List.of("latency", "atraso", "ttft"));
        map.put("latency", List.of("latencia", "atraso", "ttft"));
        map.put("publicar", List.of("deploy", "push"));
        map.put("subir", List.of("deploy", "push", "spin"));
        map.put("implantar", List.of("deploy"));
        map.put("implantacao", List.of("deploy"));
        map.put("deploy", List.of("publicar", "implantacao", "producao"));
        map.put("rota", List.of("endpoint", "api"));
        map.put("endereco", List.of("endpoint", "api"));
        map.put("endpoint", List.of("rota", "api", "payload"));
        map.put("payload", List.of("json", "endpoint", "requisicao"));
        map.put("contingencia", List.of("fallback", "local"));
        map.put("offline", List.of("fallback", "conectividade", "local"));
        map.put("fallback", List.of("contingencia", "offline", "local"));
        map.put("empurrar", List.of("push", "git"));
        map.put("puxar", List.of("pull", "git"));
        map.put("push", List.of("empurrar", "git", "pull"));
        map.put("pull", List.of("puxar", "git", "push"));
        map.put("bloqueio", List.of("blocker", "daily"));
        map.put("impedimento", List.of("blocker", "daily"));
        map.put("blocker", List.of("bloqueio", "impedimento", "daily"));
        map.put("diaria", List.of("daily", "standup"));
        map.put("daily", List.of("diaria", "standup", "blocker"));
        // IA Generativa, Microsoft Learn, Embeddings e Governança
        map.put("alucinacao", List.of("hallucination", "grounding", "rag"));
        map.put("alucinacoe", List.of("hallucination", "grounding", "rag"));
        map.put("hallucination", List.of("alucinacao", "grounding", "rag"));
        map.put("ancoragem", List.of("grounding", "rag"));
        map.put("ancorar", List.of("grounding", "rag"));
        map.put("ground", List.of("ancoragem", "rag", "hallucination"));
        map.put("vetor", List.of("embedding", "cosine", "rrf", "hibrido"));
        map.put("vetore", List.of("embedding", "cosine", "rrf", "hibrido"));
        map.put("vetoriai", List.of("embedding", "cosine", "rrf", "hibrido"));
        map.put("semantica", List.of("embedding", "cosine", "rrf", "hibrido"));
        map.put("semantico", List.of("embedding", "cosine", "rrf", "hibrido"));
        map.put("cosseno", List.of("cosine", "embedding", "rrf"));
        map.put("embedding", List.of("vetor", "cosine", "rrf", "hibrido"));
        map.put("embed", List.of("vetor", "embeddings", "cosine", "rrf", "hibrido"));
        map.put("vies", List.of("bias", "etica", "mtg"));
        map.put("viese", List.of("bias", "etica", "mtg"));
        map.put("preconceito", List.of("bias", "vies", "etica"));
        map.put("bias", List.of("vies", "etica", "mtg"));
        map.put("privacidade", List.of("lgpd", "privacy", "mtg"));
        map.put("privacy", List.of("lgpd", "privacidade", "mtg"));
        map.put("governanca", List.of("mtg", "etica", "lgpd"));
        map.put("transparencia", List.of("mtg", "watermark", "etica"));
        map.put("inclusao", List.of("ods", "acessibilidade"));
        map.put("acessibilidade", List.of("ods", "inclusao"));
        map.put("plagio", List.of("socratico", "etica", "mtg"));
        // Falsos cognatos, gramática, phrasal verbs, debugging, PRs e pronúncia
        map.put("cognato", List.of("false", "friend", "actually", "pretend"));
        map.put("atualmente", List.of("currently", "nowadays", "actually"));
        map.put("currently", List.of("atualmente", "nowadays", "actually"));
        map.put("actually", List.of("verdade", "currently", "cognato"));
        map.put("livraria", List.of("bookstore", "library", "cognato"));
        map.put("biblioteca", List.of("library", "bookstore", "cognato"));
        map.put("library", List.of("biblioteca", "livraria", "cognato"));
        map.put("fingir", List.of("pretend", "intend", "cognato"));
        map.put("pretender", List.of("intend", "plan", "pretend"));
        map.put("pretend", List.of("fingir", "intend", "cognato"));
        map.put("passado", List.of("past", "yesterday", "worked"));
        map.put("ontem", List.of("yesterday", "past"));
        map.put("past", List.of("passado", "yesterday"));
        map.put("presente", List.of("present", "simple", "rotina"));
        map.put("present", List.of("presente", "simple"));
        map.put("idade", List.of("age", "year", "have"));
        map.put("documentacao", List.of("documentation", "docs", "skimming", "scanning"));
        map.put("documentation", List.of("documentacao", "docs", "skimming", "scanning"));
        map.put("depuracao", List.of("debugging", "stack", "trace", "log"));
        map.put("depurar", List.of("debugging", "stack", "trace", "log"));
        map.put("excecao", List.of("exception", "stack", "trace", "debugging"));
        map.put("revisao", List.of("review", "pull", "request", "lgtm"));
        map.put("pronuncia", List.of("pronuncia", "listening", "shadowing", "cache", "queue"));
        map.put("audio", List.of("tts", "listening", "shadowing", "pronuncia"));
        map.put("escuta", List.of("listening", "shadowing", "tts"));
        map.put("flashcard", List.of("anki", "spaced", "repetition"));
        map.put("reverter", List.of("roll", "back", "phrasal"));
        return Map.copyOf(map);
    }

    private final KnowledgeBaseLoader loader;
    private final GeminiEmbeddingClient embeddingClient;
    private volatile IndexSnapshot snapshot;

    public Bm25RagRetriever(KnowledgeBaseLoader loader) {
        this(loader, null);
    }

    @Autowired
    public Bm25RagRetriever(KnowledgeBaseLoader loader, GeminiEmbeddingClient embeddingClient) {
        this.loader = loader;
        this.embeddingClient = embeddingClient;
        List<KnowledgeChunk> initialChunks = loader.getChunks();
        this.snapshot = buildSnapshot(initialChunks, loader.getVersion());
        if (this.embeddingClient != null && this.embeddingClient.isEnabled()) {
            this.embeddingClient.prewarmChunksAsync(initialChunks);
        }
    }

    /**
     * Força a atualização imediata do índice invertido BM25 após inserção ou remoção de documento
     * e dispara pré-aquecimento assíncrono de embeddings para novos chunks (sem bloquear).
     */
    public synchronized void refreshIndex() {
        List<KnowledgeChunk> chunks = loader.getChunks();
        this.snapshot = buildSnapshot(chunks, loader.getVersion());
        if (this.embeddingClient != null && this.embeddingClient.isEnabled()) {
            this.embeddingClient.prewarmChunksAsync(chunks);
        }
    }

    public boolean isHybridEmbeddingsEnabled() {
        return embeddingClient != null && embeddingClient.isEnabled();
    }

    public String ragModeLabel() {
        if (embeddingClient != null && embeddingClient.isEnabled() && embeddingClient.cachedChunkCount() > 0) {
            return "Híbrido BM25 + Embeddings RRF (" + embeddingClient.activeCachedModel() + ")";
        }
        if (embeddingClient != null && embeddingClient.isEnabled()) {
            return "BM25 Local + Pré-aquecimento Embeddings (" + embeddingClient.embeddingModel() + ")";
        }
        return "BM25 Local (Fuzzy + Frase Exata + Diversidade)";
    }

    public int cachedEmbeddingsCount() {
        return embeddingClient == null ? 0 : embeddingClient.cachedChunkCount();
    }

    private IndexSnapshot ensureIndexUpToDate() {
        IndexSnapshot current = this.snapshot;
        int loaderVersion = loader.getVersion();
        if (current.version() != loaderVersion) {
            synchronized (this) {
                current = this.snapshot;
                if (current.version() != loader.getVersion()) {
                    List<KnowledgeChunk> chunks = loader.getChunks();
                    current = buildSnapshot(chunks, loader.getVersion());
                    this.snapshot = current;
                    if (this.embeddingClient != null && this.embeddingClient.isEnabled()) {
                        this.embeddingClient.prewarmChunksAsync(chunks);
                    }
                }
            }
        }
        return current;
    }

    private static IndexSnapshot buildSnapshot(List<KnowledgeChunk> chunks, int version) {
        List<IndexedChunk> indexed = new ArrayList<>(chunks.size());
        Map<String, Integer> df = new HashMap<>();
        long totalLength = 0;

        for (KnowledgeChunk chunk : chunks) {
            Map<String, Integer> tf = new HashMap<>();
            String joinedTags = (chunk.tags() == null || chunk.tags().isEmpty())
                    ? ""
                    : String.join(" ", chunk.tags());
            // Título, seção, categoria e tags bilíngues têm peso maior para priorizar correspondências conceituais
            addTokens(tf, tokenize(chunk.title() + " " + chunk.section() + " " + chunk.category(), true), TITLE_META_TOKEN_WEIGHT);
            if (!joinedTags.isBlank()) {
                addTokens(tf, tokenize(joinedTags, true), TAG_TOKEN_WEIGHT);
            }
            addTokens(tf, tokenize(chunk.content(), true), 1);

            int length = tf.values().stream().mapToInt(Integer::intValue).sum();
            totalLength += length;

            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }

            String normTitleSection = normalizeForPhraseMatch(chunk.title() + " " + chunk.section());
            String normTags = normalizeForPhraseMatch(joinedTags);
            String normFullText = normTitleSection
                    + (normTags.isEmpty() ? "" : " " + normTags)
                    + " " + normalizeForPhraseMatch(chunk.content());
            indexed.add(new IndexedChunk(chunk, tf, length, normTitleSection, normTags, normFullText));
        }

        double avgLen = indexed.isEmpty() ? 1.0 : (double) totalLength / indexed.size();
        return new IndexSnapshot(version, List.copyOf(indexed), Map.copyOf(df), avgLen);
    }

    public List<KnowledgeChunk> retrieve(String query, String level, int topK) {
        return retrieve(query, level, topK, List.of(), false);
    }

    public List<KnowledgeChunk> retrieve(String query, String level, int topK, List<ChatTurn> history) {
        return retrieve(query, level, topK, history, false);
    }

    /**
     * Recupera os trechos mais relevantes combinando:
     * 1. Expansão contextual multi-turno (quando a pergunta atual é curta ou referencial)
     * 2. BM25 bilíngue + Fuzzy Matching (distância ≤ 1–2) + Bônus de frase exata/proximidade
     * 3. Fusão RRF com Embeddings Vetoriais Gemini (quando habilitado, com cache pronto e fora do lowDataMode)
     * 4. Diversidade de fontes por docId
     */
    public List<KnowledgeChunk> retrieve(String query, String level, int topK, List<ChatTurn> history, boolean lowDataMode) {
        IndexSnapshot index = ensureIndexUpToDate();
        if (index.indexedChunks().isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        String effectiveQuery = enrichQueryWithHistory(query, history);

        List<KnowledgeChunk> bm25Ranked = rankWithBm25(effectiveQuery, level, index);

        // Fallback extra de histórico caso a consulta enriquecida ainda não tenha tido match lexical
        if (bm25Ranked.isEmpty() && history != null && !history.isEmpty() && effectiveQuery.equals(query)) {
            ChatTurn last = history.getLast();
            String fallbackQuery = query + " " + last.question() + " " + last.practicePrompt();
            bm25Ranked = rankWithBm25(fallbackQuery, level, index);
            if (!bm25Ranked.isEmpty()) {
                effectiveQuery = fallbackQuery;
            }
        }

        // Busca Híbrida (BM25 + Embeddings via RRF) com fast-path de baixa latência
        boolean canUseEmbeddings = !lowDataMode
                && embeddingClient != null
                && embeddingClient.isEnabled()
                && embeddingClient.cachedChunkCount() > 0;

        boolean bm25HighConfidence = !bm25Ranked.isEmpty()
                && bm25Ranked.getFirst().score() >= HIGH_CONFIDENCE_BM25_FAST_PATH;

        if (canUseEmbeddings && !bm25HighConfidence) {
            Optional<GeminiEmbeddingClient.QueryEmbedding> queryVec = embeddingClient.embedQueryWithModel(effectiveQuery);
            if (queryVec.isPresent()) {
                List<KnowledgeChunk> vectorRanked = rankWithVectors(
                        queryVec.get().vector(),
                        queryVec.get().model(),
                        level,
                        index
                );
                if (!vectorRanked.isEmpty()) {
                    List<KnowledgeChunk> fused = fuseWithRrf(bm25Ranked, vectorRanked);
                    return applyCutoffAndDiversity(fused, limit);
                }
            }
        }

        return applyCutoffAndDiversity(bm25Ranked, limit);
    }

    private List<KnowledgeChunk> rankWithBm25(String query, String level, IndexSnapshot index) {
        Map<String, Double> weightedQueryTerms = expandQueryTermsWithFuzzy(query, index.documentFrequency());
        if (weightedQueryTerms.isEmpty()) {
            return List.of();
        }

        List<String> rawMeaningfulTokens = extractRawMeaningfulTokens(query);
        List<String> exactPhrases = buildCandidatePhrases(rawMeaningfulTokens);

        int totalDocs = index.indexedChunks().size();
        long originalTermCount = weightedQueryTerms.values().stream()
                .filter(w -> w >= 1.0)
                .count();
        List<KnowledgeChunk> scored = new ArrayList<>();

        for (IndexedChunk doc : index.indexedChunks()) {
            double score = 0.0;
            int exactOrSynonymHits = 0;
            int fuzzyHits = 0;
            for (Map.Entry<String, Double> entry : weightedQueryTerms.entrySet()) {
                String term = entry.getKey();
                double termWeight = entry.getValue();
                int tf = doc.termFrequency().getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                if (termWeight >= SYNONYM_WEIGHT) {
                    exactOrSynonymHits++;
                } else {
                    fuzzyHits++;
                }
                int df = index.documentFrequency().getOrDefault(term, 0);
                double idf = Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));
                double norm = 1.0 - B + B * (doc.length() / index.avgDocLength());
                score += termWeight * idf * ((tf * (K1 + 1.0)) / (tf + K1 * norm));
            }

            // Evita falsos positivos por colisão fuzzy isolada em consultas fora do domínio
            // (ex.: 1 palavra parecida em 4 termos desconhecidos sem nenhum match exato/sinônimo).
            if (exactOrSynonymHits == 0 && originalTermCount >= 3 && fuzzyHits < 2) {
                continue;
            }

            if (score > 0.0) {
                // Bônus de frase exata e proximidade de termos
                double phraseMultiplier = computePhraseAndProximityMultiplier(doc, exactPhrases, rawMeaningfulTokens);
                score *= phraseMultiplier;

                // Bônus leve para o nível selecionado, mantendo acesso a outros níveis (combate a bolhas)
                if (level != null && !level.isBlank() && doc.chunk().level().equalsIgnoreCase(level.trim())) {
                    score *= 1.15;
                }
                double rounded = Math.round(score * 100.0) / 100.0;
                if (rounded >= MIN_ABSOLUTE_SCORE) {
                    scored.add(doc.chunk().withScore(rounded));
                }
            }
        }

        return scored.stream()
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .toList();
    }

    private List<KnowledgeChunk> rankWithVectors(float[] queryVector, String embeddingModel, String level, IndexSnapshot index) {
        List<KnowledgeChunk> scored = new ArrayList<>();
        for (IndexedChunk doc : index.indexedChunks()) {
            Optional<float[]> chunkVec = embeddingClient.getCachedChunkEmbedding(doc.chunk(), embeddingModel);
            if (chunkVec.isEmpty()) {
                continue;
            }
            double cos = GeminiEmbeddingClient.cosineSimilarity(queryVector, chunkVec.get());
            if (cos >= MIN_COSINE_SIMILARITY) {
                double effective = cos;
                if (level != null && !level.isBlank() && doc.chunk().level().equalsIgnoreCase(level.trim())) {
                    effective *= 1.04;
                }
                scored.add(doc.chunk().withScore(Math.round(effective * 10000.0) / 10000.0));
            }
        }
        return scored.stream()
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .toList();
    }

    /**
     * Combina o ranking lexical (BM25) com o ranking semântico (Embeddings) via Reciprocal Rank Fusion (RRF).
     */
    public static List<KnowledgeChunk> fuseWithRrf(List<KnowledgeChunk> bm25Ranked, List<KnowledgeChunk> vectorRanked) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, KnowledgeChunk> byId = new LinkedHashMap<>();

        for (int i = 0; i < bm25Ranked.size(); i++) {
            KnowledgeChunk chunk = bm25Ranked.get(i);
            byId.putIfAbsent(chunk.id(), chunk);
            rrfScores.merge(chunk.id(), RRF_SCALE / (RRF_K + (i + 1)), Double::sum);
        }
        for (int i = 0; i < vectorRanked.size(); i++) {
            KnowledgeChunk chunk = vectorRanked.get(i);
            byId.putIfAbsent(chunk.id(), chunk);
            rrfScores.merge(chunk.id(), RRF_SCALE / (RRF_K + (i + 1)), Double::sum);
        }

        List<KnowledgeChunk> fused = new ArrayList<>(byId.size());
        for (Map.Entry<String, KnowledgeChunk> entry : byId.entrySet()) {
            double score = Math.round(rrfScores.getOrDefault(entry.getKey(), 0.0) * 100.0) / 100.0;
            fused.add(entry.getValue().withScore(score));
        }
        fused.sort(Comparator.comparingDouble(KnowledgeChunk::score).reversed());
        return List.copyOf(fused);
    }

    /**
     * Aplica corte relativo/absoluto e diversidade de fontes (prioriza docIds distintos quando há
     * fontes complementares acima do limiar, preservando sempre o 1º colocado e preenchendo até limit).
     */
    private static List<KnowledgeChunk> applyCutoffAndDiversity(List<KnowledgeChunk> sorted, int limit) {
        if (sorted == null || sorted.isEmpty()) {
            return List.of();
        }
        double topScore = sorted.getFirst().score();
        double minCutoff = Math.max(MIN_ABSOLUTE_SCORE, topScore * RELATIVE_SCORE_THRESHOLD);

        List<KnowledgeChunk> eligible = sorted.stream()
                .filter(chunk -> chunk.score() >= minCutoff)
                .toList();
        if (eligible.isEmpty()) {
            return List.of();
        }
        if (limit == 1 || eligible.size() == 1) {
            return List.of(eligible.getFirst());
        }

        List<KnowledgeChunk> selected = new ArrayList<>(limit);
        Set<String> usedDocIds = new HashSet<>();
        List<KnowledgeChunk> overflowSameDoc = new ArrayList<>();

        for (KnowledgeChunk chunk : eligible) {
            if (selected.size() >= limit) {
                break;
            }
            if (usedDocIds.add(chunk.docId())) {
                selected.add(chunk);
            } else {
                overflowSameDoc.add(chunk);
            }
        }

        for (KnowledgeChunk extra : overflowSameDoc) {
            if (selected.size() >= limit) {
                break;
            }
            selected.add(extra);
        }

        return List.copyOf(selected);
    }

    /**
     * Enriquece consultas curtas ou referenciais ("disso", "mais exemplos", "como usar") com termos
     * conceituais do turno anterior para manter contexto multi-turno no RAG.
     */
    public static String enrichQueryWithHistory(String query, List<ChatTurn> history) {
        if (query == null || query.isBlank() || history == null || history.isEmpty()) {
            return query == null ? "" : query;
        }
        String normalized = normalizeText(query);
        List<String> currentTokens = tokenize(query, true);

        boolean isShortQuery = currentTokens.size() <= 2;
        boolean hasReferentialCue = REFERENTIAL_CUE_PATTERN.matcher(normalized).find();

        if (!isShortQuery && !hasReferentialCue) {
            return query;
        }

        Set<String> currentSet = new HashSet<>(currentTokens);
        LinkedHashSet<String> contextTerms = new LinkedHashSet<>();

        int startIdx = Math.max(0, history.size() - 2);
        for (int i = history.size() - 1; i >= startIdx && contextTerms.size() < 6; i--) {
            ChatTurn turn = history.get(i);
            for (String token : extractRawMeaningfulTokens(turn.question())) {
                String stemmed = stemToken(token);
                if (!currentSet.contains(stemmed) && contextTerms.size() < 6) {
                    contextTerms.add(token);
                }
            }
            if (contextTerms.size() < 4 && turn.practicePrompt() != null && !turn.practicePrompt().isBlank()) {
                for (String token : extractRawMeaningfulTokens(turn.practicePrompt())) {
                    String stemmed = stemToken(token);
                    if (!currentSet.contains(stemmed) && contextTerms.size() < 6) {
                        contextTerms.add(token);
                    }
                }
            }
        }

        if (contextTerms.isEmpty()) {
            return query;
        }
        return query + " " + String.join(" ", contextTerms);
    }

    /**
     * Expande termos da consulta com sinônimos bilíngues e fuzzy matching leve sobre o vocabulário indexado.
     */
    private static Map<String, Double> expandQueryTermsWithFuzzy(String query, Map<String, Integer> vocabularyDf) {
        Map<String, Double> weighted = new LinkedHashMap<>(expandQueryTerms(query));
        if (weighted.isEmpty() || vocabularyDf == null || vocabularyDf.isEmpty()) {
            return weighted;
        }

        List<String> originalTerms = new ArrayList<>(weighted.keySet());
        for (String term : originalTerms) {
            // Aplica fuzzy apenas para termos originais (peso 1.0) ausentes no vocabulário exato
            if (weighted.getOrDefault(term, 0.0) < 1.0 || vocabularyDf.containsKey(term) || term.length() < 4) {
                continue;
            }
            int maxDist = term.length() >= 8 ? 2 : 1;
            String bestMatch = null;
            int bestDist = Integer.MAX_VALUE;
            int bestDf = -1;

            for (Map.Entry<String, Integer> vocabEntry : vocabularyDf.entrySet()) {
                String candidate = vocabEntry.getKey();
                if (candidate.length() < 4 || Math.abs(candidate.length() - term.length()) > maxDist) {
                    continue;
                }
                if (!hasCompatiblePrefix(term, candidate)) {
                    continue;
                }
                int dist = damerauLevenshteinDistance(term, candidate, maxDist);
                if (dist > 0 && dist <= maxDist) {
                    // Para distância 2, exige palavra longa (>= 7 chars); prefixo compatível já foi validado acima
                    if (dist == 2 && candidate.length() < 7) {
                        continue;
                    }
                    int df = vocabEntry.getValue();
                    if (dist < bestDist || (dist == bestDist && df > bestDf)) {
                        bestDist = dist;
                        bestDf = df;
                        bestMatch = candidate;
                    }
                }
            }

            if (bestMatch != null) {
                double fuzzyWeight = bestDist == 1 ? FUZZY_DIST1_WEIGHT : FUZZY_DIST2_WEIGHT;
                weighted.putIfAbsent(bestMatch, fuzzyWeight);

                List<String> synonyms = BILINGUAL_SYNONYMS.get(bestMatch);
                if (synonyms != null) {
                    for (String syn : synonyms) {
                        weighted.putIfAbsent(stemToken(syn), FUZZY_SYNONYM_WEIGHT);
                    }
                }
            }
        }
        return weighted;
    }

    /**
     * Bloqueio leve de prefixo para evitar colisões semânticas falsas (ex.: "panela" vs "janela"),
     * permitindo mesma inicial, transposição inicial ou 'h' mudo inicial (ex.: "halucinacao" -> "alucinacao").
     */
    private static boolean hasCompatiblePrefix(String term, String candidate) {
        if (term.charAt(0) == candidate.charAt(0)) {
            return true;
        }
        if (term.length() >= 2 && candidate.length() >= 2
                && term.charAt(0) == candidate.charAt(1) && term.charAt(1) == candidate.charAt(0)) {
            return true;
        }
        if (term.charAt(0) == 'h' && term.length() >= 2 && term.charAt(1) == candidate.charAt(0)) {
            return true;
        }
        return candidate.charAt(0) == 'h' && candidate.length() >= 2 && candidate.charAt(1) == term.charAt(0);
    }

    /**
     * Distância de edição Damerau-Levenshtein (inserção, remoção, substituição e transposição adjacente)
     * com encerramento antecipado se exceder maxDistance.
     */
    public static int damerauLevenshteinDistance(String a, String b, int maxDistance) {
        if (a == null || b == null) {
            return maxDistance + 1;
        }
        int lenA = a.length();
        int lenB = b.length();
        if (Math.abs(lenA - lenB) > maxDistance) {
            return maxDistance + 1;
        }
        if (a.equals(b)) {
            return 0;
        }

        int[][] dp = new int[lenA + 1][lenB + 1];
        for (int i = 0; i <= lenA; i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= lenB; j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= lenA; i++) {
            int rowMin = Integer.MAX_VALUE;
            char ca = a.charAt(i - 1);
            for (int j = 1; j <= lenB; j++) {
                char cb = b.charAt(j - 1);
                int cost = (ca == cb) ? 0 : 1;
                int val = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
                if (i > 1 && j > 1 && ca == b.charAt(j - 2) && a.charAt(i - 2) == cb) {
                    val = Math.min(val, dp[i - 2][j - 2] + 1);
                }
                dp[i][j] = val;
                if (val < rowMin) {
                    rowMin = val;
                }
            }
            if (rowMin > maxDistance) {
                return maxDistance + 1;
            }
        }
        return dp[lenA][lenB];
    }

    private static double computePhraseAndProximityMultiplier(
            IndexedChunk doc,
            List<String> exactPhrases,
            List<String> rawMeaningfulTokens
    ) {
        double multiplier = 1.0;

        boolean titlePhraseHit = false;
        boolean tagPhraseHit = false;
        boolean contentPhraseHit = false;
        for (String phrase : exactPhrases) {
            if (doc.normalizedTitleSection().contains(phrase)) {
                titlePhraseHit = true;
                break;
            }
            if (!tagPhraseHit && !doc.normalizedTags().isEmpty() && doc.normalizedTags().contains(phrase)) {
                tagPhraseHit = true;
            }
            if (!contentPhraseHit && doc.normalizedFullText().contains(phrase)) {
                contentPhraseHit = true;
            }
        }

        if (titlePhraseHit) {
            multiplier *= 1.25;
        } else if (tagPhraseHit) {
            multiplier *= 1.22;
        } else if (contentPhraseHit) {
            multiplier *= 1.15;
        }

        // Bônus leve de proximidade quando >= 2 termos relevantes aparecem próximos (<= 45 chars)
        if (!titlePhraseHit && rawMeaningfulTokens.size() >= 2) {
            List<Integer> positions = new ArrayList<>();
            for (String token : rawMeaningfulTokens) {
                int pos = doc.normalizedFullText().indexOf(token);
                if (pos >= 0) {
                    positions.add(pos);
                }
            }
            if (positions.size() >= 2) {
                positions.sort(Integer::compareTo);
                for (int i = 1; i < positions.size(); i++) {
                    if (positions.get(i) - positions.get(i - 1) <= 45) {
                        multiplier *= 1.08;
                        break;
                    }
                }
            }
        }

        return Math.min(multiplier, 1.35);
    }

    private static List<String> extractRawMeaningfulTokens(String text) {
        String normalized = normalizeText(text);
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String raw = matcher.group();
            if (!STOPWORDS.contains(raw) && !STOPWORDS.contains(stemToken(raw))) {
                tokens.add(raw);
            }
        }
        return tokens;
    }

    private static List<String> buildCandidatePhrases(List<String> rawTokens) {
        if (rawTokens.size() < 2) {
            return List.of();
        }
        List<String> phrases = new ArrayList<>();
        for (int i = 0; i < rawTokens.size() - 1; i++) {
            String bigram = rawTokens.get(i) + " " + rawTokens.get(i + 1);
            if (bigram.length() >= 6) {
                phrases.add(bigram);
            }
            if (i < rawTokens.size() - 2) {
                String trigram = bigram + " " + rawTokens.get(i + 2);
                phrases.add(trigram);
            }
        }
        return phrases;
    }

    /**
     * Tokeniza a consulta com stemming leve e expande sinônimos bilíngues (PT ↔ EN) com peso 0.75.
     */
    public static Map<String, Double> expandQueryTerms(String query) {
        if (query == null || query.isBlank()) {
            return Map.of();
        }
        String normalized = normalizeText(query);
        List<String> queryTokens = tokenize(query, true);
        if (queryTokens.isEmpty()) {
            queryTokens = tokenize(query, false);
        }
        if (queryTokens.isEmpty()) {
            return Map.of();
        }

        Map<String, Double> weighted = new LinkedHashMap<>();
        for (String token : queryTokens) {
            weighted.put(token, 1.0);
            List<String> synonyms = BILINGUAL_SYNONYMS.get(token);
            if (synonyms == null && token.length() >= 5 && (token.endsWith("os") || token.endsWith("as"))) {
                synonyms = BILINGUAL_SYNONYMS.get(token.substring(0, token.length() - 1));
            }
            if (synonyms != null) {
                for (String syn : synonyms) {
                    String stemmedSyn = stemToken(syn);
                    weighted.putIfAbsent(stemmedSyn, SYNONYM_WEIGHT);
                }
            }
        }

        // Expansão de expressões compostas PT ↔ EN (ex.: "falso amigo", "plano b", "busca hibrida")
        for (Map.Entry<String, List<String>> entry : PHRASE_SYNONYMS.entrySet()) {
            if (normalized.contains(entry.getKey())) {
                for (String syn : entry.getValue()) {
                    String stemmedSyn = stemToken(syn);
                    weighted.putIfAbsent(stemmedSyn, SYNONYM_WEIGHT);
                }
            }
        }

        return weighted;
    }

    public static String normalizeText(String input) {
        if (input == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(input, Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
    }

    private static String normalizeForPhraseMatch(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        return normalizeText(input).replaceAll("[^a-z0-9]+", " ").trim();
    }

    /**
     * Normalização leve de sufixos (-ies, -ied, -ing, -ed, -es, -s) preservando radicais curtos e siglas.
     */
    public static String stemToken(String token) {
        if (token == null || token.length() < 4 || PROTECTED_STEMS.contains(token)) {
            return token == null ? "" : token;
        }
        if (token.length() >= 5 && (token.endsWith("ies") || token.endsWith("ied"))) {
            return token.substring(0, token.length() - 3) + "y";
        }
        if (token.length() >= 6 && token.endsWith("ing")) {
            String base = token.substring(0, token.length() - 3);
            if (base.length() >= 3 && base.charAt(base.length() - 1) == base.charAt(base.length() - 2)) {
                return base.substring(0, base.length() - 1);
            }
            return base;
        }
        if (token.length() >= 5 && token.endsWith("ed")) {
            String base = token.substring(0, token.length() - 2);
            if (base.length() >= 3 && base.charAt(base.length() - 1) == base.charAt(base.length() - 2)) {
                return base.substring(0, base.length() - 1);
            }
            return base;
        }
        if (token.length() >= 5 && (token.endsWith("shes") || token.endsWith("ches")
                || token.endsWith("xes") || token.endsWith("zes") || token.endsWith("sses"))) {
            return token.substring(0, token.length() - 2);
        }
        if (token.endsWith("s")
                && !token.endsWith("ss")
                && !token.endsWith("us")
                && !token.endsWith("is")
                && !token.endsWith("os")
                && !token.endsWith("as")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }

    private static List<String> tokenize(String text, boolean filterStopwords) {
        String normalized = normalizeText(text);
        List<String> tokens = new ArrayList<>();
        Matcher matcher = TOKEN_PATTERN.matcher(normalized);
        while (matcher.find()) {
            String raw = matcher.group();
            if (filterStopwords && STOPWORDS.contains(raw)) {
                continue;
            }
            String stemmed = stemToken(raw);
            if (!filterStopwords || !STOPWORDS.contains(stemmed)) {
                tokens.add(stemmed);
            }
        }
        return tokens;
    }

    private static void addTokens(Map<String, Integer> tf, List<String> tokens, int weight) {
        for (String token : tokens) {
            tf.merge(token, weight, Integer::sum);
        }
    }

    private record IndexedChunk(
            KnowledgeChunk chunk,
            Map<String, Integer> termFrequency,
            int length,
            String normalizedTitleSection,
            String normalizedTags,
            String normalizedFullText
    ) {
    }

    private record IndexSnapshot(
            int version,
            List<IndexedChunk> indexedChunks,
            Map<String, Integer> documentFrequency,
            double avgDocLength
    ) {
    }
}
