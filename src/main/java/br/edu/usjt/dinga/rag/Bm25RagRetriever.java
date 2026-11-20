package br.edu.usjt.dinga.rag;

import br.edu.usjt.dinga.model.KnowledgeChunk;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Motor RAG leve em Java puro usando ranqueamento BM25 com suporte bilíngue PT-BR ↔ EN,
 * expansão de sinônimos técnicos/gramaticais, normalização leve de sufixos (stemming),
 * stopwords ampliadas, corte relativo de relevância (threshold) e reindexação dinâmica ao vivo.
 */
@Component
public class Bm25RagRetriever {

    private static final double K1 = 1.5;
    private static final double B = 0.75;
    private static final double MIN_ABSOLUTE_SCORE = 0.85;
    private static final double RELATIVE_SCORE_THRESHOLD = 0.35;
    private static final double SYNONYM_WEIGHT = 0.75;
    private static final Pattern TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");

    private static final Set<String> PROTECTED_STEMS = Set.of(
            "bias", "vies", "ods", "docs", "aws", "https", "status", "class", "pass", "less",
            "this", "focus", "campus", "linux", "redis", "devops", "news", "series",
            "process", "access", "address", "progress", "yes", "bus", "gas", "plus"
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
            // Verbos e substantivos genéricos de perguntas conversacionais (PT-BR)
            "funciona", "funcionam", "funcionar", "diferenca", "diferencas", "significa", "significam",
            "explica", "explicar", "exemplo", "exemplos", "frase", "frases", "palavra", "palavras",
            "termo", "termos", "errar", "erro", "erros", "certo", "correto", "correta",
            "usar", "uso", "usa", "usado", "fazer", "faz", "feito", "dizer", "diz", "falar", "fala",
            "quero", "preciso", "gostaria", "posso", "pode", "podem", "devo", "deve",
            "estou", "estao", "fui", "foi", "era", "seria", "tenho", "tinha", "ter",
            "montar", "comecar", "aprender", "entender", "saber", "ajuda", "ajudar",
            // Stopwords em inglês (EN)
            "the", "and", "for", "with", "how", "what", "when", "where", "which", "this", "that",
            "these", "those", "are", "was", "were", "can", "could", "you", "your", "from", "into",
            "about", "does", "mean", "between", "difference", "explain", "tell", "please", "have", "has"
    );

    /**
     * Mapa de expansão bilíngue PT ↔ EN para termos técnicos e gramaticais (token já normalizado/stemmed -> termos equivalentes).
     */
    private static final Map<String, List<String>> BILINGUAL_SYNONYMS = buildBilingualSynonyms();

    /**
     * Expressões compostas (bigramas/frases curtas) PT ↔ EN para expandir consultas naturais.
     */
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
            Map.entry("limite de taxa", List.of("rate", "limit", "tpm", "fallback")),
            Map.entry("baixa conectividade", List.of("ods", "offline", "fallback", "conectividade")),
            Map.entry("revisao de codigo", List.of("pull", "request", "review", "git"))
    );

    private static Map<String, List<String>> buildBilingualSynonyms() {
        Map<String, List<String>> map = new HashMap<>();
        // Infraestrutura, Cloud, APIs e Git
        map.put("atraso", List.of("latency", "latencia"));
        map.put("lentidao", List.of("latency", "latencia"));
        map.put("demora", List.of("latency", "latencia"));
        map.put("latencia", List.of("latency", "atraso"));
        map.put("latency", List.of("latencia", "atraso"));
        map.put("publicar", List.of("deploy", "push"));
        map.put("subir", List.of("deploy", "push"));
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
        // IA Generativa, Microsoft Learn e Governança
        map.put("alucinacao", List.of("hallucination", "grounding", "rag"));
        map.put("alucinacoe", List.of("hallucination", "grounding", "rag"));
        map.put("hallucination", List.of("alucinacao", "grounding", "rag"));
        map.put("ancoragem", List.of("grounding", "rag"));
        map.put("ancorar", List.of("grounding", "rag"));
        map.put("ground", List.of("ancoragem", "rag", "hallucination"));
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
        // Falsos cognatos e gramática PT ↔ EN
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
        return Map.copyOf(map);
    }

    private final KnowledgeBaseLoader loader;
    private volatile IndexSnapshot snapshot;

    public Bm25RagRetriever(KnowledgeBaseLoader loader) {
        this.loader = loader;
        this.snapshot = buildSnapshot(loader.getChunks(), loader.getVersion());
    }

    /**
     * Força a atualização imediata do índice invertido BM25 após inserção ou remoção de documento.
     */
    public synchronized void refreshIndex() {
        this.snapshot = buildSnapshot(loader.getChunks(), loader.getVersion());
    }

    private IndexSnapshot ensureIndexUpToDate() {
        IndexSnapshot current = this.snapshot;
        int loaderVersion = loader.getVersion();
        if (current.version() != loaderVersion) {
            synchronized (this) {
                current = this.snapshot;
                if (current.version() != loader.getVersion()) {
                    current = buildSnapshot(loader.getChunks(), loader.getVersion());
                    this.snapshot = current;
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
            // Título, seção e categoria têm peso maior para priorizar correspondências conceituais
            addTokens(tf, tokenize(chunk.title() + " " + chunk.section() + " " + chunk.category(), true), 2);
            addTokens(tf, tokenize(chunk.content(), true), 1);

            int length = tf.values().stream().mapToInt(Integer::intValue).sum();
            totalLength += length;

            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
            indexed.add(new IndexedChunk(chunk, tf, length));
        }

        double avgLen = indexed.isEmpty() ? 1.0 : (double) totalLength / indexed.size();
        return new IndexSnapshot(version, List.copyOf(indexed), Map.copyOf(df), avgLen);
    }

    /**
     * Recupera os trechos mais relevantes aplicando expansão bilíngue PT↔EN, stemming leve,
     * filtro de stopwords ampliado e corte relativo (threshold).
     */
    public List<KnowledgeChunk> retrieve(String query, String level, int topK) {
        IndexSnapshot index = ensureIndexUpToDate();
        if (index.indexedChunks().isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, topK);
        Map<String, Double> weightedQueryTerms = expandQueryTerms(query);
        if (weightedQueryTerms.isEmpty()) {
            return List.of();
        }

        int totalDocs = index.indexedChunks().size();
        List<KnowledgeChunk> scored = new ArrayList<>();

        for (IndexedChunk doc : index.indexedChunks()) {
            double score = 0.0;
            for (Map.Entry<String, Double> entry : weightedQueryTerms.entrySet()) {
                String term = entry.getKey();
                double termWeight = entry.getValue();
                int tf = doc.termFrequency().getOrDefault(term, 0);
                if (tf == 0) {
                    continue;
                }
                int df = index.documentFrequency().getOrDefault(term, 0);
                double idf = Math.log(1.0 + (totalDocs - df + 0.5) / (df + 0.5));
                double norm = 1.0 - B + B * (doc.length() / index.avgDocLength());
                score += termWeight * idf * ((tf * (K1 + 1.0)) / (tf + K1 * norm));
            }

            if (score > 0.0) {
                // Bônus leve para o nível selecionado, mantendo acesso a outros níveis (combate a bolhas)
                if (level != null && !level.isBlank() && doc.chunk().level().equalsIgnoreCase(level.trim())) {
                    score *= 1.15;
                }
                double rounded = Math.round(score * 100.0) / 100.0;
                scored.add(doc.chunk().withScore(rounded));
            }
        }

        if (scored.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunk> sorted = scored.stream()
                .sorted(Comparator.comparingDouble(KnowledgeChunk::score).reversed())
                .toList();

        double topScore = sorted.getFirst().score();
        double minCutoff = Math.max(MIN_ABSOLUTE_SCORE, topScore * RELATIVE_SCORE_THRESHOLD);

        return sorted.stream()
                .filter(chunk -> chunk.score() >= minCutoff)
                .limit(limit)
                .toList();
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

        // Expansão de expressões compostas PT ↔ EN (ex.: "falso amigo", "plano b", "na verdade")
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

    private record IndexedChunk(KnowledgeChunk chunk, Map<String, Integer> termFrequency, int length) {
    }

    private record IndexSnapshot(
            int version,
            List<IndexedChunk> indexedChunks,
            Map<String, Integer> documentFrequency,
            double avgDocLength
    ) {
    }
}
