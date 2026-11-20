package br.edu.usjt.dinga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatResponse;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.LearnerState;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.SimulationRequest;
import br.edu.usjt.dinga.model.SimulationResponse;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.service.DingaTutorService;
import br.edu.usjt.dinga.service.GroundednessVerifier;
import br.edu.usjt.dinga.service.QuizService;
import br.edu.usjt.dinga.service.SimulatorService;
import br.edu.usjt.dinga.skills.DiagnosticoPerfilSkill;
import br.edu.usjt.dinga.skills.GuardiaoEticoMtgSkill;
import br.edu.usjt.dinga.skills.InglesTecnicoSkill;
import br.edu.usjt.dinga.skills.PraticaAtivaSkill;
import br.edu.usjt.dinga.skills.SkillRouter;
import br.edu.usjt.dinga.skills.TeacherSkill;
import br.edu.usjt.dinga.skills.TutorSocraticoSkill;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Flow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class AiCohesionBoostTests {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SkillRouter skillRouter;

    @Autowired
    private Bm25RagRetriever ragRetriever;

    @Autowired
    private KnowledgeBaseLoader knowledgeLoader;

    @Autowired
    private DingaTutorService tutorService;

    @Autowired
    private DiagnosticoPerfilSkill diagnosticoSkill;

    @Autowired
    private TutorSocraticoSkill tutorSkill;

    @Autowired
    private InglesTecnicoSkill tecnicoSkill;

    @Autowired
    private PraticaAtivaSkill praticaSkill;

    @Autowired
    private GuardiaoEticoMtgSkill eticoSkill;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    // 1. Memória Pedagógica do Aluno (Learner State)
    @Test
    void learnerStateIsInjectedInPromptSanitizedForLgpdAndUpdatedViaDelta() throws Exception {
        LearnerState stateWithPii = new LearnerState(
                List.of("Passado simples regular (-ed)", "Contato aluno@teste.com"),
                List.of("Falsos Cognatos", "falsos cognatos"),
                List.of("Cloud, APIs & Git"),
                85
        );
        // Deduplicação case-insensitive garantida no record
        assertEquals(1, stateWithPii.masteredTopics().size());

        ChatRequest req = new ChatRequest(
                "Como uso grounding no Microsoft Learn?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false,
                List.of(),
                stateWithPii
        );
        String prompt = tecnicoSkill.buildSystemPrompt(req, List.of());
        assertTrue(prompt.contains("MEMÓRIA PEDAGÓGICA DO ALUNO"));
        assertTrue(prompt.contains("Passado simples regular (-ed)"));
        assertTrue(prompt.contains("Falsos Cognatos"));
        assertTrue(prompt.contains("85%"));

        // Endpoint /api/chat recebe learnerState e devolve learnerStateDelta com weakPoints detectados
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message": "Corrija minha frase de daily: Yesterday I work in the API and I have 20 years.",
                                  "level": "A2",
                                  "goal": "Carreira",
                                  "preferredSkill": "auto",
                                  "lowDataMode": false,
                                  "history": [],
                                  "learnerState": {
                                    "weakPoints": ["Preposição de 'depend'"],
                                    "masteredTopics": ["Falsos Cognatos"],
                                    "recentTopics": ["Daily Standup"],
                                    "quizAccuracyPercent": 75
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.learnerStateDelta.addedWeakPoints").isArray())
                .andExpect(jsonPath("$.learnerStateDelta.addedWeakPoints.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.learnerStateDelta.focusTopic").isNotEmpty())
                .andExpect(jsonPath("$.mtg.groundednessScore").isNumber());

        // Verifica que PII dentro do learnerState é mascarada antes de chegar ao LLM
        HybridLlmClient mockLlm = mock(HybridLlmClient.class);
        when(mockLlm.supportsGenerationOptions()).thenReturn(true);
        List<String> capturedSystemPrompts = new ArrayList<>();
        when(mockLlm.generate(any(), any(), any(), any())).thenAnswer(inv -> {
            capturedSystemPrompts.add(inv.getArgument(0, String.class));
            return Optional.of(new HybridLlmClient.LlmResult(
                    "Grounding ancora o modelo em documentos confiáveis do RAG.",
                    "Gemini (gemini-3.8-flash)"
            ));
        });
        DingaTutorService customTutor = new DingaTutorService(skillRouter, ragRetriever, mockLlm);
        customTutor.chat(req);

        assertEquals(1, capturedSystemPrompts.size());
        assertFalse(capturedSystemPrompts.getFirst().contains("aluno@teste.com"));
        assertTrue(capturedSystemPrompts.getFirst().contains("[E-MAIL PROTEGIDO — LGPD]"));
    }

    // 2. Few-Shot Prompting Calibrado por Skill e Nível CEFR (A1 -> B2)
    @Test
    void fewShotPromptingIsCalibratedAcrossAllSkillsAndCefrLevels() {
        List<TeacherSkill> allSkills = List.of(diagnosticoSkill, tutorSkill, tecnicoSkill, praticaSkill, eticoSkill);
        List<String> cefrLevels = List.of("A1", "A2", "B1", "B2");

        for (TeacherSkill skill : allSkills) {
            String a1Prompt = skill.buildSystemPrompt(new ChatRequest("Teste", "A1", "Tech", "auto", false), List.of());
            String b2Prompt = skill.buildSystemPrompt(new ChatRequest("Teste", "B2", "Tech", "auto", false), List.of());
            assertNotEquals(a1Prompt, b2Prompt, "A1 e B2 devem ter calibração few-shot distinta na skill " + skill.definition().id());

            for (String level : cefrLevels) {
                String prompt = skill.buildSystemPrompt(new ChatRequest("Pergunta", level, "Tech", "auto", false), List.of());
                assertTrue(prompt.contains("Calibração CEFR (" + level + "):"), "Faltou diretriz CEFR " + level + " em " + skill.definition().id());
                assertTrue(prompt.contains("EXEMPLOS CALIBRADOS (FEW-SHOT"), "Faltou bloco few-shot em " + skill.definition().id());
                assertTrue(prompt.contains("Entrada:") && prompt.contains("Resposta ideal:"), "Faltou par Entrada/Resposta ideal em " + skill.definition().id());
            }
        }
    }

    // 3. Temperatura Dinâmica por Skill (Controlled Decoding)
    @Test
    void skillsDefineDynamicDecodingOptionsAndHybridLlmClientSerializesThemToGemini38Flash() throws Exception {
        assertTrue(tecnicoSkill.generationOptions().temperature() >= 0.20 && tecnicoSkill.generationOptions().temperature() <= 0.30);
        assertTrue(eticoSkill.generationOptions().temperature() >= 0.20 && eticoSkill.generationOptions().temperature() <= 0.30);
        assertTrue(tutorSkill.generationOptions().temperature() >= 0.50 && tutorSkill.generationOptions().temperature() <= 0.60);
        assertTrue(praticaSkill.generationOptions().temperature() >= 0.50 && praticaSkill.generationOptions().temperature() <= 0.60);

        List<String> capturedBodies = new ArrayList<>();
        HttpClient mockHttp = mockHttpCapturingGeminiRequests(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"Resposta calibrada.\"}]}}]}",
                200,
                capturedBodies
        );

        LlmProperties geminiProps = new LlmProperties("gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        HybridLlmClient client = new HybridLlmClient(geminiProps, objectMapper, mockHttp);

        client.generate("Sys", "Msg", List.of(), tecnicoSkill.generationOptions());
        client.generate("Sys", "Msg", List.of(), tutorSkill.generationOptions());

        // Cache key deve diferenciar temperature/topP distintos (2 chamadas HTTP reais)
        assertEquals(2, capturedBodies.size());

        JsonNode techBody = objectMapper.readTree(capturedBodies.get(0));
        assertEquals(0.25, techBody.path("generationConfig").path("temperature").asDouble(), 0.001);
        assertEquals(0.85, techBody.path("generationConfig").path("topP").asDouble(), 0.001);

        JsonNode socraticBody = objectMapper.readTree(capturedBodies.get(1));
        assertEquals(0.55, socraticBody.path("generationConfig").path("temperature").asDouble(), 0.001);
        assertEquals(0.92, socraticBody.path("generationConfig").path("topP").asDouble(), 0.001);
    }

    // 4. Continuidade de Skill (Conversation Stickiness) no SkillRouter
    @Test
    void skillRouterPreservesPreviousSkillOnShortFollowUpsAndSwitchesOnClearIntentChange() {
        ChatTurn techTurn = new ChatTurn(
                "O que é endpoint e payload?",
                "Endpoint é a URL e payload é o corpo JSON...",
                "Explique em 1 frase a diferença entre grounding e hallucination.",
                "ingles-tecnico"
        );

        // Continuação curta mantém ingles-tecnico
        SkillRouter.RoutingDecision sticky1 = skillRouter.route(new ChatRequest(
                "Pode dar mais um exemplo?", "B1", "Tech", "auto", false, List.of(techTurn)
        ));
        assertEquals("ingles-tecnico", sticky1.skill().definition().id());
        assertTrue(sticky1.reasoning().contains("Stickiness"));

        // Pedido de clarificação ("explica melhor") após guardiao-etico-mtg não cai no tutor-socratico
        ChatTurn ethicsTurn = new ChatTurn(
                "Como funciona o MTG-IA?",
                "O MTG-IA une transparência, privacidade e supervisão humana.",
                "Como você validaria uma resposta de IA?",
                "guardiao-etico-mtg"
        );
        SkillRouter.RoutingDecision stickyEthics = skillRouter.route(new ChatRequest(
                "Não entendi, explica melhor", "B1", "Tech", "auto", false, List.of(ethicsTurn)
        ));
        assertEquals("guardiao-etico-mtg", stickyEthics.skill().definition().id());
        assertTrue(stickyEthics.reasoning().contains("Stickiness"));

        // Mudança clara de intenção para ética troca corretamente de skill
        SkillRouter.RoutingDecision switchedToEthics = skillRouter.route(new ChatRequest(
                "Como vocês aplicam MTG-IA, LGPD e ODS?", "B1", "Tech", "auto", false, List.of(techTurn)
        ));
        assertEquals("guardiao-etico-mtg", switchedToEthics.skill().definition().id());

        // Mudança clara de intenção para gramática/falso cognato troca para tutor-socratico
        SkillRouter.RoutingDecision switchedToTutor = skillRouter.route(new ChatRequest(
                "Qual a diferença entre actually e currently?", "A2", "Tech", "auto", false, List.of(techTurn)
        ));
        assertEquals("tutor-socratico", switchedToTutor.skill().definition().id());

        // Seleção manual sempre prevalece sobre stickiness
        SkillRouter.RoutingDecision manualOverride = skillRouter.route(new ChatRequest(
                "Pode dar mais um exemplo?", "B1", "Tech", "pratica-ativa", false, List.of(techTurn)
        ));
        assertEquals("pratica-ativa", manualOverride.skill().definition().id());
    }

    // 5. Structured Outputs (JSON Schema) no Gemini 3.8 Flash + Fallback seguro se HTTP 400
    @Test
    @SuppressWarnings("unchecked")
    void quizAndSimulatorUseGeminiStructuredOutputsWithSafeFallbackOn400() throws Exception {
        // 5a. QuizService envia responseMimeType + responseSchema com temperatura 0.25
        List<String> quizBodies = new ArrayList<>();
        String quizJsonPayload = """
                {"question":"Qual a função do grounding no RAG?","codeOrDocSnippet":"\\"Use RAG to ground responses.\\"","options":["Ancorar em fontes confiáveis","Treinar pesos do zero","Ignorar validação","Desativar logs"],"correctIndex":0,"explanation":"Grounding reduz alucinações.","englishExample":"RAG grounds the model in trusted docs."}
                """.trim();
        HttpClient mockQuizHttp = mockHttpCapturingGeminiRequests(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + objectMapper.writeValueAsString(quizJsonPayload) + "}]}}]}",
                200,
                quizBodies
        );
        LlmProperties geminiProps = new LlmProperties("gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        QuizService customQuiz = new QuizService(
                knowledgeLoader, ragRetriever, new HybridLlmClient(geminiProps, objectMapper, mockQuizHttp), objectMapper
        );
        QuizService.GeneratedQuizBatch batch = customQuiz.generateQuestions(new QuizGenerationRequest("grounding", "B1", "", 1));
        assertEquals(1, batch.generated().size());
        assertEquals(1, quizBodies.size());

        JsonNode quizReq = objectMapper.readTree(quizBodies.getFirst());
        JsonNode quizCfg = quizReq.path("generationConfig");
        assertEquals(0.25, quizCfg.path("temperature").asDouble(), 0.001);
        assertEquals("application/json", quizCfg.path("responseMimeType").asString());
        assertEquals("OBJECT", quizCfg.path("responseSchema").path("type").asString());
        assertTrue(quizCfg.path("responseSchema").path("properties").has("options"));

        // 5b. SimulatorService envia responseMimeType + responseSchema com temperatura 0.50
        List<String> simBodies = new ArrayList<>();
        String simJsonPayload = """
                {"clarityScore":90,"grammarScore":88,"technicalVocabScore":92,"feedback":"Excelente relato técnico.","naturalVersion":"Yesterday I worked on the API endpoint.","strength":"Clareza técnica.","improvement":"Cite testes automatizados."}
                """.trim();
        HttpClient mockSimHttp = mockHttpCapturingGeminiRequests(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":" + objectMapper.writeValueAsString(simJsonPayload) + "}]}}]}",
                200,
                simBodies
        );
        SimulatorService customSim = new SimulatorService(
                new HybridLlmClient(geminiProps, objectMapper, mockSimHttp), objectMapper
        );
        SimulationResponse simResp = customSim.submitAnswer(new SimulationRequest(
                "daily", "B1", 1, "Yesterday I worked on the REST API endpoint and wrote unit tests.", List.of()
        ));
        assertNotNull(simResp.turnEvaluation());
        assertEquals(90, simResp.turnEvaluation().clarityScore());
        assertEquals(1, simBodies.size());

        JsonNode simReq = objectMapper.readTree(simBodies.getFirst());
        JsonNode simCfg = simReq.path("generationConfig");
        assertEquals(0.50, simCfg.path("temperature").asDouble(), 0.001);
        assertEquals("application/json", simCfg.path("responseMimeType").asString());
        assertEquals("OBJECT", simCfg.path("responseSchema").path("type").asString());
        assertTrue(simCfg.path("responseSchema").path("properties").has("clarityScore"));

        // 5c. Fallback seguro: se o modelo retornar HTTP 400 para responseSchema, tenta novamente sem schema
        HttpClient fallbackHttp = mock(HttpClient.class);
        HttpResponse<String> badSchemaResp = mock(HttpResponse.class);
        when(badSchemaResp.statusCode()).thenReturn(400);
        when(badSchemaResp.body()).thenReturn("{\"error\":\"responseSchema unsupported\"}");

        HttpResponse<String> okPlainResp = mock(HttpResponse.class);
        when(okPlainResp.statusCode()).thenReturn(200);
        when(okPlainResp.body()).thenReturn("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}");

        List<String> retryBodies = new ArrayList<>();
        when(fallbackHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0, HttpRequest.class);
            retryBodies.add(extractRequestBody(req));
            return retryBodies.size() == 1 ? badSchemaResp : okPlainResp;
        });

        HybridLlmClient fallbackClient = new HybridLlmClient(geminiProps, objectMapper, fallbackHttp);
        Optional<HybridLlmClient.LlmResult> recovered = fallbackClient.generate(
                "Sys",
                "User",
                HybridLlmClient.GenerationOptions.structuredJson(0.25, 0.85, 500, java.util.Map.of("type", "OBJECT"))
        );
        assertTrue(recovered.isPresent());
        assertEquals("{\"ok\":true}", recovered.get().text());
        assertEquals(2, retryBodies.size());
        assertTrue(objectMapper.readTree(retryBodies.get(0)).path("generationConfig").has("responseSchema"));
        assertFalse(objectMapper.readTree(retryBodies.get(1)).path("generationConfig").has("responseSchema"));
        verify(fallbackHttp, times(2)).send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    // 6. Verificador Pós-Geração de Ancoragem (Groundedness Score — 0 ms) + Checagem de Saída LGPD/PII
    @Test
    void groundednessVerifierScoresAnchoredVsOffTopicRepliesAndMasksOutputPii() throws Exception {
        KnowledgeChunk ragChunk = new KnowledgeChunk(
                "chunk-05-1",
                "05-ingles-tecnico-microsoft-learn-ia",
                "Inglês Técnico — Microsoft Learn & IA",
                "B1",
                "Microsoft Learn & IA",
                "Microsoft Learn",
                "Grounding, RAG e Hallucination",
                "- **Grounding:** ancorar a resposta da IA em documentos confiáveis via **RAG**.\n- **Hallucination:** quando o modelo inventa fatos sem fonte verificável.",
                1.0
        );

        GroundednessVerifier.VerificationResult grounded = GroundednessVerifier.verify(
                "No Microsoft Learn, grounding significa ancorar a resposta em documentos confiáveis usando RAG para evitar hallucination.",
                List.of(ragChunk)
        );
        assertTrue(grounded.groundednessScore() >= 0.55, "Resposta ancorada deveria ter score alto, mas obteve " + grounded.groundednessScore());
        assertFalse(grounded.outputPiiMasked());

        GroundednessVerifier.VerificationResult offTopic = GroundednessVerifier.verify(
                "Receita tradicional de bolo de cenoura com cobertura de chocolate e manteiga fresca.",
                List.of(ragChunk)
        );
        assertTrue(offTopic.groundednessScore() <= 0.20, "Resposta fora do contexto deveria ter score baixo, mas obteve " + offTopic.groundednessScore());

        assertEquals(0.0, GroundednessVerifier.computeGroundednessScore("Qualquer texto", List.of()));

        // Checagem de saída LGPD/PII integrada ao DingaTutorService
        HybridLlmClient leakyLlm = mock(HybridLlmClient.class);
        when(leakyLlm.supportsGenerationOptions()).thenReturn(true);
        when(leakyLlm.generate(any(), any(), any(), any())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                "Grounding com RAG evita alucinações. Para suporte, contate vazado@empresa.com ou CPF 123.456.789-00.",
                "Gemini (gemini-3.8-flash)"
        )));

        DingaTutorService tutorWithLeakyLlm = new DingaTutorService(skillRouter, ragRetriever, leakyLlm);
        ChatResponse safeResponse = tutorWithLeakyLlm.chat(new ChatRequest(
                "O que é grounding e RAG no Microsoft Learn?", "B1", "Tech", "auto", false
        ));

        assertFalse(safeResponse.reply().contains("vazado@empresa.com"));
        assertFalse(safeResponse.reply().contains("123.456.789-00"));
        assertTrue(safeResponse.reply().contains("[E-MAIL PROTEGIDO — LGPD]"));
        assertTrue(safeResponse.reply().contains("[CPF PROTEGIDO — LGPD]"));
        assertTrue(safeResponse.mtg().piiMasked());
        assertTrue(safeResponse.mtg().groundednessScore() > 0.0);

        // Verifica integração no frontend (app.js): Ancoragem no rodapé MTG-IA e LearnerState local + limpeza LGPD
        try (InputStream is = new ClassPathResource("static/app.js").getInputStream()) {
            String appJs = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(appJs.contains("Ancoragem ${groundedPct}%"), "app.js deve exibir Ancoragem discretamente no rodapé MTG-IA");
            assertTrue(appJs.contains("learnerState: state.learnerState"), "app.js deve enviar learnerState no payload de chat");
            assertTrue(appJs.contains("applyLearnerStateDelta"), "app.js deve aplicar learnerStateDelta retornado pelo tutor");
            assertTrue(appJs.contains("recordLearnerQuizOutcome"), "app.js deve atualizar learnerState ao responder quiz");
        }
    }

    @Test
    void chatStreamMasksSplitPiiTokensAndMergesInputOutputPrivacyNote() {
        HybridLlmClient streamingLeakyLlm = mock(HybridLlmClient.class);
        when(streamingLeakyLlm.supportsGenerationOptions()).thenReturn(true);
        when(streamingLeakyLlm.streamGenerate(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            @SuppressWarnings("unchecked")
            java.util.function.Consumer<String> onToken = inv.getArgument(4, java.util.function.Consumer.class);
            // Simula PII dividida entre múltiplos chunks SSE
            onToken.accept("Grounding com RAG evita alucinações. ");
            onToken.accept("Para contato use vazado@");
            onToken.accept("empresa.com ou CPF 123.456.");
            onToken.accept("789-00 imediatamente.");
            return Optional.of(new HybridLlmClient.LlmResult(
                    "Grounding com RAG evita alucinações. Para contato use vazado@empresa.com ou CPF 123.456.789-00 imediatamente.",
                    "Gemini (gemini-3.8-flash)"
            ));
        });

        DingaTutorService streamingTutor = new DingaTutorService(skillRouter, ragRetriever, streamingLeakyLlm);
        StringBuilder streamedOut = new StringBuilder();
        ChatResponse response = streamingTutor.chatStream(
                new ChatRequest("Meu telefone é (11) 99999-8888. O que é grounding?", "B1", "Tech", "auto", false),
                streamedOut::append
        );

        String streamedText = streamedOut.toString();
        assertFalse(streamedText.contains("vazado@empresa.com"), "Token SSE não pode vazar e-mail cru: " + streamedText);
        assertFalse(streamedText.contains("123.456.789-00"), "Token SSE não pode vazar CPF cru: " + streamedText);
        assertTrue(streamedText.contains("[E-MAIL PROTEGIDO — LGPD]"));
        assertTrue(streamedText.contains("[CPF PROTEGIDO — LGPD]"));

        assertTrue(response.mtg().piiMasked());
        assertTrue(response.mtg().privacyNote().contains("telefone"));
        assertTrue(response.mtg().privacyNote().contains("e-mail"));
        assertTrue(response.mtg().privacyNote().contains("CPF"));

        // Placeholders LGPD não devem distorcer o groundednessScore
        KnowledgeChunk chunk = new KnowledgeChunk(
                "c1", "doc1", "LGPD", "B1", "Ética", "USJT", "Privacidade",
                "Protegido por LGPD com foco em privacidade e segurança de dados.", 1.0
        );
        double scoreOnlyPlaceholder = GroundednessVerifier.computeGroundednessScore("[CPF PROTEGIDO — LGPD]", List.of(chunk));
        assertEquals(0.0, scoreOnlyPlaceholder);
    }

    @Test
    void learnerStateDeltaDetectsMasteredAndWeakFromGeminiMicroChallengeReplies() {
        ChatTurn prevChallenge = new ChatTurn(
                "Qual a diferença entre actually e currently?",
                "Actually significa na verdade...",
                "Desafio rápido: como você diria 'Na verdade, eu estudo tecnologia todas as noites'?",
                "tutor-socratico"
        );

        HybridLlmClient geminiPositive = mock(HybridLlmClient.class);
        when(geminiPositive.supportsGenerationOptions()).thenReturn(true);
        when(geminiPositive.generate(any(), any(), any(), any())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                "Excelente! Você usou \"actually\" corretamente no sentido de na verdade.",
                "Gemini (gemini-3.8-flash)"
        )));
        DingaTutorService tutorPositive = new DingaTutorService(skillRouter, ragRetriever, geminiPositive);
        ChatResponse okResp = tutorPositive.chat(new ChatRequest(
                "Actually, I study technology every night.", "A2", "Tech", "auto", false, List.of(prevChallenge)
        ));
        assertFalse(okResp.learnerStateDelta().addedMasteredTopics().isEmpty(), "Resposta positiva do Gemini deve marcar tópico dominado");
        assertTrue(okResp.learnerStateDelta().addedWeakPoints().isEmpty());

        HybridLlmClient geminiCorrective = mock(HybridLlmClient.class);
        when(geminiCorrective.supportsGenerationOptions()).thenReturn(true);
        when(geminiCorrective.generate(any(), any(), any(), any())).thenReturn(Optional.of(new HybridLlmClient.LlmResult(
                "Quase lá! Você usou currently, mas para na verdade use actually.",
                "Gemini (gemini-3.8-flash)"
        )));
        DingaTutorService tutorCorrective = new DingaTutorService(skillRouter, ragRetriever, geminiCorrective);
        ChatResponse weakResp = tutorCorrective.chat(new ChatRequest(
                "Currently, I study technology every night.", "A2", "Tech", "auto", false, List.of(prevChallenge)
        ));
        assertTrue(weakResp.learnerStateDelta().addedMasteredTopics().isEmpty());
        assertFalse(weakResp.learnerStateDelta().addedWeakPoints().isEmpty(), "Resposta corretiva do Gemini deve registrar ponto fraco");
    }

    @Test
    void skillRouterStickinessHandlesIdiomaticContinuationCues() {
        ChatTurn techTurn = new ChatTurn(
                "O que é grounding no RAG?",
                "Grounding ancora o modelo em fontes confiáveis.",
                "Resuma grounding em 1 frase.",
                "ingles-tecnico"
        );

        // "E na prática?" não deve cair em pratica-ativa por causa do substantivo "prática"
        SkillRouter.RoutingDecision naPratica = skillRouter.route(new ChatRequest(
                "E na prática?", "B1", "Tech", "auto", false, List.of(techTurn)
        ));
        assertEquals("ingles-tecnico", naPratica.skill().definition().id());
        assertTrue(naPratica.reasoning().contains("Stickiness"));

        // "Não entendi o porquê, explica melhor" após guardiao-etico-mtg não deve cair no tutor-socratico
        ChatTurn ethicsTurn = new ChatTurn(
                "Como funciona o MTG-IA?",
                "Transparência, LGPD e Human-in-the-loop.",
                "Como validar uma resposta de IA?",
                "guardiao-etico-mtg"
        );
        SkillRouter.RoutingDecision whyExplain = skillRouter.route(new ChatRequest(
                "Não entendi o porquê, explica melhor", "B1", "Tech", "auto", false, List.of(ethicsTurn)
        ));
        assertEquals("guardiao-etico-mtg", whyExplain.skill().definition().id());
        assertTrue(whyExplain.reasoning().contains("Stickiness"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void hybridLlmClientCacheDifferentiatesSchemasAndOpenAiOllamaFallbackOn400() throws Exception {
        // Cache key deve diferenciar schemas distintos mesmo com mesma temperatura e prompts
        List<String> geminiBodies = new ArrayList<>();
        HttpClient mockGeminiHttp = mockHttpCapturingGeminiRequests(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"ok\\\":true}\"}]}}]}",
                200,
                geminiBodies
        );
        LlmProperties geminiProps = new LlmProperties("gemini", "fake-key", "gemini-3.8-flash", "", "", "", "", "", "", "");
        HybridLlmClient geminiClient = new HybridLlmClient(geminiProps, objectMapper, mockGeminiHttp);

        geminiClient.generate("Sys", "User", HybridLlmClient.GenerationOptions.structuredJson(
                0.25, 0.85, 500, java.util.Map.of("type", "OBJECT", "required", List.of("a"))
        ));
        geminiClient.generate("Sys", "User", HybridLlmClient.GenerationOptions.structuredJson(
                0.25, 0.85, 500, java.util.Map.of("type", "OBJECT", "required", List.of("b"))
        ));
        assertEquals(2, geminiBodies.size(), "Schemas diferentes não devem colidir no cache");

        // OpenAI/Groq: envia top_p + response_format e faz retry sem response_format se HTTP 400
        HttpClient mockOpenAiHttp = mock(HttpClient.class);
        HttpResponse<String> badResp = mock(HttpResponse.class);
        when(badResp.statusCode()).thenReturn(400);
        when(badResp.body()).thenReturn("{\"error\":\"json_object unsupported\"}");
        HttpResponse<String> okOpenAi = mock(HttpResponse.class);
        when(okOpenAi.statusCode()).thenReturn(200);
        when(okOpenAi.body()).thenReturn("{\"choices\":[{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}]}");

        List<String> openAiBodies = new ArrayList<>();
        when(mockOpenAiHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            openAiBodies.add(extractRequestBody(inv.getArgument(0, HttpRequest.class)));
            return openAiBodies.size() == 1 ? badResp : okOpenAi;
        });

        LlmProperties groqProps = new LlmProperties("groq", "", "", "groq-key", "llama-3.3-70b-versatile", "", "", "", "", "");
        HybridLlmClient groqClient = new HybridLlmClient(groqProps, objectMapper, mockOpenAiHttp);
        Optional<HybridLlmClient.LlmResult> groqRes = groqClient.generate(
                "Sys", "User", HybridLlmClient.GenerationOptions.structuredJson(0.25, 0.85, 500, java.util.Map.of("type", "OBJECT"))
        );
        assertTrue(groqRes.isPresent());
        assertEquals(2, openAiBodies.size());
        JsonNode firstGroq = objectMapper.readTree(openAiBodies.get(0));
        assertEquals(0.85, firstGroq.path("top_p").asDouble(), 0.001);
        assertEquals("json_object", firstGroq.path("response_format").path("type").asString());
        assertFalse(objectMapper.readTree(openAiBodies.get(1)).has("response_format"));

        // Ollama: envia options.temperature/top_p + format=json e faz retry sem format se HTTP 400
        HttpClient mockOllamaHttp = mock(HttpClient.class);
        HttpResponse<String> okOllama = mock(HttpResponse.class);
        when(okOllama.statusCode()).thenReturn(200);
        when(okOllama.body()).thenReturn("{\"message\":{\"content\":\"{\\\"ok\\\":true}\"}}");
        List<String> ollamaBodies = new ArrayList<>();
        when(mockOllamaHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            ollamaBodies.add(extractRequestBody(inv.getArgument(0, HttpRequest.class)));
            return ollamaBodies.size() == 1 ? badResp : okOllama;
        });

        LlmProperties ollamaProps = new LlmProperties("ollama", "", "", "", "", "", "", "", "http://localhost:11434", "llama3.2");
        HybridLlmClient ollamaClient = new HybridLlmClient(ollamaProps, objectMapper, mockOllamaHttp);
        Optional<HybridLlmClient.LlmResult> ollamaRes = ollamaClient.generate(
                "Sys", "User", HybridLlmClient.GenerationOptions.structuredJson(0.50, 0.92, 500, java.util.Map.of("type", "OBJECT"))
        );
        assertTrue(ollamaRes.isPresent());
        assertEquals(2, ollamaBodies.size());
        JsonNode firstOllama = objectMapper.readTree(ollamaBodies.get(0));
        assertEquals(0.50, firstOllama.path("options").path("temperature").asDouble(), 0.001);
        assertEquals("json", firstOllama.path("format").asString());
        assertFalse(objectMapper.readTree(ollamaBodies.get(1)).has("format"));
    }

    @SuppressWarnings("unchecked")
    private HttpClient mockHttpCapturingGeminiRequests(String responseJson, int statusCode, List<String> capturedBodies) throws Exception {
        HttpClient mockHttp = mock(HttpClient.class);
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(statusCode);
        when(mockResp.body()).thenReturn(responseJson);
        when(mockHttp.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest req = inv.getArgument(0, HttpRequest.class);
            capturedBodies.add(extractRequestBody(req));
            return mockResp;
        });
        return mockHttp;
    }

    private static String extractRequestBody(HttpRequest request) {
        Optional<HttpRequest.BodyPublisher> publisherOpt = request.bodyPublisher();
        if (publisherOpt.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        publisherOpt.get().subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                sb.append(new String(bytes, StandardCharsets.UTF_8));
            }

            @Override
            public void onError(Throwable throwable) {
            }

            @Override
            public void onComplete() {
            }
        });
        return sb.toString();
    }
}
