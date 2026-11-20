package br.edu.usjt.dinga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatResponse;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.NewKnowledgeDocumentRequest;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.QuizQuestion;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.GeminiEmbeddingClient;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.service.DingaTutorService;
import br.edu.usjt.dinga.service.QuizService;
import br.edu.usjt.dinga.storage.JsonDataStore;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class RagBoostAndBenchmarkTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseLoader knowledgeLoader;

    @Autowired
    private Bm25RagRetriever ragRetriever;

    @Autowired
    private DingaTutorService tutorService;

    @Autowired
    private QuizService quizService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    void ragBenchmarkMeetsRecallAt3AndMrrTargetsAcrossAll15CuratedDocuments() {
        record BenchmarkCase(String query, String level, Set<String> expectedDocIds) {
        }

        List<BenchmarkCase> dataset = List.of(
                new BenchmarkCase("verbo to be apresentacao pessoal simple present", "A1", Set.of("01-a1-presente-simples-e-apresentacao")),
                new BenchmarkCase("simple past verbos irregulares did didn't yesterday", "A2", Set.of("02-a2-passado-e-rotina-estudos")),
                new BenchmarkCase("skimming scanning leitura documentacao tecnica verbos modais", "B1", Set.of("03-b1-leitura-documentacao-tecnica")),
                new BenchmarkCase("estrutura daily standup entrevista tecnica metodo STAR", "B2", Set.of("04-b2-comunicacao-profissional-daily-entrevista")),
                new BenchmarkCase("Microsoft Learn grounding RAG hallucination responsible AI", "B1", Set.of("05-ingles-tecnico-microsoft-learn-ia")),
                new BenchmarkCase("endpoint payload latencia fallback HTTP 429 API REST", "B1", Set.of("06-ingles-tecnico-cloud-apis-git")),
                new BenchmarkCase("qual a diferenca entre actually e currently falso cognato", "A2", Set.of("07-falsos-cognatos-e-erros-comuns-brasileiros")),
                new BenchmarkCase("metodo socratico autonomia combate a bolhas de aprendizado", "ALL", Set.of("08-metodo-socratico-e-combate-a-bolhas")),
                new BenchmarkCase("MTG-IA LGPD EU AI Act watermarking governanca etica", "ALL", Set.of("09-governanca-etica-mtg-ia-lgpd-ai-act")),
                new BenchmarkCase("ODS 4 ODS 10 baixa conectividade acessibilidade modo leve", "ALL", Set.of("10-ods-inclusao-acessibilidade-baixa-conectividade")),
                new BenchmarkCase("phrasal verbs set up spin up roll back figure out", "B1", Set.of("11-a2-b1-phrasal-verbs-tecnologia-e-trabalho")),
                new BenchmarkCase("como ler stack trace NullPointerException root cause debugging", "B1", Set.of("12-b1-erros-logs-debugging-e-stack-traces")),
                new BenchmarkCase("few-shot prompt engineering embeddings cosine similarity RRF busca hibrida", "B2", Set.of("13-b1-b2-prompt-engineering-e-rag-hibrido")),
                new BenchmarkCase("como escrever code review LGTM nitpick pull request GitHub", "B2", Set.of("14-b2-code-review-pull-requests-e-colaboracao")),
                new BenchmarkCase("pronuncia cache queue schema shadowing repeticao espacada Anki", "ALL", Set.of("15-a1-b2-pronuncia-listening-e-estrategias-estudo")),
                // Consultas com erros de digitação (Fuzzy Matching)
                new BenchmarkCase("o que e groudning e halucinacao em IA", "B1", Set.of("05-ingles-tecnico-microsoft-learn-ia", "13-b1-b2-prompt-engineering-e-rag-hibrido")),
                new BenchmarkCase("como evitar erro com actully e falsos cognat", "A2", Set.of("07-falsos-cognatos-e-erros-comuns-brasileiros")),
                new BenchmarkCase("atraso latensy no enpoint da api", "B1", Set.of("06-ingles-tecnico-cloud-apis-git")),
                // Consultas puramente em português (expansão bilíngue)
                new BenchmarkCase("como reverter um deploy com phrasal verbs", "B1", Set.of("11-a2-b1-phrasal-verbs-tecnologia-e-trabalho")),
                new BenchmarkCase("como investigar causa raiz e pilha de chamadas em logs", "B1", Set.of("12-b1-erros-logs-debugging-e-stack-traces")),
                new BenchmarkCase("revisao de codigo educada em pull request", "B2", Set.of("14-b2-code-review-pull-requests-e-colaboracao")),
                new BenchmarkCase("dicas de escuta e flashcards para memorizar vocabulario", "ALL", Set.of("15-a1-b2-pronuncia-listening-e-estrategias-estudo")),
                // Novas consultas coloquiais A1/A2 e trilhas reais do Microsoft Learn (cobertas por tags bilíngues)
                new BenchmarkCase("botoes em ingles interface ide falar idade estagio iniciante", "A1", Set.of("01-a1-presente-simples-e-apresentacao")),
                new BenchmarkCase("trilha ai-900 azure ai fundamentals responsible ai", "B1", Set.of("05-ingles-tecnico-microsoft-learn-ia")),
                new BenchmarkCase("az-900 azure fundamentals sla failover api rest", "B1", Set.of("06-ingles-tecnico-cloud-apis-git")),
                new BenchmarkCase("azure ai content safety ripd etico humano no circuito", "ALL", Set.of("09-governanca-etica-mtg-ia-lgpd-ai-act")),
                new BenchmarkCase("azure monitor application insights steps to reproduce", "B1", Set.of("12-b1-erros-logs-debugging-e-stack-traces")),
                new BenchmarkCase("azure ai search hybrid search fast path gemini 3.8 flash", "B2", Set.of("13-b1-b2-prompt-engineering-e-rag-hibrido"))
        );

        int hitsAt3 = 0;
        double reciprocalRankSum = 0.0;
        StringBuilder failures = new StringBuilder();

        for (BenchmarkCase tc : dataset) {
            List<KnowledgeChunk> results = ragRetriever.retrieve(tc.query(), tc.level(), 3);
            int rank = -1;
            for (int i = 0; i < results.size(); i++) {
                if (tc.expectedDocIds().contains(results.get(i).docId())) {
                    rank = i + 1;
                    break;
                }
            }
            if (rank > 0) {
                hitsAt3++;
                reciprocalRankSum += 1.0 / rank;
            } else {
                failures.append("\n  MISS query='").append(tc.query())
                        .append("' expected=").append(tc.expectedDocIds())
                        .append(" got=").append(results.stream().map(KnowledgeChunk::docId).toList());
            }
        }

        double recallAt3 = (double) hitsAt3 / dataset.size();
        double mrr = reciprocalRankSum / dataset.size();

        assertTrue(recallAt3 >= 0.95,
                String.format("Expected Recall@3 >= 0.95, got %.3f (%d/%d)%s", recallAt3, hitsAt3, dataset.size(), failures));
        assertTrue(mrr >= 0.85,
                String.format("Expected MRR >= 0.85, got %.3f%s", mrr, failures));
    }

    @Test
    void fuzzyMatchingHandlesTyposAndExactPhraseBonusWorksWithoutFalsePositives() {
        assertEquals(0, Bm25RagRetriever.damerauLevenshteinDistance("grounding", "grounding", 2));
        assertEquals(1, Bm25RagRetriever.damerauLevenshteinDistance("groudning", "grounding", 2));
        assertEquals(1, Bm25RagRetriever.damerauLevenshteinDistance("halucinacao", "alucinacao", 2));
        assertEquals(2, Bm25RagRetriever.damerauLevenshteinDistance("documntaco", "documentacao", 2));
        assertTrue(Bm25RagRetriever.damerauLevenshteinDistance("brigadeiro", "grounding", 2) > 2);

        List<KnowledgeChunk> typoGrounding = ragRetriever.retrieve("groudning e halucinacao", "B1", 3);
        assertFalse(typoGrounding.isEmpty());
        assertTrue(typoGrounding.stream().anyMatch(c -> c.docId().contains("microsoft-learn-ia")));

        List<KnowledgeChunk> typoActually = ragRetriever.retrieve("actully falso cognat", "A2", 3);
        assertFalse(typoActually.isEmpty());
        assertEquals("07-falsos-cognatos-e-erros-comuns-brasileiros", typoActually.getFirst().docId());

        List<KnowledgeChunk> typoEndpoint = ragRetriever.retrieve("enpoint e latensy", "B1", 3);
        assertFalse(typoEndpoint.isEmpty());
        assertEquals("06-ingles-tecnico-cloud-apis-git", typoEndpoint.getFirst().docId());

        // Exact phrase query should prioritize the exact matching document
        List<KnowledgeChunk> exactPhrase = ragRetriever.retrieve("stack trace root cause", "B1", 3);
        assertFalse(exactPhrase.isEmpty());
        assertEquals("12-b1-erros-logs-debugging-e-stack-traces", exactPhrase.getFirst().docId());

        // Zero false positives on unrelated queries
        assertTrue(ragRetriever.retrieve("receita culinaria brigadeiro chocolate panela", "A2", 3).isEmpty());
        assertTrue(ragRetriever.retrieve("futebol campeonato estadio bola", "B1", 3).isEmpty());
    }

    @Test
    void multiTurnContextualQueryExpansionEnrichesShortAndReferentialFollowUps() {
        ChatTurn prevFalseFriends = new ChatTurn(
                "Qual a diferença entre actually e currently?",
                "Actually significa na verdade; currently significa atualmente.",
                "Tente escrever uma frase usando actually.",
                "tutor-socratico"
        );

        String enriched = Bm25RagRetriever.enrichQueryWithHistory("Me dê mais exemplos disso na prática", List.of(prevFalseFriends));
        assertTrue(enriched.toLowerCase().contains("actually"), "Expected history enrichment with 'actually', got: " + enriched);
        assertTrue(enriched.toLowerCase().contains("currently"), "Expected history enrichment with 'currently', got: " + enriched);

        List<KnowledgeChunk> followUpChunks = ragRetriever.retrieve(
                "Me dê mais exemplos disso na prática",
                "A2",
                3,
                List.of(prevFalseFriends),
                false
        );
        assertFalse(followUpChunks.isEmpty());
        assertEquals("07-falsos-cognatos-e-erros-comuns-brasileiros", followUpChunks.getFirst().docId());

        // Detailed non-referential follow-up on a new topic must NOT be polluted by previous turn
        String newTopic = "Como escrever code review LGTM e nitpick em pull request no GitHub?";
        String notEnriched = Bm25RagRetriever.enrichQueryWithHistory(newTopic, List.of(prevFalseFriends));
        assertEquals(newTopic, notEnriched);

        // End-to-end chat with referential follow-up
        ChatResponse chatRes = tutorService.chat(new ChatRequest(
                "Como usar isso em uma frase?",
                "A2",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false,
                List.of(prevFalseFriends)
        ));
        assertFalse(chatRes.retrievedSources().isEmpty());
        assertEquals("07-falsos-cognatos-e-erros-comuns-brasileiros", chatRes.retrievedSources().getFirst().docId());
    }

    @Test
    void smartChunkingSubdividesLongSectionsWithParagraphBoundariesAndOverlap(@TempDir Path tempDir) {
        // Short section stays 1 chunk
        String shortText = "Parágrafo curto sobre APIs REST e endpoints JSON.";
        assertEquals(1, KnowledgeBaseLoader.subdivideWithOverlap(shortText, 900, 1050, 120).size());

        // Long multi-paragraph section (~2400 chars) subdivides cleanly with overlap
        String p1 = "Primeiro parágrafo detalhado sobre arquitetura de microserviços em Java 25 com Spring Boot. "
                + "Nesta etapa explicamos como configurar endpoints REST seguros com validação rigorosa de payload JSON e tratamento de exceções padronizado. "
                + "Também destacamos a importância de manter tempos de resposta previsíveis em redes móveis de baixa largura de banda.";
        String p2 = "Segundo parágrafo focado em observabilidade, métricas de latência TTFT e rastreamento distribuído. "
                + "Quando um serviço externo de IA generativa sofre oscilação ou atinge o limite de taxa HTTP 429, o circuito de contingência aciona um fallback automático. "
                + "Esse padrão garante continuidade pedagógica sem interromper a sessão de estudos do aluno.";
        String p3 = "Terceiro parágrafo explorando busca híbrida com BM25, embeddings vetoriais e Reciprocal Rank Fusion (RRF). "
                + "A fusão de rankings equilibra correspondência lexical exata de siglas técnicas com similaridade semântica por cosseno. "
                + "O cache persistente em disco evita recomputar vetores já indexados a cada reinicialização do servidor.";
        String p4 = "Quarto parágrafo abordando governança ética MTG-IA, privacidade LGPD e letramento digital crítico. "
                + "Todo conteúdo assistido por IA recebe marca d'água rastreável e exibe explicitamente as fontes consultadas na base curada. "
                + "A regra central permanece clara: a IA sugere, mas o estudante e o docente validam criticamente.";
        String p5 = "Quinto parágrafo com síntese prática sobre revisão de código, pull requests claros e repetição espaçada no Anki. "
                + "Escrever comentários respeitosos usando prefixos como Nit e LGTM fortalece a colaboração em times internacionais. "
                + "Combinar prática ativa diária com áudio TTS acelera a retenção de vocabulário técnico.";

        String longSection = String.join("\n\n", List.of(p1, p2, p3, p4, p5));
        assertTrue(longSection.length() > 1500);

        List<String> parts = KnowledgeBaseLoader.subdivideWithOverlap(
                longSection,
                KnowledgeBaseLoader.TARGET_CHUNK_CHARS,
                KnowledgeBaseLoader.MAX_CHUNK_CHARS,
                KnowledgeBaseLoader.OVERLAP_CHARS
        );
        assertTrue(parts.size() >= 2, "Expected long section to be split into >= 2 chunks");
        for (int i = 0; i < parts.size(); i++) {
            assertTrue(parts.get(i).length() <= KnowledgeBaseLoader.MAX_CHUNK_CHARS + 100,
                    "Chunk " + i + " exceeded max length: " + parts.get(i).length());
        }

        // Verify overlap continuity between chunk 0 and chunk 1 (tail words of chunk 0 appear at start of chunk 1)
        String chunk0Tail = parts.get(0).substring(Math.max(0, parts.get(0).length() - 60)).trim();
        String chunk1Head = parts.get(1).substring(0, Math.min(parts.get(1).length(), 180));
        assertTrue(chunk1Head.contains(chunk0Tail),
                "Expected overlap from end of chunk 0 ('" + chunk0Tail + "') at start of chunk 1 ('" + chunk1Head + "')");

        // Verify loader assigns "(parte i/N)" labels when adding a long custom document
        JsonDataStore store = new JsonDataStore(tempDir.toString(), objectMapper);
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(store);
        var summary = loader.addCustomDocument(new NewKnowledgeDocumentRequest(
                "Guia Longo de Arquitetura Híbrida",
                "B2",
                "Curadoria Docente",
                "Prof. USJT",
                "Arquitetura Completa",
                "## Arquitetura Completa\n" + longSection
        ));
        assertTrue(summary.chunkCount() >= 2);
        List<KnowledgeChunk> docChunks = loader.getChunks().stream()
                .filter(c -> c.docId().equals(summary.docId()))
                .toList();
        assertTrue(docChunks.getFirst().section().contains("(parte 1/"));
        assertTrue(docChunks.getLast().section().contains("(parte " + docChunks.size() + "/" + docChunks.size() + ")"));
    }

    @Test
    void sourceDiversityLimitsSameDocIdRedundancyWhenComplementarySourcesExist() {
        List<KnowledgeChunk> ragChunks = ragRetriever.retrieve("RAG grounding embeddings Microsoft Learn", "B1", 3);
        assertTrue(ragChunks.size() >= 2);
        long distinctDocs = ragChunks.stream().map(KnowledgeChunk::docId).distinct().count();
        assertTrue(distinctDocs >= 2, "Expected diverse docIds in top-3, got: " + ragChunks.stream().map(KnowledgeChunk::docId).toList());

        List<KnowledgeChunk> governanceChunks = ragRetriever.retrieve("LGPD MTG-IA ODS acessibilidade etica", "ALL", 3);
        assertTrue(governanceChunks.size() >= 2);
        long distinctGovDocs = governanceChunks.stream().map(KnowledgeChunk::docId).distinct().count();
        assertTrue(distinctGovDocs >= 2, "Expected diverse governance docIds, got: " + governanceChunks.stream().map(KnowledgeChunk::docId).toList());
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridRagCombinesBm25AndGeminiEmbeddingsWithRrfPersistsCacheAndRespectsLowDataMode(@TempDir Path tempDir) throws Exception {
        JsonDataStore store = new JsonDataStore(tempDir.toString(), objectMapper);
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(store);
        List<KnowledgeChunk> chunks = loader.getChunks();

        // Build deterministic mock vectors: target doc 13 gets [1, 0, 0], all other chunks get [0, 1, 0]
        HttpClient mockHttp = mock(HttpClient.class);
        int[] offset = {0};
        int[] queryEmbedCalls = {0};
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String uri = req.uri().toString();
            HttpResponse<String> resp = mock(HttpResponse.class);
            when(resp.statusCode()).thenReturn(200);

            if (uri.contains(":batchEmbedContents")) {
                int start = offset[0];
                int end = Math.min(chunks.size(), start + 24);
                offset[0] = end;
                StringBuilder sb = new StringBuilder("{\"embeddings\":[");
                for (int i = start; i < end; i++) {
                    if (i > start) {
                        sb.append(',');
                    }
                    boolean isTarget = chunks.get(i).docId().equals("13-b1-b2-prompt-engineering-e-rag-hibrido")
                            && chunks.get(i).section().contains("Embeddings");
                    sb.append(isTarget ? "{\"values\":[1.0,0.0,0.0]}" : "{\"values\":[0.0,1.0,0.0]}");
                }
                sb.append("]}");
                when(resp.body()).thenReturn(sb.toString());
            } else if (uri.contains(":embedContent")) {
                queryEmbedCalls[0]++;
                // Query vector aligns 100% with target chunk ([1.0, 0.0, 0.0])
                when(resp.body()).thenReturn("{\"embedding\":{\"values\":[1.0,0.0,0.0]}}");
            }
            return resp;
        });

        LlmProperties geminiProps = new LlmProperties("gemini", "fake-gemini-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        GeminiEmbeddingClient embeddingClient = new GeminiEmbeddingClient(
                geminiProps, objectMapper, store, "gemini-embedding-001", mockHttp
        );

        int indexed = embeddingClient.indexChunksSync(chunks);
        assertEquals(chunks.size(), indexed);
        assertTrue(Files.exists(tempDir.resolve(GeminiEmbeddingClient.CACHE_FILE)), "embeddings-cache.json must be persisted");

        // Reloading from disk must restore all cached embeddings with ZERO HTTP calls
        HttpClient zeroCallHttp = mock(HttpClient.class);
        GeminiEmbeddingClient reloadedClient = new GeminiEmbeddingClient(
                geminiProps, objectMapper, store, "gemini-embedding-001", zeroCallHttp
        );
        assertEquals(chunks.size(), reloadedClient.cachedChunkCount());
        verify(zeroCallHttp, times(0)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Pure semantic query (zero lexical overlap with any curated doc) must be retrieved via Hybrid RRF!
        Bm25RagRetriever hybridRetriever = new Bm25RagRetriever(loader, embeddingClient);
        assertTrue(hybridRetriever.ragModeLabel().contains("Híbrido BM25 + Embeddings RRF"));

        List<KnowledgeChunk> semanticHits = hybridRetriever.retrieve("zxqkvp ytrwmn plmkjn", "B2", 3, List.of(), false);
        assertFalse(semanticHits.isEmpty(), "Hybrid RRF should retrieve semantic match even with zero BM25 overlap");
        assertEquals("13-b1-b2-prompt-engineering-e-rag-hibrido", semanticHits.getFirst().docId());
        assertEquals(1, queryEmbedCalls[0]);

        // lowDataMode=true must skip remote query embeddings completely (zero HTTP calls, instant local BM25 only)
        List<KnowledgeChunk> lowDataHits = hybridRetriever.retrieve("zxqkvp ytrwmn plmkjn", "B2", 3, List.of(), true);
        assertTrue(lowDataHits.isEmpty(), "lowDataMode must skip remote query embeddings and use local BM25 only");
        assertEquals(1, queryEmbedCalls[0], "lowDataMode must not issue any remote embedding call");

        // HTTP 500 error on query embedding must gracefully fall back to BM25 local without throwing
        HttpResponse<String> errResp = mock(HttpResponse.class);
        when(errResp.statusCode()).thenReturn(500);
        when(errResp.body()).thenReturn("{\"error\":\"internal\"}");
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(errResp);

        List<KnowledgeChunk> fallbackHits = hybridRetriever.retrieve("actually falso cognato", "A2", 3, List.of(), false);
        assertFalse(fallbackHits.isEmpty());
        assertEquals("07-falsos-cognatos-e-erros-comuns-brasileiros", fallbackHits.getFirst().docId());
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridLlmClientModelCooldownSkipsFailingModelsAndEndpointsExposeRagModeAndSseNoBuffering() throws Exception {
        LlmProperties geminiProps = new LlmProperties(
                "gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", ""
        );
        HttpClient mockHttp = mock(HttpClient.class);

        HttpResponse<String> notFoundResp = mock(HttpResponse.class);
        when(notFoundResp.statusCode()).thenReturn(404);
        when(notFoundResp.body()).thenReturn("{\"error\":\"not found\"}");

        HttpResponse<String> okResp = mock(HttpResponse.class);
        when(okResp.statusCode()).thenReturn(200);
        when(okResp.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Fast answer.\"}]}}]}");

        // Call 1: gemini-3.8-flash -> 404 (marked on cooldown), fallback gemini-3.8-flash-lite -> 200
        // Call 2 (different query): must skip gemini-3.8-flash and hit gemini-3.8-flash-lite directly (1 HTTP call!)
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(notFoundResp)
                .thenReturn(okResp)
                .thenReturn(okResp);

        HybridLlmClient client = new HybridLlmClient(geminiProps, objectMapper, mockHttp);
        var first = client.generate("sys", "first question");
        assertTrue(first.isPresent());
        assertEquals("Gemini (gemini-3.8-flash-lite)", first.get().providerLabel());
        assertTrue(client.isModelOnCooldown("gemini-3.8-flash"));

        var second = client.generate("sys", "second different question");
        assertTrue(second.isPresent());
        assertEquals("Gemini (gemini-3.8-flash-lite)", second.get().providerLabel());
        // Total HTTP calls: 2 on first request + only 1 on second request = 3 (not 4!)
        verify(mockHttp, times(3)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        // Verify /api/health and /api/meta expose ragMode and cachedEmbeddingsCount
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentsCount").value(15))
                .andExpect(jsonPath("$.ragMode").isNotEmpty())
                .andExpect(jsonPath("$.cachedEmbeddingsCount").isNumber());

        mockMvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ragMode").isNotEmpty())
                .andExpect(jsonPath("$.cachedEmbeddingsCount").isNumber());

        // Verify /api/chat/stream sets X-Accel-Buffering: no for immediate TTFT flushing
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"O que é stack trace?\",\"level\":\"B1\"}"))
                .andExpect(request().asyncStarted())
                .andExpect(header().string("X-Accel-Buffering", "no"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void regressionFixesCoverModelFallbackIsolationFastPathWordBoundariesAndSynonyms(@TempDir Path tempDir) throws Exception {
        // 1. Word-boundary check in multi-turn query expansion:
        // "delete" contains "dele", "delay" contains "dela", "e commit" contains "e com", "historico" contains "isto"
        ChatTurn prevTurn = new ChatTurn(
                "Qual a diferença entre actually e currently?",
                "Actually significa na verdade; currently significa atualmente.",
                "Escreva uma frase com actually.",
                "tutor-socratico"
        );
        for (String independentQuery : List.of(
                "Como usar o método HTTP DELETE em uma API REST?",
                "Como reduzir o delay em chamadas de API REST?",
                "O que é commit e push no GitHub?",
                "Como consultar o histórico de commits no Git?"
        )) {
            assertEquals(
                    independentQuery,
                    Bm25RagRetriever.enrichQueryWithHistory(independentQuery, List.of(prevTurn)),
                    "Independent query must NOT be polluted by substring match on referential cue: " + independentQuery
            );
        }

        // 2. Singular "embedding" (stems to "embed") expands bilingual synonyms, and silent-h dist=2 typo works
        var embedTerms = Bm25RagRetriever.expandQueryTerms("O que é embedding?");
        assertTrue(embedTerms.containsKey("embed"));
        assertTrue(embedTerms.containsKey("vetor"), "Singular 'embedding' should expand synonym 'vetor'");
        assertTrue(embedTerms.containsKey("cosine"), "Singular 'embedding' should expand synonym 'cosine'");

        List<KnowledgeChunk> silentHDist2 = ragRetriever.retrieve("o que e halucinasao em IA", "B1", 3);
        assertFalse(silentHDist2.isEmpty());
        assertTrue(silentHDist2.stream().anyMatch(c -> c.docId().contains("microsoft-learn-ia")));

        // 3. Defensive bounds on subdivideWithOverlap
        assertFalse(KnowledgeBaseLoader.subdivideWithOverlap("Texto curto válido.", 0, 0, -10).isEmpty());

        // 4. Embedding model fallback isolation (gemini-embedding-001 -> 404 -> text-embedding-004) + BM25 fast-path skips HTTP
        JsonDataStore store = new JsonDataStore(tempDir.toString(), objectMapper);
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(store);
        List<KnowledgeChunk> chunks = loader.getChunks();

        HttpClient mockHttp = mock(HttpClient.class);
        int[] offset = {0};
        int[] queryCalls = {0};
        String[] lastQueryModelUri = {""};
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0);
            String uri = req.uri().toString();
            HttpResponse<String> resp = mock(HttpResponse.class);

            if (uri.contains("gemini-embedding-001")) {
                when(resp.statusCode()).thenReturn(404);
                when(resp.body()).thenReturn("{\"error\":\"model not found\"}");
                return resp;
            }
            when(resp.statusCode()).thenReturn(200);
            if (uri.contains(":batchEmbedContents")) {
                int start = offset[0];
                int end = Math.min(chunks.size(), start + 24);
                offset[0] = end;
                StringBuilder sb = new StringBuilder("{\"embeddings\":[");
                for (int i = start; i < end; i++) {
                    if (i > start) {
                        sb.append(',');
                    }
                    boolean isTarget = chunks.get(i).docId().equals("14-b2-code-review-pull-requests-e-colaboracao");
                    sb.append(isTarget ? "{\"values\":[0.0,0.0,1.0]}" : "{\"values\":[0.0,1.0,0.0]}");
                }
                sb.append("]}");
                when(resp.body()).thenReturn(sb.toString());
            } else if (uri.contains(":embedContent")) {
                queryCalls[0]++;
                lastQueryModelUri[0] = uri;
                when(resp.body()).thenReturn("{\"embedding\":{\"values\":[0.0,0.0,1.0]}}");
            }
            return resp;
        });

        LlmProperties geminiProps = new LlmProperties("gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        GeminiEmbeddingClient embClient = new GeminiEmbeddingClient(geminiProps, objectMapper, store, "gemini-embedding-001", mockHttp);
        int indexed = embClient.indexChunksSync(chunks);
        assertEquals(chunks.size(), indexed);
        assertEquals("text-embedding-004", embClient.activeCachedModel());
        assertEquals(0, embClient.cachedChunkCountForModel("gemini-embedding-001"));
        assertEquals(chunks.size(), embClient.cachedChunkCountForModel("text-embedding-004"));

        Bm25RagRetriever hybrid = new Bm25RagRetriever(loader, embClient);
        assertTrue(hybrid.ragModeLabel().contains("text-embedding-004"));

        // Obvious high-confidence BM25 query must take fast path with ZERO remote embedContent calls
        List<KnowledgeChunk> fastPathHits = hybrid.retrieve("O que é stack trace NullPointerException?", "B1", 3, List.of(), false);
        assertFalse(fastPathHits.isEmpty());
        assertEquals("12-b1-erros-logs-debugging-e-stack-traces", fastPathHits.getFirst().docId());
        assertEquals(0, queryCalls[0], "High-confidence BM25 hit must skip remote embedding call for low latency");

        // Zero-BM25 semantic query must invoke embedContent on the SAME cached model (text-embedding-004)
        List<KnowledgeChunk> semanticHits = hybrid.retrieve("zxqkvp ytrwmn plmkjn", "B2", 3, List.of(), false);
        assertFalse(semanticHits.isEmpty());
        assertEquals("14-b2-code-review-pull-requests-e-colaboracao", semanticHits.getFirst().docId());
        assertEquals(1, queryCalls[0]);
        assertTrue(lastQueryModelUri[0].contains("text-embedding-004"),
                "Query embedding must use the same model as cached chunks, got: " + lastQueryModelUri[0]);
    }

    @Test
    void bilingualFrontmatterTagsEnrichedContentAndTagWeightedBm25WorkAcrossAll15Docs(@TempDir Path tempDir) {
        // 1. All 15 curated documents must have non-empty bilingual tags and every chunk inherits them
        var docs = knowledgeLoader.getDocumentsSummary();
        assertEquals(15, docs.size());
        for (var doc : docs) {
            assertTrue(doc.tags().size() >= 8, "Expected >= 8 bilingual tags on " + doc.docId() + ", got " + doc.tags().size());
        }
        for (KnowledgeChunk chunk : knowledgeLoader.getChunks()) {
            assertFalse(chunk.tags().isEmpty(), "Expected chunk " + chunk.id() + " to inherit document tags");
        }

        // 2. Every curated document must include standardized practical contrasts (❌ Evite / ✅ Prefira)
        //    and feed clean English pronunciation examples via chat
        for (var doc : docs) {
            String docBody = knowledgeLoader.getChunks().stream()
                    .filter(c -> c.docId().equals(doc.docId()))
                    .map(KnowledgeChunk::content)
                    .reduce("", (a, b) -> a + "\n" + b);
            assertTrue(docBody.contains("❌ Evite"), "Missing '❌ Evite' contrast in " + doc.docId());
            assertTrue(docBody.contains("✅ Prefira"), "Missing '✅ Prefira' contrast in " + doc.docId());
        }

        // 3. Tag parsing edge cases (null, blank, brackets, semicolons, quotes, deduplication)
        assertTrue(KnowledgeBaseLoader.parseTags(null).isEmpty());
        assertTrue(KnowledgeBaseLoader.parseTags("   ").isEmpty());
        assertEquals(
                List.of("ai-900", "grounding", "ancoragem"),
                KnowledgeBaseLoader.parseTags("[\"ai-900\", 'grounding'; ancoragem, grounding]")
        );

        // 4. Uploaded markdown with tags: frontmatter persists across reloads and boosts BM25 ranking
        JsonDataStore store = new JsonDataStore(tempDir.toString(), objectMapper);
        KnowledgeBaseLoader loader = new KnowledgeBaseLoader(store);
        String uploadedMd = """
                title: Guia Rápido de Observabilidade
                level: B1
                category: Engenharia
                source: Docente USJT
                tags: telemetria distribuida, opentelemetry, rastreamento ponta a ponta
                ---

                ## Métricas e Traces
                Use traces para acompanhar requisições entre serviços.
                - *"Distributed tracing connects logs across microservices."*
                """;
        var uploaded = loader.addUploadedDocument("observabilidade.md", uploadedMd, null, null, null, null);
        assertEquals(List.of("telemetria distribuida", "opentelemetry", "rastreamento ponta a ponta"), uploaded.tags());

        KnowledgeBaseLoader reloaded = new KnowledgeBaseLoader(store);
        Bm25RagRetriever customRetriever = new Bm25RagRetriever(reloaded);
        List<KnowledgeChunk> tagHits = customRetriever.retrieve("telemetria distribuida opentelemetry", "B1", 3);
        assertFalse(tagHits.isEmpty());
        assertEquals(uploaded.docId(), tagHits.getFirst().docId());
        assertEquals(uploaded.tags(), tagHits.getFirst().tags());

        // 5. GeminiEmbeddingClient chunkKey incorporates tags (automatic cache invalidation when tags change)
        LlmProperties geminiProps = new LlmProperties("gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        GeminiEmbeddingClient embClient = new GeminiEmbeddingClient(geminiProps, objectMapper, store, "gemini-embedding-001", mock(HttpClient.class));
        KnowledgeChunk withoutTags = new KnowledgeChunk("id#1", "doc", "Title", "B1", "Cat", "Src", "Sec", "Body", 0.0, List.of());
        KnowledgeChunk withTags = new KnowledgeChunk("id#1", "doc", "Title", "B1", "Cat", "Src", "Sec", "Body", 0.0, List.of("ai-900", "grounding"));
        assertFalse(embClient.chunkKey("gemini-embedding-001", withoutTags)
                .equals(embClient.chunkKey("gemini-embedding-001", withTags)));

        // 6. Beginner A1 interface/pronunciation chat extracts natural English examples for TTS chips
        ChatResponse a1Chat = tutorService.chat(new ChatRequest(
                "Quais são os botoes em ingles da interface da IDE para iniciantes?",
                "A1",
                "Inglês para Tecnologia",
                "auto",
                false,
                List.of()
        ));
        assertFalse(a1Chat.retrievedSources().isEmpty());
        assertEquals("01-a1-presente-simples-e-apresentacao", a1Chat.retrievedSources().getFirst().docId());
        assertTrue(a1Chat.englishExamples().size() >= 2, "Expected >= 2 English TTS chips, got: " + a1Chat.englishExamples());
    }

    @Test
    void ttsChipsAndQuizGenerationExcludeAvoidAntiPatternsAndHeaderOnlyBulletsAcrossAll15Docs() throws Exception {
        Set<String> forbiddenAvoidExamples = Set.of(
                "I have 21 years.",
                "You are a developer?",
                "My name it is Lucas.",
                "She study Java every night.",
                "Yesterday I work on the login page.",
                "I didn't finished the exercise.",
                "You must to set the variable.",
                "I pretend to study cloud computing.",
                "I have 20 years and I want to make a question.",
                "Thanks for the informations and feedbacks!",
                "I am agree with you, it depends of the network.",
                "Enter in the system and exit the account.",
                "Why did you write this ugly code? Fix it."
        );

        var docs = knowledgeLoader.getDocumentsSummary();
        assertEquals(15, docs.size());

        // 1. Verify DingaTutorService.extractEnglishExamples across every curated document
        for (var doc : docs) {
            List<KnowledgeChunk> docChunks = knowledgeLoader.getChunks().stream()
                    .filter(c -> c.docId().equals(doc.docId()))
                    .toList();
            List<String> extracted = DingaTutorService.extractEnglishExamples("", docChunks);
            assertTrue(extracted.size() >= 2, "Expected >= 2 valid English TTS examples for " + doc.docId() + ", got: " + extracted);
            for (String example : extracted) {
                assertFalse(forbiddenAvoidExamples.contains(example),
                        "TTS chip must NEVER extract '❌ Evite' anti-pattern '" + example + "' from " + doc.docId());
                assertFalse(example.contains("\n"),
                        "TTS chip must not span multiple lines in " + doc.docId() + ": " + example);
            }
        }

        // 2. Verify QuizService deterministic generation across ALL 15 enriched docs
        try {
            for (var doc : docs) {
                var batch = quizService.generateQuestions(new QuizGenerationRequest("", doc.level(), doc.docId(), 3));
                assertEquals(3, batch.generated().size());
                for (QuizQuestion q : batch.generated()) {
                    String correctOpt = q.options().get(q.correctIndex());
                    assertFalse(correctOpt.endsWith(":") || correctOpt.endsWith("："),
                            "Quiz correct option must NOT be a header-only bullet in " + doc.docId() + ": " + correctOpt);
                    assertFalse(forbiddenAvoidExamples.contains(q.englishExample()),
                            "Quiz englishExample must NEVER be a '❌ Evite' anti-pattern in " + doc.docId() + ": " + q.englishExample());
                }
            }
        } finally {
            quizService.resetGeneratedQuestions();
        }

        // 3. Verify POST /api/knowledge accepts optional tags and indexes them in BM25
        try {
            mockMvc.perform(post("/api/knowledge")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "title": "Guia Docente de WebSockets",
                                      "level": "B2",
                                      "category": "Curadoria Docente",
                                      "sourceRef": "Prof. USJT",
                                      "section": "Comunicação Bidirecional",
                                      "content": "WebSockets mantêm conexão persistente full-duplex entre cliente e servidor. \\"WebSockets enable real-time bidirectional updates.\\"",
                                      "tags": ["websockets", "full-duplex", "tempo real"]
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.document.tags[0]").value("websockets"))
                    .andExpect(jsonPath("$.document.tags[1]").value("full-duplex"));

            List<KnowledgeChunk> wsHits = ragRetriever.retrieve("full-duplex tempo real", "B2", 3);
            assertFalse(wsHits.isEmpty());
            assertTrue(wsHits.getFirst().title().contains("WebSockets"));
            assertTrue(wsHits.getFirst().tags().contains("full-duplex"));
        } finally {
            knowledgeLoader.resetCustomDocuments();
            ragRetriever.refreshIndex();
        }
    }
}
