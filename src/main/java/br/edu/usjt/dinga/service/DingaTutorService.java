package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatResponse;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.LearnerState;
import br.edu.usjt.dinga.model.LearnerStateDelta;
import br.edu.usjt.dinga.model.MtgMetadata;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.skills.PraticaAtivaSkill;
import br.edu.usjt.dinga.skills.SkillRouter;
import br.edu.usjt.dinga.skills.TeacherSkill;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Orquestrador central da Dinga AI:
 * Guardrail Ativo LGPD & Anti-Plágio -> Roteador de Skills -> Motor RAG -> LLM Híbrido / Fallback -> Camada MTG-IA.
 */
@Service
public class DingaTutorService {

    private static final DateTimeFormatter WATERMARK_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    private static final Pattern AVOID_SEGMENT = Pattern.compile("❌[^✅\\n]*");
    private static final Pattern QUOTED_PHRASE = Pattern.compile("\"([^\"\\n]{4,160})\"|'([^'\\n]{4,160})'");
    private static final Pattern PT_DIACRITICS = Pattern.compile("[ãõçáéíóúâêôà]", Pattern.CASE_INSENSITIVE);
    private static final Pattern PT_WORDS = Pattern.compile(
            "\\b(nao|voce|atualmente|na verdade|livraria|pretender|parentes|atender|suportar|aguentar|tenho|anos|fazer uma pergunta|sugere|valida|assistir|cliente|incontavel|significa|diferenca|ontem|hoje)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern EN_WORDS = Pattern.compile(
            "\\b(i|you|he|she|it|we|they|am|is|are|was|were|do|does|did|have|has|will|would|could|should|can|must|the|and|but|while|before|after|under|through|every|more|than|without|in|on|at|to|for|with|from|into|yesterday|today|actually|currently|nowadays|pretend|intend|library|attend|support|parents|study|studied|worked|work|learning|student|students|question|information|feedback|blocker|endpoint|payload|grounding|hallucination|push|pull|daily|api|review|documentation|null|exception|error|branch|commit|avoid|remove|means|includes|supports|reduces|builds|turns|produce|beats|models|answers|sources|practice|safety)\\b",
            Pattern.CASE_INSENSITIVE
    );

    // Guardrail LGPD: detecção e mascaramento automático de CPF, e-mail e telefone antes do LLM externo
    private static final Pattern CPF_FORMATTED_PATTERN = Pattern.compile("\\b\\d{3}\\.\\d{3}\\.\\d{3}-\\d{2}\\b");
    private static final Pattern CPF_LABELED_PATTERN = Pattern.compile("(?i)(cpf\\s*(?:[:=-]|e|é|eh|numero|número)?\\s*)\\d{11}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b");
    private static final Pattern PHONE_BR_PATTERN = Pattern.compile(
            "(?:\\+55\\s?(?:\\(\\d{2}\\)\\s?|[1-9]{2}\\s?)|(?:\\(\\d{2}\\)\\s?|\\b[1-9]{2}\\s))(?:9\\d{4}|\\d{4})[\\s-]?\\d{4}\\b"
                    + "|\\b[1-9]{2}(?:9\\d{4}|\\d{4})-\\d{4}\\b"
    );

    // Guardrail Anti-Plágio / Anti-Cola (MTG-IA): pedidos para fazer trabalho/prova inteira para copiar
    private static final Pattern ANTI_PLAGIARISM_PATTERN = Pattern.compile(
            "\\b(faca|faz|fazer|resolva|resolve|resolver|responda|responder|escreva|escrever|gera|gere|gerar|monte|monta|montar|crie|cria|criar|complete|completar)\\b.{0,35}\\b(trabalho inteiro|prova inteira|redacao inteira|atividade inteira|tcc inteiro|meu trabalho todo|minha prova toda|toda a prova|todo o trabalho|meu trabalho para eu copiar|minha prova para eu copiar|minha redacao completa|meu tcc|minha prova|meu trabalho)\\b"
                    + "|\\b(me de|me da|manda|passa|quero)\\b.{0,25}\\b(cola|gabarito|respostas? prontas?|trabalho pronto|prova pronta|redacao pronta)\\b"
                    + "|\\b(para eu copiar|so copiar|copiar e colar sem|fazer por mim|do my homework|write my entire essay|solve my entire exam|give me the answers to copy)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final SkillRouter skillRouter;
    private final Bm25RagRetriever ragRetriever;
    private final HybridLlmClient llmClient;
    private final SessionMetricsService metricsService;

    public DingaTutorService(
            SkillRouter skillRouter,
            Bm25RagRetriever ragRetriever,
            HybridLlmClient llmClient
    ) {
        this(skillRouter, ragRetriever, llmClient, null);
    }

    @Autowired
    public DingaTutorService(
            SkillRouter skillRouter,
            Bm25RagRetriever ragRetriever,
            HybridLlmClient llmClient,
            SessionMetricsService metricsService
    ) {
        this.skillRouter = skillRouter;
        this.ragRetriever = ragRetriever;
        this.llmClient = llmClient;
        this.metricsService = metricsService;
    }

    public ChatResponse chat(ChatRequest request) {
        return executeChat(request, null);
    }

    /**
     * Executa o fluxo completo do Tutor emitindo os fragmentos de texto progressivamente (SSE),
     * tanto via Gemini 3.8 Flash quanto via fallback RAG Local.
     */
    public ChatResponse chatStream(ChatRequest request, Consumer<String> onToken) {
        return executeChat(request, onToken != null ? onToken : chunk -> {});
    }

    private ChatResponse executeChat(ChatRequest request, Consumer<String> onToken) {
        long startNanos = System.nanoTime();
        boolean streaming = onToken != null;

        // 1. Guardrail LGPD: mascara CPF, e-mail e telefone ANTES de qualquer chamada a LLM ou RAG
        PiiSanitizationResult sanitizedMsg = sanitizePii(request.message());
        List<ChatTurn> sanitizedHistory = sanitizeHistory(request.history());
        LearnerState sanitizedLearnerState = request.learnerState() == null
                ? LearnerState.empty()
                : request.learnerState().sanitized(text -> sanitizePii(text).sanitizedText());
        ChatRequest safeRequest = new ChatRequest(
                sanitizedMsg.sanitizedText(),
                request.level(),
                request.goal(),
                request.preferredSkill(),
                request.lowDataMode(),
                sanitizedHistory,
                sanitizedLearnerState
        );

        boolean piiMasked = sanitizedMsg.anyMasked();
        String piiNotice = piiMasked ? buildPiiNotice(sanitizedMsg.maskedTypes()) : "";

        // 2. Guardrail Anti-Plágio (MTG-IA): redireciona pedidos de cola/trabalho pronto para orientação socrática
        if (isPlagiarismOrCheatingRequest(safeRequest.message())) {
            ChatResponse antiPlagiarism = buildAntiPlagiarismResponse(
                    safeRequest, piiMasked, sanitizedMsg.maskedTypes(), piiNotice, startNanos
            );
            if (streaming) {
                emitChunked(antiPlagiarism.reply(), onToken);
            }
            return antiPlagiarism;
        }

        // 3. Roteamento de Skill + Recuperação RAG
        SkillRouter.RoutingDecision decision = skillRouter.route(safeRequest);
        TeacherSkill skill = decision.skill();

        final List<KnowledgeChunk> sources = ragRetriever.retrieve(
                safeRequest.message(),
                safeRequest.level(),
                3,
                safeRequest.history(),
                safeRequest.lowDataMode()
        );

        String systemPrompt = skill.buildSystemPrompt(safeRequest, sources);
        HybridLlmClient.GenerationOptions decodingOptions = skill.generationOptions();

        if (streaming && piiMasked) {
            onToken.accept(piiNotice + "\n\n");
        }

        Optional<HybridLlmClient.LlmResult> remoteResult;
        PiiSafeStreamConsumer safeStreamConsumer = streaming ? new PiiSafeStreamConsumer(onToken) : null;
        if (streaming) {
            remoteResult = llmClient.supportsGenerationOptions()
                    ? llmClient.streamGenerate(systemPrompt, safeRequest.message(), safeRequest.history(), decodingOptions, safeStreamConsumer)
                    : llmClient.streamGenerate(systemPrompt, safeRequest.message(), safeRequest.history(), safeStreamConsumer);
            safeStreamConsumer.flush();
        } else {
            remoteResult = llmClient.supportsGenerationOptions()
                    ? llmClient.generate(systemPrompt, safeRequest.message(), safeRequest.history(), decodingOptions)
                    : llmClient.generate(systemPrompt, safeRequest.message(), safeRequest.history());
        }

        String rawReply = remoteResult.isPresent()
                ? remoteResult.get().text()
                : skill.buildLocalResponse(safeRequest, sources);

        // 4. Verificador Pós-Geração (0 ms): ancoragem RAG (groundednessScore) + checagem de saída LGPD/PII
        GroundednessVerifier.VerificationResult verification = GroundednessVerifier.verify(rawReply, sources);
        String verifiedReply = verification.sanitizedReply();
        boolean effectivePiiMasked = piiMasked || verification.outputPiiMasked();

        if (streaming && remoteResult.isEmpty()) {
            emitChunked(verifiedReply, onToken);
        }

        String reply = piiMasked ? piiNotice + "\n\n" + verifiedReply : verifiedReply;

        String executionMode = remoteResult
                .map(res -> "IA Generativa + RAG (" + res.providerLabel() + ")")
                .orElse("RAG Local (Modo Baixa Conectividade)");

        String practicePrompt = skill.buildPracticePrompt(safeRequest, sources);
        List<String> englishExamples = extractEnglishExamples(reply, sources);
        LearnerStateDelta learnerStateDelta = computeLearnerStateDelta(safeRequest, verifiedReply, sources, skill);

        long elapsedMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        double payloadSizeKb = estimatePayloadSizeKb(reply, practicePrompt, sources);
        int bandwidthSavedPercent = safeRequest.lowDataMode() ? 97 : 92;

        String privacyNote;
        if (piiMasked && verification.outputPiiMasked()) {
            Set<String> allMaskedTypes = new LinkedHashSet<>(sanitizedMsg.maskedTypes());
            allMaskedTypes.addAll(verification.outputMaskedTypes());
            privacyNote = "LGPD Ativa (Entrada + Saída): dados pessoais (" + String.join(", ", allMaskedTypes)
                    + ") mascarados na entrada e saída verificada automaticamente.";
        } else if (piiMasked) {
            privacyNote = "LGPD Ativa: dados pessoais (" + String.join(", ", sanitizedMsg.maskedTypes())
                    + ") foram detectados e mascarados automaticamente antes do processamento.";
        } else if (verification.outputPiiMasked()) {
            privacyNote = "LGPD Ativa (Saída): dados pessoais (" + String.join(", ", verification.outputMaskedTypes())
                    + ") detectados na saída foram mascarados automaticamente pós-geração.";
        } else {
            privacyNote = "LGPD: nenhum dado pessoal sensível ou histórico de conversa é persistido no servidor.";
        }

        MtgMetadata mtg = new MtgMetadata(
                "Conteúdo gerado/assistido por IA — Dinga AI",
                generateWatermarkId(),
                executionMode,
                sources.size(),
                "A IA sugere, você valida: confira as fontes curadas do RAG e compartilhe dúvidas com seu professor.",
                privacyNote,
                elapsedMs,
                payloadSizeKb,
                bandwidthSavedPercent,
                safeRequest.lowDataMode(),
                effectivePiiMasked,
                false,
                verification.groundednessScore()
        );

        if (metricsService != null) {
            metricsService.recordChatTurn(
                    skill.definition().id(),
                    safeRequest.lowDataMode(),
                    payloadSizeKb,
                    effectivePiiMasked,
                    false
            );
        }

        return new ChatResponse(
                reply,
                skill.definition(),
                decision.reasoning(),
                sources,
                practicePrompt,
                mtg,
                englishExamples,
                learnerStateDelta
        );
    }

    private static void emitChunked(String text, Consumer<String> onToken) {
        if (text == null || text.isEmpty() || onToken == null) {
            return;
        }
        int chunkSize = 48;
        int len = text.length();
        int idx = 0;
        while (idx < len) {
            int end = Math.min(len, idx + chunkSize);
            if (end < len) {
                int space = text.lastIndexOf(' ', end);
                if (space > idx + 12) {
                    end = space + 1;
                }
            }
            onToken.accept(text.substring(idx, end));
            idx = end;
        }
    }

    private ChatResponse buildAntiPlagiarismResponse(
            ChatRequest safeRequest,
            boolean piiMasked,
            List<String> maskedTypes,
            String piiNotice,
            long startNanos
    ) {
        SkillRouter.RoutingDecision decision = skillRouter.route(new ChatRequest(
                safeRequest.message(),
                safeRequest.level(),
                safeRequest.goal(),
                "guardiao-etico-mtg",
                safeRequest.lowDataMode(),
                safeRequest.history()
        ));
        List<KnowledgeChunk> sources = ragRetriever.retrieve("metodo socratico plagio etica mtg autonomia", safeRequest.level(), 2);

        StringBuilder reply = new StringBuilder();
        if (piiMasked) {
            reply.append(piiNotice).append("\n\n");
        }
        reply.append("### Guardrail Anti-Plágio Ativo (MTG-IA — Método Socrático)\n\n");
        reply.append("Eu **não gero trabalhos, redações ou provas inteiras prontas para copiar**, porque isso fere a integridade acadêmica e impede você de dominar o inglês de verdade.\n\n");
        reply.append("Em vez disso, posso guiar você **passo a passo** para construir a sua própria entrega com autonomia:\n");
        reply.append("- **Passo 1 — Entender o enunciado:** qual é o tema principal ou a pergunta central da sua atividade?\n");
        reply.append("- **Passo 2 — Suas ideias primeiro:** escreva 2 ou 3 tópicos curtos (pode ser em português) com o que você quer defender.\n");
        reply.append("- **Passo 3 — Construção e revisão ativa:** você escreve a primeira frase em inglês e eu reviso gramática, vocabulário e fluência junto com você!\n\n");
        reply.append("Exemplo de frase inicial em inglês para começar com autoria: *\"In this assignment, I will explain the main concept in my own words.\"*");

        String practicePrompt = "Envie o tema da sua atividade e 1 frase inicial escrita por você (em português ou inglês) para começarmos o Passo 1 juntos!";
        List<String> englishExamples = extractEnglishExamples(reply.toString(), sources);

        long elapsedMs = Math.max(1L, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos));
        double payloadSizeKb = estimatePayloadSizeKb(reply.toString(), practicePrompt, sources);
        int bandwidthSavedPercent = safeRequest.lowDataMode() ? 97 : 92;

        String privacyNote = piiMasked
                ? "LGPD + Anti-Plágio Ativos: dados pessoais (" + String.join(", ", maskedTypes)
                        + ") mascarados e solicitação redirecionada para orientação socrática."
                : "MTG-IA Ativo: solicitação de resposta pronta para copiar foi redirecionada para tutoria socrática passo a passo.";

        double antiPlagiarismGroundedness = GroundednessVerifier.computeGroundednessScore(reply.toString(), sources);
        MtgMetadata mtg = new MtgMetadata(
                "Conteúdo assistido por IA com Guardrail MTG-IA — Dinga AI",
                generateWatermarkId(),
                "Guardrail Ético MTG-IA (Redirecionamento Socrático)",
                sources.size(),
                "A IA sugere, você constrói e valida: use a tutoria passo a passo para aprender sem plágio.",
                privacyNote,
                elapsedMs,
                payloadSizeKb,
                bandwidthSavedPercent,
                safeRequest.lowDataMode(),
                piiMasked,
                true,
                antiPlagiarismGroundedness
        );

        if (metricsService != null) {
            metricsService.recordChatTurn(
                    decision.skill().definition().id(),
                    safeRequest.lowDataMode(),
                    payloadSizeKb,
                    piiMasked,
                    true
            );
        }

        return new ChatResponse(
                reply.toString(),
                decision.skill().definition(),
                "Guardrail Anti-Plágio MTG-IA acionado: redirecionamento para método socrático passo a passo.",
                sources,
                practicePrompt,
                mtg,
                englishExamples
        );
    }

    /**
     * Detecta e mascara automaticamente CPF, e-mail e telefone brasileiro antes de qualquer envio ao LLM externo.
     */
    public static PiiSanitizationResult sanitizePii(String input) {
        if (input == null || input.isBlank()) {
            return new PiiSanitizationResult(input == null ? "" : input, List.of());
        }
        String sanitized = input;
        List<String> maskedTypes = new ArrayList<>();

        Matcher emailMatcher = EMAIL_PATTERN.matcher(sanitized);
        if (emailMatcher.find()) {
            sanitized = emailMatcher.replaceAll("[E-MAIL PROTEGIDO — LGPD]");
            maskedTypes.add("e-mail");
        }

        boolean cpfFound = false;
        Matcher cpfFormattedMatcher = CPF_FORMATTED_PATTERN.matcher(sanitized);
        if (cpfFormattedMatcher.find()) {
            sanitized = cpfFormattedMatcher.replaceAll("[CPF PROTEGIDO — LGPD]");
            cpfFound = true;
        }
        Matcher cpfLabeledMatcher = CPF_LABELED_PATTERN.matcher(sanitized);
        if (cpfLabeledMatcher.find()) {
            sanitized = cpfLabeledMatcher.replaceAll("$1[CPF PROTEGIDO — LGPD]");
            cpfFound = true;
        }
        if (cpfFound) {
            maskedTypes.add("CPF");
        }

        Matcher phoneMatcher = PHONE_BR_PATTERN.matcher(sanitized);
        if (phoneMatcher.find()) {
            sanitized = phoneMatcher.replaceAll("[TELEFONE PROTEGIDO — LGPD]");
            maskedTypes.add("telefone");
        }

        return new PiiSanitizationResult(sanitized, List.copyOf(maskedTypes));
    }

    /**
     * Detecta pedidos explícitos para gerar prova, redação ou trabalho inteiro pronto para copiar (cola/plágio).
     */
    public static boolean isPlagiarismOrCheatingRequest(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String norm = Bm25RagRetriever.normalizeText(message);
        boolean isMetaQuestionAboutPolicy = (norm.startsWith("como a dinga") || norm.startsWith("o que e plagio")
                || norm.startsWith("como voces") || norm.startsWith("por que"))
                && !norm.contains("para eu copiar") && !norm.contains("meu trabalho") && !norm.contains("minha prova");
        if (isMetaQuestionAboutPolicy) {
            return false;
        }
        return ANTI_PLAGIARISM_PATTERN.matcher(norm).find();
    }

    private static List<ChatTurn> sanitizeHistory(List<ChatTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<ChatTurn> safe = new ArrayList<>(history.size());
        for (ChatTurn turn : history) {
            safe.add(new ChatTurn(
                    sanitizePii(turn.question()).sanitizedText(),
                    sanitizePii(turn.reply()).sanitizedText(),
                    turn.practicePrompt(),
                    turn.skillId()
            ));
        }
        return List.copyOf(safe);
    }

    private static String buildPiiNotice(List<String> maskedTypes) {
        String typesLabel = String.join(", ", maskedTypes);
        return "**Proteção LGPD Ativa:** detectei e mascarei automaticamente dados pessoais (**" + typesLabel
                + "**) da sua mensagem antes do processamento. Evite compartilhar CPF, e-mail ou telefone no chat.";
    }

    public record PiiSanitizationResult(String sanitizedText, List<String> maskedTypes) {
        public boolean anyMasked() {
            return maskedTypes != null && !maskedTypes.isEmpty();
        }
    }

    /**
     * Extrai frases e termos de exemplo em inglês (en-US) da resposta e dos trechos RAG,
     * separando-os da explicação em português (pt-BR) para treino de pronúncia e listening.
     */
    public static List<String> extractEnglishExamples(String reply, List<KnowledgeChunk> sources) {
        Set<String> extracted = new LinkedHashSet<>();
        collectEnglishQuotes(reply, extracted);
        if (sources != null) {
            for (KnowledgeChunk chunk : sources) {
                collectEnglishQuotes(chunk.content(), extracted);
            }
        }
        if (extracted.isEmpty()) {
            extracted.add("Actually, I study technology every night.");
            extracted.add("I am learning English to read technical documentation on Microsoft Learn.");
        }
        List<String> result = new ArrayList<>(extracted);
        return result.size() > 6 ? List.copyOf(result.subList(0, 6)) : List.copyOf(result);
    }

    private static void collectEnglishQuotes(String text, Set<String> target) {
        if (text == null || text.isBlank()) {
            return;
        }
        // Remove segmentos "❌ Evite: ..." antes de extrair exemplos para nunca sugerir frases erradas nos chips 🔊 ou Quiz
        String sanitized = AVOID_SEGMENT.matcher(text).replaceAll("");
        Matcher matcher = QUOTED_PHRASE.matcher(sanitized);
        while (matcher.find()) {
            String candidate = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            if (candidate == null) {
                continue;
            }
            String clean = candidate.replaceAll("[*_`]", "").trim();
            if (clean.length() < 4 || clean.length() > 140) {
                continue;
            }
            String withoutProperNoun = clean.replace("São Paulo", "Sao Paulo");
            if (PT_DIACRITICS.matcher(withoutProperNoun).find()) {
                continue;
            }
            String norm = Bm25RagRetriever.normalizeText(clean);
            if (PT_WORDS.matcher(norm).find()) {
                continue;
            }
            if (EN_WORDS.matcher(norm).find()) {
                target.add(clean);
            }
        }
    }

    private static double estimatePayloadSizeKb(String reply, String practicePrompt, List<KnowledgeChunk> sources) {
        int bytes = 0;
        if (reply != null) {
            bytes += reply.getBytes(StandardCharsets.UTF_8).length;
        }
        if (practicePrompt != null) {
            bytes += practicePrompt.getBytes(StandardCharsets.UTF_8).length;
        }
        if (sources != null) {
            for (KnowledgeChunk chunk : sources) {
                bytes += chunk.title().getBytes(StandardCharsets.UTF_8).length;
                bytes += chunk.section().getBytes(StandardCharsets.UTF_8).length;
                bytes += chunk.content().getBytes(StandardCharsets.UTF_8).length;
            }
        }
        // Inclui overhead estimado de metadados JSON
        bytes += 380;
        double kb = bytes / 1024.0;
        return Math.max(0.25, Math.round(kb * 100.0) / 100.0);
    }

    private static LearnerStateDelta computeLearnerStateDelta(
            ChatRequest safeRequest,
            String reply,
            List<KnowledgeChunk> sources,
            TeacherSkill skill
    ) {
        Set<String> weakPoints = new LinkedHashSet<>();
        Set<String> mastered = new LinkedHashSet<>();

        PraticaAtivaSkill.GrammarAnalysis grammar = PraticaAtivaSkill.analyzeBrazilianPatterns(safeRequest.message());
        for (PraticaAtivaSkill.GrammarIssue issue : grammar.issues()) {
            if (issue.title() != null && !issue.title().isBlank()) {
                weakPoints.add(issue.title());
            }
        }

        String focusTopic = (sources != null && !sources.isEmpty())
                ? sources.getFirst().section()
                : skill.definition().name();

        boolean hadPreviousChallenge = safeRequest.lastTurn()
                .map(turn -> turn.practicePrompt() != null && !turn.practicePrompt().isBlank())
                .orElse(false);
        String msg = safeRequest.message().trim();
        String normMsg = Bm25RagRetriever.normalizeText(msg);
        boolean looksLikeStandaloneQuestion = msg.endsWith("?")
                && (normMsg.startsWith("qual ") || normMsg.startsWith("o que ") || normMsg.startsWith("como ")
                || normMsg.startsWith("onde ") || normMsg.startsWith("quais ") || normMsg.startsWith("por que "))
                && !normMsg.contains("certo") && !normMsg.contains("correto") && !normMsg.contains("acertei");

        boolean isMicroChallengeEvaluation = reply != null && (
                reply.contains("Avaliação do seu Micro-desafio")
                        || (hadPreviousChallenge && !looksLikeStandaloneQuestion)
        );

        if (isMicroChallengeEvaluation) {
            String normReply = Bm25RagRetriever.normalizeText(reply);
            boolean hasPositiveCue = normReply.contains("excelente")
                    || normReply.contains("muito bem")
                    || normReply.contains("resposta certeira")
                    || normReply.contains("parabens")
                    || normReply.contains("corretamente")
                    || normReply.contains("mandou bem")
                    || normReply.contains("perfeito");
            boolean hasCorrectiveCue = normReply.contains("quase la")
                    || normReply.contains("ajuste apenas")
                    || normReply.contains("correcao gramatical")
                    || normReply.contains("correcao sugerida")
                    || normReply.contains("o que ajustar");

            if (hasPositiveCue && !hasCorrectiveCue && weakPoints.isEmpty()) {
                mastered.add(focusTopic);
            } else if (hasCorrectiveCue) {
                weakPoints.add(focusTopic);
            }
        }

        return new LearnerStateDelta(List.copyOf(weakPoints), List.copyOf(mastered), focusTopic);
    }

    /**
     * Buffer deslizante LGPD para streaming SSE: retém uma pequena cauda até fronteira segura
     * para mascarar CPF, e-mail ou telefone mesmo quando divididos entre dois chunks consecutivos.
     */
    private static final class PiiSafeStreamConsumer implements Consumer<String> {
        private static final int TAIL_GUARD_CHARS = 40;
        private final Consumer<String> downstream;
        private final StringBuilder pending = new StringBuilder();

        PiiSafeStreamConsumer(Consumer<String> downstream) {
            this.downstream = downstream;
        }

        @Override
        public void accept(String delta) {
            if (delta == null || delta.isEmpty()) {
                return;
            }
            pending.append(delta);
            if (pending.length() <= TAIL_GUARD_CHARS) {
                return;
            }
            int safeLimit = pending.length() - TAIL_GUARD_CHARS;
            int splitIdx = findSafeWhitespaceBoundary(pending, safeLimit);
            if (splitIdx <= 0) {
                return;
            }
            String candidate = pending.substring(0, splitIdx);
            pending.delete(0, splitIdx);
            emitSanitized(candidate);
        }

        void flush() {
            if (pending.isEmpty()) {
                return;
            }
            String remaining = pending.toString();
            pending.setLength(0);
            emitSanitized(remaining);
        }

        private void emitSanitized(String chunk) {
            String safe = sanitizePii(chunk).sanitizedText();
            if (!safe.isEmpty() && downstream != null) {
                downstream.accept(safe);
            }
        }

        private static int findSafeWhitespaceBoundary(StringBuilder sb, int maxEnd) {
            for (int i = Math.min(maxEnd, sb.length()) - 1; i >= 0; i--) {
                if (Character.isWhitespace(sb.charAt(i))) {
                    return i + 1;
                }
            }
            return -1;
        }
    }

    private static String generateWatermarkId() {
        String datePart = LocalDate.now().format(WATERMARK_DATE);
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase(Locale.ROOT);
        return "DINGA-" + datePart + "-" + suffix;
    }
}
