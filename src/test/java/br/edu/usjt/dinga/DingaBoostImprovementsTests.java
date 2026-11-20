package br.edu.usjt.dinga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.config.RateLimitInterceptor;
import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.SimulationRequest;
import br.edu.usjt.dinga.model.SimulationResponse;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.service.QuizService;
import br.edu.usjt.dinga.service.RateLimiterService;
import br.edu.usjt.dinga.service.SessionMetricsService;
import br.edu.usjt.dinga.service.SimulatorService;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class DingaBoostImprovementsTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseLoader knowledgeLoader;

    @Autowired
    private Bm25RagRetriever ragRetriever;

    @Autowired
    private QuizService quizService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @Autowired
    private SessionMetricsService metricsService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        rateLimiterService.reset();
    }

    @Test
    void simulatorRunsMultiTurnDailyStandupWithLgpdMaskingAndFinalReport() throws Exception {
        // Start Daily (3 steps)
        MvcResult startRes = mockMvc.perform(post("/api/simulator/start")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"daily\",\"level\":\"B1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("daily"))
                .andExpect(jsonPath("$.currentStep").value(1))
                .andExpect(jsonPath("$.totalSteps").value(3))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.stepLabel").value("Yesterday"))
                .andReturn();
        assertNotNull(startRes.getResponse().getContentAsString());

        // Step 1 with grammar issue + CPF PII
        MvcResult step1Res = mockMvc.perform(post("/api/simulator/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "daily",
                                  "level": "B1",
                                  "stepIndex": 1,
                                  "answer": "Yesterday I work on the REST API endpoint and my CPF is 123.456.789-00.",
                                  "history": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value(2))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.piiMasked").value(true))
                .andExpect(jsonPath("$.turnEvaluation.stepLabel").value("Yesterday"))
                .andReturn();
        JsonNode step1Json = objectMapper.readTree(step1Res.getResponse().getContentAsString());
        assertTrue(step1Json.path("turnEvaluation").path("naturalVersion").asString("").contains("worked"));

        // Step 2
        mockMvc.perform(post("/api/simulator/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "daily",
                                  "level": "B1",
                                  "stepIndex": 2,
                                  "answer": "Today I will review pull requests and deploy the feature branch.",
                                  "history": []
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStep").value(3))
                .andExpect(jsonPath("$.completed").value(false))
                .andExpect(jsonPath("$.stepLabel").value("Blockers"));

        // Step 3 -> Final mini-boletim
        mockMvc.perform(post("/api/simulator/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mode": "daily",
                                  "level": "B1",
                                  "stepIndex": 3,
                                  "answer": "I do not have any blockers right now, just waiting for code review.",
                                  "history": [
                                    {"stepIndex":1,"stepLabel":"Yesterday","question":"Q1","answer":"A1","clarityScore":85,"grammarScore":80,"technicalVocabScore":85,"feedback":"ok","naturalVersion":"nat"},
                                    {"stepIndex":2,"stepLabel":"Today","question":"Q2","answer":"A2","clarityScore":90,"grammarScore":95,"technicalVocabScore":90,"feedback":"ok","naturalVersion":"nat"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true))
                .andExpect(jsonPath("$.finalReport.overallScore").isNumber())
                .andExpect(jsonPath("$.finalReport.clarityScore").isNumber())
                .andExpect(jsonPath("$.finalReport.grammarScore").isNumber())
                .andExpect(jsonPath("$.finalReport.technicalVocabScore").isNumber())
                .andExpect(jsonPath("$.finalReport.strengths").isArray());
    }

    @Test
    void simulatorUsesGemini38FlashWhenAvailableAndFallsBackLocallyOnInvalidJson() {
        HybridLlmClient mockLlm = mock(HybridLlmClient.class);
        when(mockLlm.generate(anyString(), anyString())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                """
                {"clarityScore":92,"grammarScore":88,"technicalVocabScore":94,"feedback":"Excelente clareza técnica com Gemini 3.8 Flash.","naturalVersion":"Yesterday I refactored the API endpoint and added unit tests.","strength":"Vocabulário técnico preciso.","improvement":"Continue usando verbos de ação."}
                """,
                "Gemini (gemini-3.8-flash)"
        )));

        SimulatorService sim = new SimulatorService(mockLlm, objectMapper);
        SimulationResponse resp = sim.submitAnswer(new SimulationRequest(
                "interview",
                "B2",
                1,
                "I am a Java backend developer focused on APIs and Spring Boot.",
                List.of()
        ));
        assertEquals(4, resp.totalSteps());
        assertEquals(92, resp.turnEvaluation().clarityScore());
        assertTrue(resp.executionMode().contains("gemini-3.8-flash"));

        // Invalid JSON -> graceful local fallback
        when(mockLlm.generate(anyString(), anyString()))
                .thenReturn(Optional.of(new HybridLlmClient.LlmResult("not-valid-json", "Gemini (gemini-3.8-flash)")));
        SimulationResponse fallbackResp = sim.submitAnswer(new SimulationRequest(
                "daily",
                "B1",
                1,
                "Yesterday I fixed a latency bug in the API.",
                List.of()
        ));
        assertNotNull(fallbackResp.turnEvaluation());
        assertTrue(fallbackResp.executionMode().contains("Avaliador Local"));
    }

    @Test
    void quizServiceUsesGemini38FlashWhenAvailableAndFallsBackWhenMalformedOrDuplicate() {
        HybridLlmClient mockLlm = mock(HybridLlmClient.class);
        when(mockLlm.generate(anyString(), anyString())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                """
                {
                  "question": "Qual o papel do Grounding em aplicações RAG no Microsoft Learn?",
                  "codeOrDocSnippet": "RAG grounds LLM answers in curated documents.",
                  "options": [
                    "Ancorar as respostas do modelo em fontes verificáveis para reduzir alucinações.",
                    "Aumentar o tamanho dos pesos do modelo em tempo de execução.",
                    "Substituir a revisão humana por respostas aleatórias.",
                    "Desativar o uso de documentação técnica em inglês."
                  ],
                  "correctIndex": 0,
                  "explanation": "Grounding conecta a resposta do modelo a documentos reais recuperados via RAG.",
                  "englishExample": "Grounding anchors AI responses in trusted documentation."
                }
                """,
                "Gemini (gemini-3.8-flash)"
        )));

        QuizService customQuiz = new QuizService(knowledgeLoader, ragRetriever, mockLlm, objectMapper);
        var llmBatch = customQuiz.generateQuestions(new QuizGenerationRequest("Grounding", "B1", "05-ingles-tecnico-microsoft-learn-ia", 1));
        assertEquals(1, llmBatch.generated().size());
        assertTrue(llmBatch.generationMode().contains("Gemini (gemini-3.8-flash)"));
        assertEquals("Qual o papel do Grounding em aplicações RAG no Microsoft Learn?", llmBatch.generated().getFirst().question());

        // Duplicate options -> rejected and falls back to local deterministic RAG generator
        when(mockLlm.generate(anyString(), anyString())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                """
                {"question":"Q","codeOrDocSnippet":"S","options":["Same","Same","C","D"],"correctIndex":0,"explanation":"E","englishExample":"Ex"}
                """,
                "Gemini (gemini-3.8-flash)"
        )));
        var fallbackBatch = customQuiz.generateQuestions(new QuizGenerationRequest("Grounding", "B1", "05-ingles-tecnico-microsoft-learn-ia", 1));
        assertEquals(1, fallbackBatch.generated().size());
        assertTrue(fallbackBatch.generationMode().contains("RAG Local"));
    }

    @Test
    void teacherFileUploadIndexesMarkdownAndTxtImmediatelyAndRejectsInvalidFiles() throws Exception {
        try {
            String mdContent = """
                    title: Kubernetes & Containers Vocab
                    level: B2
                    category: Curadoria Docente Upload
                    source: Prof. USJT
                    ---
                    ## Pods e Deployments
                    - **Pod:** menor unidade executável no Kubernetes ("Each pod runs one or more containers.").
                    ## Observability
                    - **Health Probe:** verifica se o serviço está saudável ("Kubernetes restarts the container when the liveness probe fails.").
                    """;
            MockMultipartFile mdFile = new MockMultipartFile(
                    "file",
                    "k8s-vocab.md",
                    "text/markdown",
                    mdContent.getBytes(StandardCharsets.UTF_8)
            );

            mockMvc.perform(multipart("/api/knowledge/upload").file(mdFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.document.title").value("Kubernetes & Containers Vocab"))
                    .andExpect(jsonPath("$.document.chunkCount").value(2))
                    .andExpect(jsonPath("$.totalDocuments").value(11));

            var hits = ragRetriever.retrieve("liveness probe container kubernetes", "B2", 3);
            assertFalse(hits.isEmpty());
            assertTrue(hits.stream().anyMatch(c -> c.title().contains("Kubernetes")));

            // Invalid extension -> 400 standardized JSON error
            MockMultipartFile badFile = new MockMultipartFile("file", "malware.exe", "application/octet-stream", "abc".getBytes());
            mockMvc.perform(multipart("/api/knowledge/upload").file(badFile))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.status").value(400))
                    .andExpect(jsonPath("$.timestamp").isNotEmpty())
                    .andExpect(jsonPath("$.message").isNotEmpty());

            // Standard Markdown WITHOUT frontmatter that uses horizontal rule '---' must preserve intro + heading
            String plainMdWithRule = """
                    # Docker Basics Guide

                    Containers package code and dependencies consistently.

                    ---

                    ## Dockerfile Instructions
                    - **ENTRYPOINT:** defines the default container command ("The Dockerfile defines the ENTRYPOINT.").
                    """;
            MockMultipartFile plainMdFile = new MockMultipartFile(
                    "file",
                    "docker-guide.md",
                    "text/markdown",
                    plainMdWithRule.getBytes(StandardCharsets.UTF_8)
            );
            mockMvc.perform(multipart("/api/knowledge/upload").file(plainMdFile))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.document.title").value("Docker Basics Guide"))
                    .andExpect(jsonPath("$.document.chunkCount").value(2));

            var dockerHits = ragRetriever.retrieve("containers package code dependencies docker", "B1", 3);
            assertTrue(dockerHits.stream().anyMatch(c -> c.content().contains("Containers package code")));
        } finally {
            knowledgeLoader.resetCustomDocuments();
            ragRetriever.refreshIndex();
        }
    }

    @Test
    void globalExceptionHandlerAndInputValidationReturnStandardizedJsonErrors() throws Exception {
        // 400 on blank chat message
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.path").value("/api/chat"));

        // 400 on oversized chat message (>2000 chars)
        String hugeMessage = "a".repeat(2100);
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"" + hugeMessage + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 400 on malformed JSON
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{broken-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Corpo JSON inválido ou malformado."));

        // 404 on unknown custom doc delete
        mockMvc.perform(delete("/api/knowledge/99-docente-nao-existe"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.timestamp").isNotEmpty());

        // 404 on unknown API route (must not become 500)
        mockMvc.perform(get("/api/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/does-not-exist"));

        // 405 on unsupported HTTP method (must not become 500)
        mockMvc.perform(post("/api/health"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.error").value("Method Not Allowed"));

        // 415 on unsupported media type (must not become 500)
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("plain text"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415));

        // 400 on missing multipart 'file' part (must not become 500)
        mockMvc.perform(multipart("/api/knowledge/upload"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));

        // 400 on oversized simulator history (>10 turns)
        StringBuilder histJson = new StringBuilder("[");
        for (int i = 0; i < 11; i++) {
            if (i > 0) histJson.append(',');
            histJson.append("{\"stepIndex\":1,\"stepLabel\":\"Yesterday\",\"question\":\"Q\",\"answer\":\"A\",\"clarityScore\":80,\"grammarScore\":80,\"technicalVocabScore\":80,\"feedback\":\"ok\",\"naturalVersion\":\"ok\"}");
        }
        histJson.append(']');
        mockMvc.perform(post("/api/simulator/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\":\"daily\",\"level\":\"B1\",\"stepIndex\":1,\"answer\":\"Yesterday I fixed a bug.\",\"history\":" + histJson + "}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rateLimiterProtectsQuizGenerateEndpointWithHttp429AndRetryAfter() throws Exception {
        try {
            rateLimiterService.reset();
            // Limit for quiz-generate is 15 req/min; 16th request must return 429
            for (int i = 0; i < 15; i++) {
                mockMvc.perform(post("/api/quiz/generate")
                                .header("X-Forwarded-For", "198.51.100.77")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"topic\":\"RAG\",\"level\":\"B1\",\"count\":1}"))
                        .andExpect(status().isOk());
            }

            mockMvc.perform(post("/api/quiz/generate")
                            .header("X-Forwarded-For", "198.51.100.77")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"topic\":\"RAG\",\"level\":\"B1\",\"count\":1}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.status").value(429))
                    .andExpect(jsonPath("$.error").value("Too Many Requests"));
        } finally {
            rateLimiterService.reset();
            quizService.resetGeneratedQuestions();
        }
    }

    @Test
    void sessionMetricsEndpointTracksChatQuizGuardrailsAndLowDataSavings() throws Exception {
        metricsService.reset();

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Meu CPF é 123.456.789-00, explique o que é RAG?\",\"level\":\"B1\",\"lowDataMode\":true}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/quiz/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"questionId\":\"quiz-rag-grounding\",\"selectedIndex\":0}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalChatTurns").value(1))
                .andExpect(jsonPath("$.lowDataChatTurns").value(1))
                .andExpect(jsonPath("$.lgpdMaskCount").value(1))
                .andExpect(jsonPath("$.totalQuizAnswered").value(1))
                .andExpect(jsonPath("$.totalQuizCorrect").value(1))
                .andExpect(jsonPath("$.quizAccuracyPercent").value(100))
                .andExpect(jsonPath("$.estimatedSavedKb").isNumber());

        // Verify skillUsage ordering is preserved (descending by count)
        metricsService.recordChatTurn("skill-low", false, 1.0, false, false);
        metricsService.recordChatTurn("skill-top", false, 1.0, false, false);
        metricsService.recordChatTurn("skill-top", false, 1.0, false, false);
        metricsService.recordChatTurn("skill-top", false, 1.0, false, false);
        metricsService.recordChatTurn("skill-mid", false, 1.0, false, false);
        metricsService.recordChatTurn("skill-mid", false, 1.0, false, false);

        var orderedKeys = List.copyOf(metricsService.snapshot().skillUsage().keySet());
        assertEquals("skill-top", orderedKeys.get(0));
        assertEquals("skill-mid", orderedKeys.get(1));
    }

    @Test
    void openApiDocsAndSwaggerUiEndpointsWorkWithSpringBoot4() throws Exception {
        MvcResult docsRes = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();
        String apiDocs = docsRes.getResponse().getContentAsString();
        assertTrue(apiDocs.contains("Dinga AI"));
        assertTrue(apiDocs.contains("/api/chat"));
        assertTrue(apiDocs.contains("/api/chat/stream"));
        assertTrue(apiDocs.contains("/api/simulator/start"));
        assertTrue(apiDocs.contains("/api/knowledge/upload"));
        assertTrue(apiDocs.contains("/api/metrics"));

        int swaggerStatus = mockMvc.perform(get("/swagger-ui.html"))
                .andReturn()
                .getResponse()
                .getStatus();
        assertTrue(swaggerStatus == 200 || swaggerStatus == 302, "Expected 200 or 302 for /swagger-ui.html, got " + swaggerStatus);

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }

    @Test
    void frontendAssetsIncludeAllBoostFeaturesMinimalistically() throws Exception {
        String html;
        String js;
        String css;
        try (InputStream in = new ClassPathResource("static/index.html").getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream in = new ClassPathResource("static/app.js").getInputStream()) {
            js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream in = new ClassPathResource("static/styles.css").getInputStream()) {
            css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertTrue(html.contains("id=\"toggle-audio-speed\""));
        assertTrue(html.contains("id=\"tab-simulator\""));
        assertTrue(html.contains("id=\"tab-vocab\""));
        assertTrue(html.contains("id=\"rag-upload-dropzone\""));
        assertTrue(html.contains("id=\"metrics-grid\""));
        assertTrue(html.contains("/swagger-ui.html"));

        assertTrue(js.contains("dinga_progress_v1"));
        assertTrue(js.contains("dinga_vocab_v1"));
        assertTrue(js.contains("/api/simulator/start"));
        assertTrue(js.contains("/api/knowledge/upload"));
        assertTrue(js.contains("/api/metrics"));

        assertTrue(css.contains(".audio-loading"));
        assertTrue(css.contains(".audio-playing"));

        // Round 2 Boost features: Nivelamento CEFR, SSE Streaming, Exportação Anki/Boletim, Atalhos e Busca Vocabulário
        assertTrue(html.contains("id=\"open-placement-btn\""));
        assertTrue(html.contains("id=\"placement-dialog\""));
        assertTrue(html.contains("id=\"vocab-search-input\""));
        assertTrue(html.contains("id=\"vocab-export-csv-btn\""));
        assertTrue(html.contains("id=\"print-report-btn\""));

        assertTrue(js.contains("/api/chat/stream"));
        assertTrue(js.contains("PLACEMENT_QUESTIONS"));
        assertTrue(js.contains("exportVocabToAnkiCsv"));
        assertTrue(js.contains("printSessionReport"));
        assertTrue(js.contains("setupGlobalKeyboardShortcuts"));
        assertTrue(js.contains("resolveCefrLevelFromPlacement"));

        assertTrue(css.contains(".msg-streaming"));
        assertTrue(css.contains(".placement-trigger-btn"));
        assertTrue(css.contains(".sidebar-export-row"));
    }

    @Test
    void chatStreamEndpointStreamsSseTokensAndDoneEventWithLgpdAndValidation() throws Exception {
        // 1. Validation error on blank message returns standardized 400 JSON before opening SSE stream
        mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/api/chat/stream"));

        // 2. Valid request streams SSE 'token' events + final 'done' ChatResponse with LGPD masking
        MvcResult asyncResult = mockMvc.perform(post("/api/chat/stream")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"Meu CPF é 123.456.789-00, o que é grounding em RAG?\",\"level\":\"B1\"}"))
                .andExpect(request().asyncStarted())
                .andReturn();

        MvcResult completed = mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andReturn();

        String sseBody = completed.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(sseBody.contains("event:token"), "SSE output should contain token events");
        assertTrue(sseBody.contains("event:done"), "SSE output should contain final done event");
        assertTrue(sseBody.contains("\"delta\""), "SSE token event should contain delta payload");
        assertTrue(sseBody.contains("\"piiMasked\":true"), "LGPD guardrail must be active in streaming mode");
        assertTrue(sseBody.contains("DINGA-"), "Final SSE done event must include MTG watermarkId");
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridLlmClientStreamsGemini38FlashSseAndCachesDuplicateCalls() throws Exception {
        LlmProperties geminiProps = new LlmProperties(
                "gemini",
                "fake-test-key",
                "gemini-3.8-flash",
                "", "", "", "", "", "", ""
        );
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<Stream<String>> sseResponse = mock(HttpResponse.class);
        when(sseResponse.statusCode()).thenReturn(200);
        when(sseResponse.body()).thenReturn(Stream.of(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Grounding \"}]}}]}",
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"ancora respostas \"}]}}]}",
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"em fontes RAG.\"}]}}]}"
        ));
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(sseResponse);

        HybridLlmClient client = new HybridLlmClient(geminiProps, objectMapper, mockHttp);
        List<String> receivedTokens = new ArrayList<>();

        var first = client.streamGenerate("system-prompt", "O que é grounding?", List.of(), receivedTokens::add);
        assertTrue(first.isPresent());
        assertEquals("Grounding ancora respostas em fontes RAG.", first.get().text());
        assertEquals("Gemini (gemini-3.8-flash)", first.get().providerLabel());
        assertEquals(List.of("Grounding ", "ancora respostas ", "em fontes RAG."), receivedTokens);
        assertEquals(1, client.cacheSize());
        assertEquals(0, client.cacheHits());
        assertEquals(1, client.cacheMisses());

        // Second identical call (both streamGenerate and generate) must be served from cache without calling HTTP again
        List<String> cachedTokens = new ArrayList<>();
        var second = client.streamGenerate("system-prompt", "O que é grounding?", List.of(), cachedTokens::add);
        assertTrue(second.isPresent());
        assertEquals(first.get().text(), second.get().text());
        assertEquals(List.of("Grounding ancora respostas em fontes RAG."), cachedTokens);

        var third = client.generate("system-prompt", "O que é grounding?", List.of());
        assertTrue(third.isPresent());
        assertEquals(first.get().text(), third.get().text());

        assertEquals(2, client.cacheHits());
        verify(mockHttp, times(1)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        client.clearCache();
        assertEquals(0, client.cacheSize());
        assertEquals(0, client.cacheHits());
    }

    @Test
    void rateLimiterEvictsExpiredEntriesAndInterceptorValidatesXForwardedForSafely() {
        RateLimiterService limiter = new RateLimiterService(true, null);
        limiter.checkLimit("10.0.0.1", "chat", 5, 1000L);
        limiter.checkLimit("10.0.0.2", "chat", 5, 1000L);
        assertEquals(2, limiter.trackedKeyCount());

        // Before window expires: nothing evicted
        int evictedNow = limiter.evictExpired(System.currentTimeMillis(), 60_000L);
        assertEquals(0, evictedNow);
        assertEquals(2, limiter.trackedKeyCount());

        // After window expires: all entries evicted so ConcurrentHashMap does not leak memory
        int evictedLater = limiter.evictExpired(System.currentTimeMillis() + 120_000L, 60_000L);
        assertEquals(2, evictedLater);
        assertEquals(0, limiter.trackedKeyCount());

        // Untrusted direct client spoofing X-Forwarded-For must be ignored when trustProxy=false
        RateLimitInterceptor strictInterceptor = new RateLimitInterceptor(limiter, false);
        MockHttpServletRequest spoofedReq = new MockHttpServletRequest();
        spoofedReq.setRemoteAddr("203.0.113.9");
        spoofedReq.addHeader("X-Forwarded-For", "1.2.3.4");
        assertEquals("203.0.113.9", strictInterceptor.extractClientIp(spoofedReq));

        // Loopback proxy with valid X-Forwarded-For extracts validated client IP
        MockHttpServletRequest loopbackReq = new MockHttpServletRequest();
        loopbackReq.setRemoteAddr("127.0.0.1");
        loopbackReq.addHeader("X-Forwarded-For", "198.51.100.42, 10.0.0.1");
        assertEquals("198.51.100.42", strictInterceptor.extractClientIp(loopbackReq));

        // Malformed / injected X-Forwarded-For is rejected and falls back to remoteAddr
        MockHttpServletRequest malformedReq = new MockHttpServletRequest();
        malformedReq.setRemoteAddr("127.0.0.1");
        malformedReq.addHeader("X-Forwarded-For", "evil;DROP TABLE users");
        assertEquals("127.0.0.1", strictInterceptor.extractClientIp(malformedReq));

        // Malformed IPv6-like strings must also be rejected (InetAddress.ofLiteral strict check)
        MockHttpServletRequest bogusIpv6Req = new MockHttpServletRequest();
        bogusIpv6Req.setRemoteAddr("127.0.0.1");
        bogusIpv6Req.addHeader("X-Forwarded-For", "::::::::, dead:beef:1:2:3:4:5:6:7:8:9");
        assertEquals("127.0.0.1", strictInterceptor.extractClientIp(bogusIpv6Req));

        // Trusted proxy mode honors valid IPv6 in X-Forwarded-For
        RateLimitInterceptor trustedProxyInterceptor = new RateLimitInterceptor(limiter, true);
        MockHttpServletRequest proxyIpv6Req = new MockHttpServletRequest();
        proxyIpv6Req.setRemoteAddr("10.0.0.5");
        proxyIpv6Req.addHeader("X-Forwarded-For", "2001:db8::1");
        assertEquals("2001:db8::1", trustedProxyInterceptor.extractClientIp(proxyIpv6Req));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridLlmClientClosesNon2xxStreamHandlesMultiPartChunksAndDoesNotCacheInterruptedStreams() throws Exception {
        LlmProperties geminiProps = new LlmProperties(
                "gemini",
                "fake-test-key",
                "gemini-3.8-flash",
                "", "", "", "", "", "", ""
        );
        HttpClient mockHttp = mock(HttpClient.class);

        // 1. Primary model returns HTTP 429 -> its body Stream MUST be closed, then falls back to next model with multi-part SSE chunk
        boolean[] closedFlag = {false};
        HttpResponse<Stream<String>> rateLimitedResp = mock(HttpResponse.class);
        when(rateLimitedResp.statusCode()).thenReturn(429);
        when(rateLimitedResp.body()).thenReturn(Stream.<String>empty().onClose(() -> closedFlag[0] = true));

        HttpResponse<Stream<String>> fallbackResp = mock(HttpResponse.class);
        when(fallbackResp.statusCode()).thenReturn(200);
        when(fallbackResp.body()).thenReturn(Stream.of(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Hello \"},{\"text\":\"world!\"}]}}]}"
        ));

        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(rateLimitedResp)
                .thenReturn(fallbackResp);

        HybridLlmClient client = new HybridLlmClient(geminiProps, objectMapper, mockHttp);
        List<String> tokens = new ArrayList<>();
        var result = client.streamGenerate("sys", "hi", List.of(), tokens::add);

        assertTrue(closedFlag[0], "Non-2xx streaming response body must be closed to avoid socket leaks");
        assertTrue(result.isPresent());
        assertEquals("Hello world!", result.get().text());
        assertEquals(List.of("Hello world!"), tokens);
        assertEquals(1, client.cacheSize());

        // 2. Interrupted stream mid-flight returns partial text already sent, but MUST NOT cache it
        HttpResponse<Stream<String>> brokenResp = mock(HttpResponse.class);
        when(brokenResp.statusCode()).thenReturn(200);
        Stream<String> failingStream = Stream.of(
                "data: {\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Partial \"}]}}]}",
                "TRIGGER_FAIL"
        ).map(line -> {
            if ("TRIGGER_FAIL".equals(line)) {
                throw new RuntimeException("Simulated network drop");
            }
            return line;
        });
        when(brokenResp.body()).thenReturn(failingStream);
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(brokenResp);

        client.clearCache();
        List<String> partialTokens = new ArrayList<>();
        var partial = client.streamGenerate("sys", "interrupted-query", List.of(), partialTokens::add);
        assertTrue(partial.isPresent());
        assertEquals("Partial", partial.get().text());
        assertEquals(0, client.cacheSize(), "Interrupted partial stream must never poison the LLM cache");
    }
}
