package br.edu.usjt.dinga.controller;

import br.edu.usjt.dinga.config.LlmProperties;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatResponse;
import br.edu.usjt.dinga.model.FeedbackReport;
import br.edu.usjt.dinga.model.FeedbackReportRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.KnowledgeDocumentSummary;
import br.edu.usjt.dinga.model.NewKnowledgeDocumentRequest;
import br.edu.usjt.dinga.model.QuizAnswerRequest;
import br.edu.usjt.dinga.model.QuizAnswerResponse;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.QuizQuestion;
import br.edu.usjt.dinga.model.SimulationRequest;
import br.edu.usjt.dinga.model.SimulationResponse;
import br.edu.usjt.dinga.model.SkillDefinition;
import br.edu.usjt.dinga.model.TtsRequest;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.service.DingaTutorService;
import br.edu.usjt.dinga.service.FeedbackReportService;
import br.edu.usjt.dinga.service.QuizService;
import br.edu.usjt.dinga.service.SessionMetricsService;
import br.edu.usjt.dinga.service.SimulatorService;
import br.edu.usjt.dinga.service.TtsService;
import br.edu.usjt.dinga.skills.SkillRouter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API REST do MVP Dinga AI.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Dinga AI", description = "Tutor Socrático, Simulador Daily/Entrevista, Quiz RAG + Gemini 3.8 Flash, Curadoria Docente, TTS e Governança MTG-IA")
public class DingaController {

    private static final int MAX_CHAT_MESSAGE_CHARS = 2000;
    private static final int MAX_CHAT_HISTORY_TURNS = 20;
    private static final int MAX_SIMULATOR_HISTORY_TURNS = 10;
    private static final int MAX_TTS_CHARS = 1500;
    private static final int MAX_QUIZ_TOPIC_CHARS = 200;
    private static final int MAX_DOC_TITLE_CHARS = 160;
    private static final int MAX_DOC_CONTENT_CHARS = 40_000;
    private static final long MAX_UPLOAD_BYTES = 512L * 1024L;

    private final DingaTutorService tutorService;
    private final SkillRouter skillRouter;
    private final KnowledgeBaseLoader knowledgeLoader;
    private final Bm25RagRetriever ragRetriever;
    private final FeedbackReportService feedbackService;
    private final QuizService quizService;
    private final TtsService ttsService;
    private final SimulatorService simulatorService;
    private final SessionMetricsService metricsService;
    private final LlmProperties llmProperties;

    public DingaController(
            DingaTutorService tutorService,
            SkillRouter skillRouter,
            KnowledgeBaseLoader knowledgeLoader,
            Bm25RagRetriever ragRetriever,
            FeedbackReportService feedbackService,
            QuizService quizService,
            TtsService ttsService,
            SimulatorService simulatorService,
            SessionMetricsService metricsService,
            LlmProperties llmProperties
    ) {
        this.tutorService = tutorService;
        this.skillRouter = skillRouter;
        this.knowledgeLoader = knowledgeLoader;
        this.ragRetriever = ragRetriever;
        this.feedbackService = feedbackService;
        this.quizService = quizService;
        this.ttsService = ttsService;
        this.simulatorService = simulatorService;
        this.metricsService = metricsService;
        this.llmProperties = llmProperties;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "UP",
                "app", "Dinga AI",
                "skillsCount", skillRouter.listDefinitions().size(),
                "documentsCount", knowledgeLoader.getDocumentsSummary().size(),
                "chunksCount", knowledgeLoader.getChunks().size(),
                "quizQuestionsCount", quizService.listQuestions(null, null).size(),
                "llmMode", llmProperties.resolvedProviderName()
        );
    }

    @GetMapping("/meta")
    public Map<String, Object> meta() {
        List<SkillDefinition> skills = skillRouter.listDefinitions();
        List<KnowledgeDocumentSummary> docs = knowledgeLoader.getDocumentsSummary();
        return Map.of(
                "projectName", "Dinga AI — Educação de Idiomas",
                "challenge", "Caso 3 – Educação personalizada com IA na plataforma Microsoft Learn",
                "institution", "Universidade São Judas Tadeu (USJT)",
                "course", "UC Dual: Inteligência Artificial e Sociedade: soluções e desafios para o futuro",
                "llmProvider", llmProperties.resolvedProviderName(),
                "remoteLlmEnabled", llmProperties.isRemoteLlmConfigured(),
                "skills", skills,
                "documents", docs,
                "chunksCount", knowledgeLoader.getChunks().size(),
                "quizCount", quizService.listQuestions(null, null).size()
        );
    }

    @Operation(summary = "Chat com o Tutor Dinga AI", description = "Aplica Guardrails LGPD e Anti-Plágio, roteia Skill, recupera RAG BM25 e responde via Gemini 3.8 Flash ou fallback local.")
    @PostMapping("/chat")
    public ChatResponse chat(@RequestBody ChatRequest request) {
        validateChatRequest(request);
        return tutorService.chat(request);
    }

    @Operation(
            summary = "Chat em Streaming (SSE) com o Tutor Dinga AI",
            description = "Entrega a resposta progressivamente via Server-Sent Events (eventos 'token' e 'done') usando Gemini 3.8 Flash com fallback transparente para RAG local."
    )
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chatStream(@RequestBody ChatRequest request) {
        validateChatRequest(request);
        SseEmitter emitter = new SseEmitter(60_000L);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> closed.set(true));
        emitter.onTimeout(() -> {
            closed.set(true);
            emitter.complete();
        });
        emitter.onError(ex -> closed.set(true));

        Thread.startVirtualThread(() -> {
            try {
                ChatResponse finalResponse = tutorService.chatStream(request, delta -> {
                    if (closed.get() || delta == null || delta.isEmpty()) {
                        return;
                    }
                    try {
                        emitter.send(SseEmitter.event()
                                .name("token")
                                .data(Map.of("delta", delta), MediaType.APPLICATION_JSON));
                    } catch (IOException | IllegalStateException ex) {
                        closed.set(true);
                    }
                });
                if (!closed.get()) {
                    emitter.send(SseEmitter.event()
                            .name("done")
                            .data(finalResponse, MediaType.APPLICATION_JSON));
                    emitter.complete();
                }
            } catch (Exception ex) {
                if (!closed.get()) {
                    emitter.completeWithError(ex);
                }
            }
        });
        return emitter;
    }

    private static void validateChatRequest(ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A mensagem não pode estar vazia.");
        }
        if (request.message().length() > MAX_CHAT_MESSAGE_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A mensagem excede o limite máximo de " + MAX_CHAT_MESSAGE_CHARS + " caracteres."
            );
        }
        if (request.history() != null && request.history().size() > MAX_CHAT_HISTORY_TURNS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Histórico de conversa excede o máximo permitido (" + MAX_CHAT_HISTORY_TURNS + " turnos)."
            );
        }
    }

    @Operation(summary = "Iniciar Simulador Daily Standup / Entrevista Técnica", description = "Inicia uma rodada guiada multi-turno (3 etapas Daily ou 4 etapas Entrevista).")
    @PostMapping("/simulator/start")
    public SimulationResponse startSimulation(@RequestBody(required = false) SimulationRequest request) {
        String mode = request == null ? "daily" : request.mode();
        String level = request == null ? "B1" : request.level();
        return simulatorService.start(mode, level);
    }

    @Operation(summary = "Enviar resposta no Simulador", description = "Avalia a resposta do turno via Gemini 3.8 Flash (com fallback local) e entrega o mini-boletim na última etapa.")
    @PostMapping("/simulator/answer")
    public SimulationResponse answerSimulation(@RequestBody SimulationRequest request) {
        if (request == null || request.answer().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Escreva sua resposta em inglês para avançar no simulador.");
        }
        if (request.answer().length() > 1500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Resposta excede o limite de 1500 caracteres.");
        }
        if (request.history() != null && request.history().size() > MAX_SIMULATOR_HISTORY_TURNS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Histórico do simulador excede o limite permitido.");
        }
        return simulatorService.submitAnswer(request);
    }

    @Operation(summary = "Métricas da Sessão e Governança MTG-IA", description = "Retorna indicadores agregados sem dados pessoais (LGPD): acertos no quiz, skills mais acionadas, guardrails e economia de dados.")
    @GetMapping("/metrics")
    public SessionMetricsService.SessionMetricsSnapshot metrics() {
        return metricsService.snapshot();
    }

    @GetMapping("/knowledge")
    public Map<String, Object> knowledge() {
        List<KnowledgeDocumentSummary> docs = knowledgeLoader.getDocumentsSummary();
        List<KnowledgeChunk> chunks = knowledgeLoader.getChunks();
        return Map.of(
                "documents", docs,
                "chunks", chunks
        );
    }

    @Operation(summary = "Cadastrar novo documento RAG manualmente")
    @PostMapping("/knowledge")
    public Map<String, Object> addKnowledgeDocument(@RequestBody NewKnowledgeDocumentRequest request) {
        if (request == null || request.title().isBlank() || request.content().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o título e o conteúdo didático do novo documento RAG.");
        }
        if (request.title().length() > MAX_DOC_TITLE_CHARS || request.content().length() > MAX_DOC_CONTENT_CHARS) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Título ou conteúdo excede o tamanho máximo permitido.");
        }
        KnowledgeDocumentSummary created = knowledgeLoader.addCustomDocument(request);
        ragRetriever.refreshIndex();
        String suggestedPrompt = "Me explique sobre " + created.title() + " (" + request.section() + ")";
        return Map.of(
                "document", created,
                "totalDocuments", knowledgeLoader.getDocumentsSummary().size(),
                "totalChunks", knowledgeLoader.getChunks().size(),
                "suggestedPrompt", suggestedPrompt
        );
    }

    @Operation(summary = "Importar arquivo .md ou .txt para a base RAG (Painel Docente)", description = "Particiona automaticamente por seções '## ' e indexa imediatamente no motor BM25.")
    @PostMapping(value = "/knowledge/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> uploadKnowledgeFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sourceRef
    ) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecione um arquivo .md ou .txt não vazio.");
        }
        if (file.getSize() > MAX_UPLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo excede o limite máximo de 512 KB.");
        }
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().trim();
        String lowerName = originalName.toLowerCase(Locale.ROOT);
        if (!lowerName.endsWith(".md") && !lowerName.endsWith(".txt")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Formato inválido. Envie apenas arquivos .md ou .txt.");
        }

        String rawContent;
        try {
            rawContent = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Não foi possível ler o arquivo enviado.");
        }
        if (rawContent.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O arquivo enviado não possui texto indexável.");
        }

        KnowledgeDocumentSummary created = knowledgeLoader.addUploadedDocument(
                originalName,
                rawContent,
                title,
                level,
                category,
                sourceRef
        );
        ragRetriever.refreshIndex();
        String suggestedPrompt = "Me explique os principais conceitos e exemplos em inglês de: " + created.title();
        return Map.of(
                "document", created,
                "totalDocuments", knowledgeLoader.getDocumentsSummary().size(),
                "totalChunks", knowledgeLoader.getChunks().size(),
                "suggestedPrompt", suggestedPrompt
        );
    }

    @DeleteMapping("/knowledge/{docId}")
    public Map<String, Object> deleteKnowledgeDocument(@PathVariable String docId) {
        if (knowledgeLoader.isBaseDocument(docId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Documentos curados da base oficial não podem ser removidos.");
        }
        boolean removed = knowledgeLoader.removeCustomDocument(docId);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Documento customizado não encontrado: " + docId);
        }
        ragRetriever.refreshIndex();
        return Map.of(
                "deleted", docId,
                "totalDocuments", knowledgeLoader.getDocumentsSummary().size(),
                "totalChunks", knowledgeLoader.getChunks().size()
        );
    }

    @GetMapping("/quiz")
    public List<QuizQuestion> listQuizQuestions(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String category
    ) {
        return quizService.listQuestions(level, category);
    }

    @Operation(summary = "Gerar novas questões de Quiz (Gemini 3.8 Flash + fallback RAG local)")
    @PostMapping("/quiz/generate")
    public QuizService.GeneratedQuizBatch generateQuizQuestions(
            @RequestBody(required = false) QuizGenerationRequest request
    ) {
        if (request != null && request.topic().length() > MAX_QUIZ_TOPIC_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O tema do quiz excede o limite de " + MAX_QUIZ_TOPIC_CHARS + " caracteres."
            );
        }
        return quizService.generateQuestions(request);
    }

    @DeleteMapping("/quiz/{questionId}")
    public Map<String, Object> deleteQuizQuestion(@PathVariable String questionId) {
        if (quizService.isBaseQuestion(questionId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Questões oficiais da base curada não podem ser removidas.");
        }
        boolean removed = quizService.removeGeneratedQuestion(questionId);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Questão gerada não encontrada: " + questionId);
        }
        return Map.of(
                "deleted", questionId,
                "totalQuestions", quizService.listQuestions(null, null).size()
        );
    }

    @PostMapping("/quiz/answer")
    public QuizAnswerResponse checkQuizAnswer(@RequestBody QuizAnswerRequest request) {
        if (request == null || request.questionId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o ID da questão do quiz.");
        }
        try {
            return quizService.checkAnswer(request);
        } catch (IllegalArgumentException ex) {
            if (ex.getMessage() != null && ex.getMessage().startsWith("Índice de alternativa inválido")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage());
        }
    }

    @PostMapping("/feedback")
    public FeedbackReport submitFeedback(@RequestBody FeedbackReportRequest request) {
        if (request == null || request.comment() == null || request.comment().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descreva o erro ou viés observado.");
        }
        return feedbackService.add(request);
    }

    @GetMapping("/feedback")
    public List<FeedbackReport> listFeedback() {
        return feedbackService.listAll();
    }

    @DeleteMapping("/feedback/{reportId}")
    public Map<String, Object> deleteFeedback(@PathVariable String reportId) {
        boolean removed = feedbackService.remove(reportId);
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Relato de auditoria não encontrado: " + reportId);
        }
        return Map.of(
                "deleted", reportId,
                "remaining", feedbackService.listAll().size()
        );
    }

    @Operation(summary = "Sintetizar voz (TTS híbrido online + eSpeak-NG offline)")
    @PostMapping("/tts")
    public ResponseEntity<byte[]> synthesizeSpeechPost(@RequestBody TtsRequest request) {
        if (request == null || request.text().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o texto para leitura em voz alta.");
        }
        if (request.text().length() > MAX_TTS_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O texto para síntese de voz excede o limite de " + MAX_TTS_CHARS + " caracteres."
            );
        }
        return buildTtsResponse(request.text(), request.lang(), Boolean.TRUE.equals(request.offline()));
    }

    @Operation(summary = "Sintetizar voz via GET")
    @GetMapping("/tts")
    public ResponseEntity<byte[]> synthesizeSpeechGet(
            @RequestParam String text,
            @RequestParam(defaultValue = "en-US") String lang,
            @RequestParam(defaultValue = "false") boolean offline
    ) {
        if (text == null || text.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o texto para leitura em voz alta.");
        }
        if (text.length() > MAX_TTS_CHARS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "O texto para síntese de voz excede o limite de " + MAX_TTS_CHARS + " caracteres."
            );
        }
        return buildTtsResponse(text, lang, offline);
    }

    private ResponseEntity<byte[]> buildTtsResponse(String text, String lang, boolean offline) {
        try {
            TtsService.TtsAudio audio = ttsService.synthesize(text, lang, offline);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(audio.contentType()))
                    .cacheControl(CacheControl.maxAge(5, TimeUnit.MINUTES).cachePrivate())
                    .header("X-Dinga-TTS-Engine", audio.engine())
                    .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(audio.bytes().length))
                    .body(audio.bytes());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, ex.getMessage());
        } catch (IllegalStateException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        }
    }
}
