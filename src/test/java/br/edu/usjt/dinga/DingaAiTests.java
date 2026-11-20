package br.edu.usjt.dinga;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.controller.DingaController;
import br.edu.usjt.dinga.controller.SlidesRedirectController;
import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatResponse;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.FeedbackReport;
import br.edu.usjt.dinga.model.FeedbackReportRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.NewKnowledgeDocumentRequest;
import br.edu.usjt.dinga.model.QuizAnswerRequest;
import br.edu.usjt.dinga.model.QuizAnswerResponse;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.QuizQuestion;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.service.DingaTutorService;
import br.edu.usjt.dinga.service.FeedbackReportService;
import br.edu.usjt.dinga.service.QuizService;
import br.edu.usjt.dinga.skills.PraticaAtivaSkill;
import br.edu.usjt.dinga.skills.SkillRouter;
import br.edu.usjt.dinga.storage.JsonDataStore;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
class DingaAiTests {

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private KnowledgeBaseLoader knowledgeLoader;

    @Autowired
    private Bm25RagRetriever ragRetriever;

    @Autowired
    private SkillRouter skillRouter;

    @Autowired
    private DingaTutorService tutorService;

    @Autowired
    private FeedbackReportService feedbackService;

    @Autowired
    private SlidesRedirectController slidesRedirectController;

    @Autowired
    private LlmProperties llmProperties;

    @Autowired
    private HybridLlmClient hybridLlmClient;

    @Autowired
    private QuizService quizService;

    @Autowired
    private DingaController dingaController;

    @Test
    void knowledgeBaseLoadsAllCuratedDocuments() {
        assertEquals(15, knowledgeLoader.getDocumentsSummary().size());
        assertTrue(knowledgeLoader.getChunks().size() >= 35);
    }

    @Test
    void bm25RetrieverFindsRelevantChunksForPtAndEnQueries() {
        List<KnowledgeChunk> falseFriends = ragRetriever.retrieve("actually falso cognato", "A2", 3);
        assertFalse(falseFriends.isEmpty());
        assertTrue(falseFriends.getFirst().docId().contains("falsos-cognatos"));

        List<KnowledgeChunk> techTerms = ragRetriever.retrieve("API deploy Microsoft Learn", "B1", 3);
        assertFalse(techTerms.isEmpty());
        assertTrue(techTerms.stream().anyMatch(c -> c.docId().contains("ingles-tecnico")));

        List<KnowledgeChunk> governance = ragRetriever.retrieve("LGPD MTG-IA ODS", "A2", 3);
        assertFalse(governance.isEmpty());
        assertTrue(governance.stream().anyMatch(c -> c.docId().contains("mtg-ia") || c.docId().contains("ods")));
    }

    @Test
    void skillRouterRoutesAllFiveSkillsAndRespectsManualSelection() {
        assertEquals("diagnostico-perfil",
                skillRouter.route(new ChatRequest("Quero montar meu plano de estudos e nivelamento A2", "A2", "Tech", "auto", false))
                        .skill().definition().id());
        assertEquals("ingles-tecnico",
                skillRouter.route(new ChatRequest("O que é endpoint e payload em APIs no Microsoft Learn?", "B1", "Tech", "auto", false))
                        .skill().definition().id());
        assertEquals("pratica-ativa",
                skillRouter.route(new ChatRequest("Corrija minha frase de daily: Yesterday I work", "A2", "Carreira", "auto", false))
                        .skill().definition().id());
        assertEquals("guardiao-etico-mtg",
                skillRouter.route(new ChatRequest("Como vocês aplicam MTG-IA, LGPD e ODS?", "A2", "Tech", "auto", false))
                        .skill().definition().id());
        assertEquals("tutor-socratico",
                skillRouter.route(new ChatRequest("Qual a diferença entre actually e currently?", "A2", "Tech", "auto", false))
                        .skill().definition().id());

        // Seleção manual explícita deve sobrepor roteamento automático
        assertEquals("guardiao-etico-mtg",
                skillRouter.route(new ChatRequest("Olá!", "A1", "Base", "guardiao-etico-mtg", false))
                        .skill().definition().id());
    }

    @Test
    void tutorServiceWorksWithoutApiKeyUsingLocalRagFallback() {
        ChatResponse response = tutorService.chat(new ChatRequest(
                "Como funciona grounding e RAG no Microsoft Learn?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));

        assertNotNull(response);
        assertFalse(response.reply().isBlank());
        assertEquals("ingles-tecnico", response.activeSkill().id());
        assertFalse(response.retrievedSources().isEmpty());
        assertTrue(response.mtg().watermarkId().startsWith("DINGA-"));
        assertTrue(response.mtg().executionMode().contains("RAG Local"));
    }

    @Test
    void feedbackServiceStoresMtgAuditReports() {
        FeedbackReport created = feedbackService.add(new FeedbackReportRequest(
                "DINGA-20261119-TEST",
                "Alucinação / Informação incorreta",
                "Exemplo de relato para auditoria MTG-IA"
        ));
        assertNotNull(created.id());
        assertTrue(feedbackService.listAll().stream().anyMatch(r -> r.id().equals(created.id())));
    }

    @Test
    void chatRequestDeserializesWithOptionalFieldsOmitted() {
        ChatRequest req = objectMapper.readValue(
                "{\"message\":\"Qual a diferença entre actually e currently?\",\"level\":\"A2\"}",
                ChatRequest.class
        );
        assertEquals("Qual a diferença entre actually e currently?", req.message());
        assertEquals("A2", req.level());
        assertEquals("auto", req.preferredSkill());
        assertFalse(req.goal().isBlank());
        assertEquals(false, req.lowDataMode());
    }

    @Test
    void llmPropertiesDefaultsToWorkingGeminiModel() {
        LlmProperties props = new LlmProperties(null, "fake-key", null, null, null, null, null, null, null, null);
        assertEquals("gemini-3.8-flash", props.geminiModel());
        assertEquals("Gemini (gemini-3.8-flash)", props.resolvedProviderName());
        assertTrue(props.isRemoteLlmConfigured());
    }

    @Test
    void slidesDeckContainsEmbeddedBase64ScreenshotsAndRedirectWorks() throws Exception {
        assertEquals("redirect:/slides/index.html", slidesRedirectController.redirectToSlidesIndex());

        ClassPathResource slidesResource = new ClassPathResource("static/slides/index.html");
        assertTrue(slidesResource.exists());
        try (InputStream in = slidesResource.getInputStream()) {
            String html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(html.contains("id=\"img-print-1\" src=\"data:image/png;base64,"));
            assertTrue(html.contains("id=\"img-print-2\" src=\"data:image/png;base64,"));
            assertTrue(html.contains("id=\"img-print-3\" src=\"data:image/png;base64,"));
            assertFalse(html.contains("src=\"screenshot/"));
        }
    }

    @Test
    void gemini38FlashModelIsConfiguredAndPrioritizedInFallbackChain() {
        assertEquals("gemini-3.8-flash", llmProperties.geminiModel());
        List<String> candidates = hybridLlmClient.candidateGeminiModels();
        assertFalse(candidates.isEmpty());
        assertEquals("gemini-3.8-flash", candidates.getFirst());
        assertTrue(candidates.contains("gemini-3.8-flash-lite"));
    }

    @Test
    void conversationMemoryEvaluatesStudentReplyToMicroChallenge() {
        ChatTurn previousTurn = new ChatTurn(
                "Qual a diferença entre actually e currently?",
                "Explicação sobre falsos cognatos...",
                "Desafio rápido: como você diria em inglês 'Na verdade, eu estudo tecnologia todas as noites'? (Cuidado com o falso cognato!)",
                "tutor-socratico"
        );

        ChatResponse correctAttempt = tutorService.chat(new ChatRequest(
                "Actually, I study technology every night.",
                "A2",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false,
                List.of(previousTurn)
        ));
        assertTrue(correctAttempt.reply().contains("Avaliação do seu Micro-desafio"));
        assertTrue(correctAttempt.reply().contains("Excelente!"));

        ChatResponse trapAttempt = tutorService.chat(new ChatRequest(
                "Currently, I study technology every night.",
                "A2",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false,
                List.of(previousTurn)
        ));
        assertTrue(trapAttempt.reply().contains("Avaliação do seu Micro-desafio"));
        assertTrue(trapAttempt.reply().contains("Quase lá!"));
    }

    @Test
    void englishExamplesAreExtractedSeparatelyFromPortugueseExplanation() {
        ChatResponse response = tutorService.chat(new ChatRequest(
                "Corrija minha frase de daily: Yesterday I work in the API and have 20 years old.",
                "A2",
                "Entrevista e Carreira",
                "auto",
                false
        ));
        assertNotNull(response.englishExamples());
        assertFalse(response.englishExamples().isEmpty());
        assertTrue(response.englishExamples().stream().anyMatch(ex -> ex.contains("I am 20 years old") || ex.contains("Yesterday I worked")));
        assertFalse(response.englishExamples().stream().anyMatch(ex -> ex.toLowerCase().contains("na verdade")));
    }

    @Test
    void bm25RetrieverAppliesSurgicalStopwordsAndRelativeThreshold() {
        List<KnowledgeChunk> chunks = ragRetriever.retrieve(
                "Qual a diferença entre actually e currently e como funciona essa frase?",
                "A2",
                3
        );
        assertFalse(chunks.isEmpty());
        assertEquals("07-falsos-cognatos-e-erros-comuns-brasileiros", chunks.getFirst().docId());
        double topScore = chunks.getFirst().score();
        for (KnowledgeChunk chunk : chunks) {
            assertTrue(chunk.score() >= topScore * 0.35);
            assertTrue(chunk.score() >= 0.85);
        }

        List<KnowledgeChunk> unrelated = ragRetriever.retrieve("receita culinaria brigadeiro chocolate panela", "A2", 3);
        assertTrue(unrelated.isEmpty());
    }

    @Test
    void quizServiceProvidesQuestionsAndValidatesAnswersImmediately() {
        List<QuizQuestion> all = quizService.listQuestions(null, null);
        assertTrue(all.size() >= 8);

        List<QuizQuestion> b1Only = quizService.listQuestions("B1", "Microsoft Learn");
        assertFalse(b1Only.isEmpty());
        assertTrue(b1Only.stream().allMatch(q -> "B1".equals(q.level())));

        QuizQuestion first = all.getFirst();
        QuizAnswerResponse right = dingaController.checkQuizAnswer(new QuizAnswerRequest(first.id(), first.correctIndex()));
        assertTrue(right.correct());
        assertFalse(right.explanation().isBlank());
        assertFalse(right.englishExample().isBlank());

        int wrongIdx = (first.correctIndex() + 1) % first.options().size();
        QuizAnswerResponse wrong = dingaController.checkQuizAnswer(new QuizAnswerRequest(first.id(), wrongIdx));
        assertFalse(wrong.correct());
        assertEquals(first.correctIndex(), wrong.correctIndex());

        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> dingaController.checkQuizAnswer(new QuizAnswerRequest(first.id(), -1)));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> dingaController.checkQuizAnswer(new QuizAnswerRequest(first.id(), 99)));
    }

    @Test
    void dailyChallengeEvaluationDetectsPastTenseTrapAtEndOfSentence() {
        ChatTurn dailyTurn = new ChatTurn(
                "Corrija minha frase de daily: Yesterday I work",
                "Modo Prática Ativa...",
                "Reescreva agora em inglês respondendo às 2 primeiras perguntas de uma daily: 'What did you do yesterday?' e 'What will you do today?'",
                "pratica-ativa"
        );
        ChatResponse trapEnd = tutorService.chat(new ChatRequest(
                "Yesterday I work",
                "A2",
                "Entrevista e Carreira",
                "auto",
                false,
                List.of(dailyTurn)
        ));
        assertTrue(trapEnd.reply().contains("Avaliação do seu Micro-desafio"));
        assertTrue(trapEnd.reply().contains("worked"));
    }

    @Test
    void teacherPanelAddsCustomKnowledgeLiveAndRagRetrievesItImmediately() {
        try {
            var createdMap = dingaController.addKnowledgeDocument(new NewKnowledgeDocumentRequest(
                    "Azure AI Studio Prompt Flow e Guardrails",
                    "B1",
                    "Curadoria Docente ao Vivo",
                    "Prof. USJT",
                    "Vocabulário de Prompt Flow e Guardrails",
                    "No Azure AI Studio, Prompt Flow orquestra chamadas de LLM com RAG e Guardrails filtram riscos éticos. \"We use Prompt Flow and Guardrails in Azure AI.\""
            ));
            assertEquals(16, createdMap.get("totalDocuments"));

            ChatResponse chatRes = tutorService.chat(new ChatRequest(
                    "O que são Prompt Flow e Guardrails no Azure AI Studio?",
                    "B1",
                    "Inglês para Tecnologia / Microsoft Learn",
                    "auto",
                    false
            ));
            assertFalse(chatRes.retrievedSources().isEmpty());
            assertTrue(chatRes.retrievedSources().stream().anyMatch(c -> c.docId().startsWith("16-docente-")));
        } finally {
            knowledgeLoader.resetCustomDocuments();
            ragRetriever.refreshIndex();
        }
        assertEquals(15, knowledgeLoader.getDocumentsSummary().size());
    }

    @Test
    void mtgMetadataIncludesLatencyAndPayloadSavingsForLowConnectivity() {
        ChatResponse normal = tutorService.chat(new ChatRequest(
                "Como funciona grounding e RAG no Microsoft Learn?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        ChatResponse lowData = tutorService.chat(new ChatRequest(
                "Como funciona grounding e RAG no Microsoft Learn?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                true
        ));

        assertTrue(normal.mtg().responseTimeMs() >= 1L);
        assertTrue(normal.mtg().payloadSizeKb() > 0.0);
        assertEquals(92, normal.mtg().estimatedBandwidthSavedPercent());
        assertFalse(normal.mtg().lowDataModeActive());

        assertTrue(lowData.mtg().lowDataModeActive());
        assertEquals(97, lowData.mtg().estimatedBandwidthSavedPercent());
        assertTrue(lowData.reply().length() <= normal.reply().length());
    }

    @Test
    void quizGeneratorCreatesDynamicQuestionsFromRagAndTeacherDocuments() {
        try {
            var batch = dingaController.generateQuizQuestions(new QuizGenerationRequest(
                    "Grounding e RAG no Microsoft Learn",
                    "B1",
                    "05-ingles-tecnico-microsoft-learn-ia",
                    2
            ));
            assertNotNull(batch);
            assertEquals(2, batch.generated().size());
            assertTrue(batch.totalQuestions() >= 10);

            QuizQuestion genQ = batch.generated().getFirst();
            assertTrue(genQ.id().startsWith("quiz-gen-"));
            assertEquals("05-ingles-tecnico-microsoft-learn-ia", genQ.sourceDocId());
            assertEquals(4, genQ.options().size());
            assertFalse(genQ.englishExample().isBlank());

            QuizAnswerResponse checked = dingaController.checkQuizAnswer(
                    new QuizAnswerRequest(genQ.id(), genQ.correctIndex())
            );
            assertTrue(checked.correct());

            // Também gera questão a partir de documento cadastrado ao vivo pelo professor
            var createdDoc = dingaController.addKnowledgeDocument(new NewKnowledgeDocumentRequest(
                    "Git Rebase e Cherry-pick",
                    "B1",
                    "Curadoria Docente ao Vivo",
                    "Prof. USJT",
                    "Vocabulário de Git Rebase",
                    "- **Rebase:** reaplicar commits para manter histórico linear (\"Please rebase your feature branch onto main.\")."
            ));
            var docSummary = (br.edu.usjt.dinga.model.KnowledgeDocumentSummary) createdDoc.get("document");

            var teacherBatch = dingaController.generateQuizQuestions(new QuizGenerationRequest(
                    "Rebase",
                    "B1",
                    docSummary.docId(),
                    1
            ));
            assertEquals(1, teacherBatch.generated().size());
            assertEquals(docSummary.docId(), teacherBatch.generated().getFirst().sourceDocId());

            // Geração com docId explícito + topic preenchido com count=2 deve manter todas as questões estritamente no docId escolhido
            var strictDocBatch = dingaController.generateQuizQuestions(new QuizGenerationRequest(
                    "Grounding, RAG e Hallucination",
                    "B1",
                    "06-ingles-tecnico-cloud-apis-git",
                    2
            ));
            assertEquals(2, strictDocBatch.generated().size());
            assertTrue(strictDocBatch.generated().stream()
                    .allMatch(q -> "06-ingles-tecnico-cloud-apis-git".equals(q.sourceDocId())));

            // Documento com citação apenas em Português (08-metodo-socratico, 09-governanca-etica) NÃO deve gerar englishExample em Português
            var ethicsBatch = dingaController.generateQuizQuestions(new QuizGenerationRequest(
                    "",
                    "B2",
                    "09-governanca-etica-mtg-ia-lgpd-ai-act",
                    1
            ));
            QuizQuestion ethicsQ = ethicsBatch.generated().getFirst();
            assertFalse(ethicsQ.englishExample().toLowerCase().contains("a ia sugere"));
            assertTrue(ethicsQ.englishExample().toLowerCase().contains("responsible ai"));
        } finally {
            quizService.resetGeneratedQuestions();
            knowledgeLoader.resetCustomDocuments();
            ragRetriever.refreshIndex();
        }
        assertEquals(8, quizService.listQuestions(null, null).size());
    }

    @Test
    void minimalistFrontendAssetsLoadAndAllStaticDomBindingsMatch() throws Exception {
        ClassPathResource indexRes = new ClassPathResource("static/index.html");
        ClassPathResource cssRes = new ClassPathResource("static/styles.css");
        ClassPathResource jsRes = new ClassPathResource("static/app.js");

        assertTrue(indexRes.exists());
        assertTrue(cssRes.exists());
        assertTrue(jsRes.exists());

        String html;
        String js;
        try (InputStream in = indexRes.getInputStream()) {
            html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        try (InputStream in = jsRes.getInputStream()) {
            js = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }

        // Verify all static el.* getElementById bindings exist in index.html
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("document\\.getElementById\\(\"([^\"]+)\"\\)")
                .matcher(js);
        java.util.Set<String> dynamicIds = java.util.Set.of("test-new-rag-btn", "gen-quiz-new-rag-btn");
        int checked = 0;
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!dynamicIds.contains(id)) {
                assertTrue(html.contains("id=\"" + id + "\""), "Missing ID in index.html: " + id);
                checked++;
            }
        }
        assertTrue(checked >= 60);
    }

    @Test
    void bm25RetrieverSupportsBilingualSynonymsAndLightStemming() {
        assertEquals("work", Bm25RagRetriever.stemToken("worked"));
        assertEquals("study", Bm25RagRetriever.stemToken("studies"));
        assertEquals("endpoint", Bm25RagRetriever.stemToken("endpoints"));
        assertEquals("bias", Bm25RagRetriever.stemToken("bias"));

        // Consulta puramente em português deve recuperar termos técnicos em inglês via expansão bilíngue
        List<KnowledgeChunk> latencyHits = ragRetriever.retrieve("atraso e lentidao na api", "B1", 3);
        assertFalse(latencyHits.isEmpty());
        assertTrue(latencyHits.stream().anyMatch(c -> c.docId().contains("cloud-apis-git")));

        List<KnowledgeChunk> deployHits = ragRetriever.retrieve("publicar codigo em producao", "B1", 3);
        assertFalse(deployHits.isEmpty());
        assertTrue(deployHits.stream().anyMatch(c -> c.docId().contains("cloud-apis-git")));

        List<KnowledgeChunk> hallucinationHits = ragRetriever.retrieve("como evitar alucinacao com ancoragem", "B1", 3);
        assertFalse(hallucinationHits.isEmpty());
        assertTrue(hallucinationHits.stream().anyMatch(c -> c.docId().contains("microsoft-learn-ia")));

        List<KnowledgeChunk> falseFriendHits = ragRetriever.retrieve("falso amigo em ingles", "A2", 3);
        assertFalse(falseFriendHits.isEmpty());
        assertTrue(falseFriendHits.stream().anyMatch(c -> c.docId().contains("falsos-cognatos")));

        List<KnowledgeChunk> biasHits = ragRetriever.retrieve("vies algorítmico e privacidade", "B2", 3);
        assertFalse(biasHits.isEmpty());
        assertTrue(biasHits.stream().anyMatch(c -> c.docId().contains("mtg-ia")));
    }

    @Test
    void activeGuardrailMasksLgpdPiiAndBlocksAntiPlagiarismRequests() {
        var sanitized = DingaTutorService.sanitizePii(
                "Meu CPF é 123.456.789-00, meu email aluno.teste@usjt.br e telefone (11) 98765-4321."
        );
        assertTrue(sanitized.anyMasked());
        assertFalse(sanitized.sanitizedText().contains("123.456.789-00"));
        assertFalse(sanitized.sanitizedText().contains("aluno.teste@usjt.br"));
        assertFalse(sanitized.sanitizedText().contains("98765-4321"));
        assertTrue(sanitized.sanitizedText().contains("[CPF PROTEGIDO — LGPD]"));
        assertTrue(sanitized.sanitizedText().contains("[E-MAIL PROTEGIDO — LGPD]"));
        assertTrue(sanitized.sanitizedText().contains("[TELEFONE PROTEGIDO — LGPD]"));

        ChatResponse piiResp = tutorService.chat(new ChatRequest(
                "Meu CPF é 123.456.789-00 e email teste@usjt.br, me explica o que é RAG?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertTrue(piiResp.mtg().piiMasked());
        assertFalse(piiResp.mtg().antiPlagiarismTriggered());
        assertTrue(piiResp.reply().contains("Proteção LGPD Ativa"));
        assertFalse(piiResp.reply().contains("123.456.789-00"));
        assertFalse(piiResp.reply().contains("teste@usjt.br"));

        ChatResponse cheatResp = tutorService.chat(new ChatRequest(
                "Faça meu trabalho inteiro de inglês sobre Cloud para eu copiar sem explicar nada",
                "B1",
                "Estudos Acadêmicos",
                "auto",
                false
        ));
        assertTrue(cheatResp.mtg().antiPlagiarismTriggered());
        assertTrue(cheatResp.reply().contains("Guardrail Anti-Plágio Ativo"));
        assertTrue(cheatResp.reply().contains("Passo 1"));

        // Pergunta legítima sobre política anti-plágio não deve disparar falso positivo
        ChatResponse legitimateResp = tutorService.chat(new ChatRequest(
                "Como a Dinga AI aplica MTG-IA e evita plágio na educação?",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertFalse(legitimateResp.mtg().antiPlagiarismTriggered());
    }

    @Test
    void inglesTecnicoSkillAnalyzesRealCodeErrorsAndDocumentationModals() {
        ChatResponse npeResp = tutorService.chat(new ChatRequest(
                "O que significa esse erro: java.lang.NullPointerException: Cannot invoke String.length() because name is null",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertEquals("ingles-tecnico", npeResp.activeSkill().id());
        assertTrue(npeResp.reply().contains("1. Sentido real"));
        assertTrue(npeResp.reply().contains("2. Termos-chave e verbos modais"));
        assertTrue(npeResp.reply().contains("3. O que fazer na prática"));

        ChatResponse gitResp = tutorService.chat(new ChatRequest(
                "Deu erro no terminal: git push rejected (non-fast-forward) failed to push some refs",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertEquals("ingles-tecnico", gitResp.activeSkill().id());
        assertTrue(gitResp.reply().contains("git pull"));

        ChatResponse docResp = tutorService.chat(new ChatRequest(
                "You must set JAVA_HOME before running. You should enable low-data mode. You may customize the port.",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertEquals("ingles-tecnico", docResp.activeSkill().id());
        assertTrue(docResp.reply().contains("`must`"));
        assertTrue(docResp.reply().contains("`should`"));
        assertTrue(docResp.reply().contains("`may`"));
    }

    @Test
    void praticaAtivaSkillDetects15PlusBrazilianGrammarPatternsWithStructuredFeedback() {
        List<String> brazilianErrorSamples = List.of(
                "I have 20 years",
                "Yesterday I work on the API",
                "Yesterday I go to the university",
                "She work every day in Java",
                "He don't like meetings",
                "I have a doubt about this endpoint",
                "It depends of the server configuration",
                "Can you explain me how RAG works?",
                "I want to make a question",
                "I need more informations about cloud",
                "Actually hoje eu estudo APIs",
                "I pretend to learn Spring Boot",
                "I listen music while studying",
                "Yesterday I went to home early",
                "I didn't went to the standup",
                "I study for learn technical English",
                "Is very good for beginners",
                "I am agree with your pull request",
                "Much people use GitHub daily"
        );

        for (String sample : brazilianErrorSamples) {
            var analysis = PraticaAtivaSkill.analyzeBrazilianPatterns(sample);
            assertFalse(analysis.issues().isEmpty(), "Expected pattern detection for: " + sample);
            assertFalse(analysis.naturalSuggestion().isBlank(), "Expected natural suggestion for: " + sample);
        }

        ChatResponse resp = tutorService.chat(new ChatRequest(
                "Corrija: I have a doubt, it depends of the API and yesterday I go to home.",
                "A2",
                "Entrevista e Carreira",
                "auto",
                false
        ));
        assertEquals("pratica-ativa", resp.activeSkill().id());
        assertTrue(resp.reply().contains("O que acertou"));
        assertTrue(resp.reply().contains("O que ajustar"));
        assertTrue(resp.reply().contains("Forma natural"));
    }

    @Test
    void jsonPersistenceSurvivesRestartAndSupportsItemDeletion(@TempDir Path tempDir) {
        JsonDataStore store = new JsonDataStore(tempDir.toString(), objectMapper);

        KnowledgeBaseLoader loader1 = new KnowledgeBaseLoader(store);
        Bm25RagRetriever retriever1 = new Bm25RagRetriever(loader1);
        QuizService quiz1 = new QuizService(loader1, retriever1, hybridLlmClient, objectMapper, store);
        FeedbackReportService feedback1 = new FeedbackReportService(store);

        var createdDoc = loader1.addCustomDocument(new NewKnowledgeDocumentRequest(
                "Docker Compose e Variáveis",
                "B1",
                "Curadoria Docente",
                "Prof. USJT",
                "Containers",
                "- **Container:** ambiente isolado (\"We run the app inside a Docker container.\")."
        ));
        var quizBatch = quiz1.generateQuestions(new QuizGenerationRequest("Container", "B1", createdDoc.docId(), 1));
        String genQuestionId = quizBatch.generated().getFirst().id();
        var report = feedback1.add(new FeedbackReportRequest("DINGA-20261119-PERS", "Sugestão de melhoria", "Persistir JSON"));

        // Simula reinicialização do servidor Java carregando novos componentes a partir da mesma pasta data/
        KnowledgeBaseLoader loader2 = new KnowledgeBaseLoader(store);
        Bm25RagRetriever retriever2 = new Bm25RagRetriever(loader2);
        QuizService quiz2 = new QuizService(loader2, retriever2, hybridLlmClient, objectMapper, store);
        FeedbackReportService feedback2 = new FeedbackReportService(store);

        assertEquals(16, loader2.getDocumentsSummary().size());
        assertTrue(loader2.getDocumentsSummary().stream().anyMatch(d -> d.docId().equals(createdDoc.docId())));
        assertTrue(quiz2.findById(genQuestionId).isPresent());
        assertTrue(feedback2.listAll().stream().anyMatch(r -> r.id().equals(report.id())));

        // Testa remoção persistente dos itens criados
        assertTrue(loader2.removeCustomDocument(createdDoc.docId()));
        assertTrue(quiz2.removeGeneratedQuestion(genQuestionId));
        assertTrue(feedback2.remove(report.id()));

        KnowledgeBaseLoader loader3 = new KnowledgeBaseLoader(store);
        QuizService quiz3 = new QuizService(loader3, new Bm25RagRetriever(loader3), hybridLlmClient, objectMapper, store);
        FeedbackReportService feedback3 = new FeedbackReportService(store);

        assertEquals(15, loader3.getDocumentsSummary().size());
        assertTrue(quiz3.findById(genQuestionId).isEmpty());
        assertTrue(feedback3.listAll().isEmpty());

        // Proteção nos endpoints DELETE do controller: não permite apagar documentos/questões base
        assertThrows(ResponseStatusException.class,
                () -> dingaController.deleteKnowledgeDocument("01-a1-presente-simples-e-apresentacao"));
        assertThrows(ResponseStatusException.class,
                () -> dingaController.deleteQuizQuestion("quiz-rag-grounding"));
        assertThrows(ResponseStatusException.class,
                () -> dingaController.deleteFeedback("RPT-INEXISTENTE"));
    }

    @Test
    void regressionFixesCoverBilingualPluralsGuardrailsCodeErrorsGrammarAndHiddenCss() throws Exception {
        // 1. Stemming + sinônimos bilíngues para "vies" isolado, plurais PT (-os/-as) e "marca d'água"
        assertEquals("vies", Bm25RagRetriever.stemToken("vies"));
        assertTrue(Bm25RagRetriever.expandQueryTerms("vies").containsKey("bias"));
        assertTrue(Bm25RagRetriever.expandQueryTerms("atrasos").containsKey("latency"));
        assertTrue(Bm25RagRetriever.expandQueryTerms("cognatos").containsKey("friend"));
        assertTrue(Bm25RagRetriever.expandQueryTerms("marca d'água").containsKey("watermark"));

        // 2. LGPD: CPF com "é" sem pontuação e variações de telefone BR + anti-plágio com verbos no infinitivo
        var cpfWithAccent = DingaTutorService.sanitizePii("Meu CPF é 12345678900 e fone +5511987654321 ou (11) 98765 4321.");
        assertTrue(cpfWithAccent.anyMasked());
        assertFalse(cpfWithAccent.sanitizedText().contains("12345678900"));
        assertFalse(cpfWithAccent.sanitizedText().contains("987654321"));
        assertFalse(cpfWithAccent.sanitizedText().contains("98765 4321"));
        assertTrue(DingaTutorService.isPlagiarismOrCheatingRequest("Você pode fazer meu trabalho inteiro de inglês?"));
        assertTrue(DingaTutorService.isPlagiarismOrCheatingRequest("Consegue responder minha prova de inglês?"));

        // 3. InglesTecnicoSkill: erro real do Git sem literal "git push", TypeError com linha :400 e must + must not juntos
        ChatResponse rawGitResp = tutorService.chat(new ChatRequest(
                "! [rejected] main -> main (non-fast-forward) error: failed to push some refs",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertTrue(rawGitResp.reply().contains("git pull"));

        ChatResponse typeErrLine400 = tutorService.chat(new ChatRequest(
                "TypeError: Cannot read properties of undefined at app.js:400",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertTrue(typeErrLine400.reply().contains("TypeError"));
        assertFalse(typeErrLine400.reply().contains("HTTP 400"));

        ChatResponse bothModals = tutorService.chat(new ChatRequest(
                "You must set JAVA_HOME before running and you must not commit your API key.",
                "B1",
                "Inglês para Tecnologia / Microsoft Learn",
                "auto",
                false
        ));
        assertTrue(bothModals.reply().contains("`must not`"));
        assertTrue(bothModals.reply().contains("`must`"));

        // 4. PraticaAtivaSkill: zero falsos positivos em daily válida / infinitivo e correção natural fiel
        var validDaily = PraticaAtivaSkill.analyzeBrazilianPatterns("Yesterday I worked on the API, and today I will push my code.");
        assertTrue(validDaily.issues().isEmpty(), "Valid daily should not trigger false positives: " + validDaily.issues());

        var validInfinitive = PraticaAtivaSkill.analyzeBrazilianPatterns("Yesterday I wanted to go home.");
        assertTrue(validInfinitive.issues().isEmpty(), "Infinitive 'to go' should not trigger past trap: " + validInfinitive.issues());

        var multiPast = PraticaAtivaSkill.analyzeBrazilianPatterns("Yesterday I go to the lab and buy a book");
        assertTrue(multiPast.naturalSuggestion().contains("went"));
        assertTrue(multiPast.naturalSuggestion().contains("bought"));

        var weAgree = PraticaAtivaSkill.analyzeBrazilianPatterns("We are agree with the plan");
        assertEquals("We agree with the plan.", weAgree.naturalSuggestion());

        var sheAgree = PraticaAtivaSkill.analyzeBrazilianPatterns("She is agree with us");
        assertEquals("She agrees with us.", sheAgree.naturalSuggestion());

        var shePretends = PraticaAtivaSkill.analyzeBrazilianPatterns("She pretends to learn Java");
        assertEquals("She plans to learn Java.", shePretends.naturalSuggestion());

        var makingQ = PraticaAtivaSkill.analyzeBrazilianPatterns("I am making a question");
        assertEquals("I am asking a question.", makingQ.naturalSuggestion());

        var doublePast = PraticaAtivaSkill.analyzeBrazilianPatterns("I didn't studied yesterday");
        assertEquals("I didn't study yesterday.", doublePast.naturalSuggestion());

        var pastThirdPerson = PraticaAtivaSkill.analyzeBrazilianPatterns("Yesterday he work on the API");
        assertEquals(1, pastThirdPerson.issues().size());
        assertTrue(pastThirdPerson.naturalSuggestion().contains("worked"));

        // 5. CSS: garante que [hidden] não seja sobrescrito por display:flex em .adaptive-banner / .challenge-banner
        ClassPathResource cssRes = new ClassPathResource("static/styles.css");
        try (InputStream in = cssRes.getInputStream()) {
            String css = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(css.contains("[hidden]") && css.contains("display: none !important;"));
        }
    }

    @Test
    void ttsEndpointSynthesizesValidAudioAndRejectsBlankText() {
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> dingaController.synthesizeSpeechPost(new br.edu.usjt.dinga.model.TtsRequest("   ", "en-US", true)));

        var enResp = dingaController.synthesizeSpeechPost(
                new br.edu.usjt.dinga.model.TtsRequest("Actually, I study technology every night.", "en-US", true)
        );
        assertEquals(200, enResp.getStatusCode().value());
        assertNotNull(enResp.getBody());
        assertTrue(enResp.getBody().length > 1000);
        String enType = String.valueOf(enResp.getHeaders().getContentType());
        assertTrue(enType.startsWith("audio/wav") || enType.startsWith("audio/mpeg"));

        var ptResp = dingaController.synthesizeSpeechGet("Olá! Vamos praticar inglês técnico.", "pt-BR", true);
        assertEquals(200, ptResp.getStatusCode().value());
        assertNotNull(ptResp.getBody());
        assertTrue(ptResp.getBody().length > 1000);
    }
}
