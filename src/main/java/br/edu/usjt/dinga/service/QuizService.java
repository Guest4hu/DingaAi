package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.QuizAnswerRequest;
import br.edu.usjt.dinga.model.QuizAnswerResponse;
import br.edu.usjt.dinga.model.QuizGenerationRequest;
import br.edu.usjt.dinga.model.QuizQuestion;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import br.edu.usjt.dinga.rag.KnowledgeBaseLoader;
import br.edu.usjt.dinga.storage.JsonDataStore;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Serviço do Modo Quiz / Desafio Rápido: questões interativas sobre vocabulário técnico
 * do Microsoft Learn, Cloud, APIs, Git e falsos cognatos para brasileiros, com gerador
 * dinâmico ancorado na base RAG (LLM híbrido + síntese pedagógica local) e persistência JSON.
 */
@Service
public class QuizService {

    public static final String STORAGE_FILE = "generated-quiz.json";
    private static final Pattern BOLD_TERM = Pattern.compile("\\*\\*([^*]{2,55})\\*\\*");
    private static final Pattern GENERATED_ID_SEQ = Pattern.compile("^quiz-gen-(\\d+)$");

    private final KnowledgeBaseLoader knowledgeLoader;
    private final Bm25RagRetriever ragRetriever;
    private final HybridLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final JsonDataStore dataStore;
    private final SessionMetricsService metricsService;

    private final List<QuizQuestion> baseQuestions = List.of(
            new QuizQuestion(
                    "quiz-rag-grounding",
                    "B1",
                    "Microsoft Learn & IA",
                    "Nos módulos de IA Generativa do Microsoft Learn, qual é a função principal do 'Grounding' combinado ao 'RAG'?",
                    "\"Use Retrieval-Augmented Generation (RAG) to ground the model's responses in your organization's trusted data.\"",
                    List.of(
                            "Ancorar o modelo em documentos reais e confiáveis para reduzir alucinações (hallucinations).",
                            "Treinar todos os pesos da rede neural do zero (pre-training) a cada pergunta.",
                            "Traduzir automaticamente códigos Java para Python sem validação humana.",
                            "Aumentar a temperatura criativa do modelo para gerar histórias fictícias."
                    ),
                    0,
                    "Correto! 'Grounding' significa ancorar a geração da IA em dados verificáveis (via RAG), evitando que o modelo invente fatos sem fonte (hallucination).",
                    "We use RAG to ground the AI tutor in curated educational documents.",
                    "05-ingles-tecnico-microsoft-learn-ia"
            ),
            new QuizQuestion(
                    "quiz-false-cognate-actually",
                    "A2",
                    "Falsos Cognatos",
                    "Em uma reunião técnica, você quer dizer: 'Na verdade, eu estudo documentação em inglês todas as noites'. Como escrever sem cair no falso cognato?",
                    "False friend alert: 'Actually' vs. 'Currently / Nowadays'",
                    List.of(
                            "Actually, I study English documentation every night.",
                            "Currently, I pretend to study English documentation every night.",
                            "Atually, I attend a library to buy books every night.",
                            "In actual, I have 20 years and study documentation."
                    ),
                    0,
                    "Excelente! 'Actually' significa 'na verdade / de fato'. Já para dizer 'atualmente', usamos 'currently' ou 'nowadays'.",
                    "Actually, I study English documentation every night, and currently I am learning Cloud APIs.",
                    "07-falsos-cognatos-e-erros-comuns-brasileiros"
            ),
            new QuizQuestion(
                    "quiz-api-payload-endpoint",
                    "B1",
                    "Cloud, APIs & Git",
                    "Ao depurar uma API REST, você lê o log abaixo. O que significam 'endpoint' e 'payload'?",
                    "\"The POST /api/chat endpoint returned HTTP 400 because the request payload was missing required fields.\"",
                    List.of(
                            "Endpoint é o endereço/rota da API (/api/chat) e payload é o corpo de dados enviado na requisição.",
                            "Endpoint é o banco de dados final e payload é o custo financeiro da nuvem.",
                            "Endpoint é o cabo de rede do servidor e payload é o tempo de espera em milissegundos.",
                            "Endpoint é um erro de compilação e payload é a senha de autenticação do usuário."
                    ),
                    0,
                    "Perfeito! Em APIs REST, 'endpoint' é a URL de acesso do serviço e 'payload' é a carga útil de dados (JSON) trafegada.",
                    "The endpoint validates the JSON payload before returning a response.",
                    "06-ingles-tecnico-cloud-apis-git"
            ),
            new QuizQuestion(
                    "quiz-cloud-fallback-latency",
                    "B1",
                    "Cloud, APIs & Git",
                    "Na arquitetura da Dinga AI e no Microsoft Learn, o que significa acionar um 'fallback' com baixa 'latency'?",
                    "\"When the remote LLM API reaches its rate limit, the application triggers a local RAG fallback with 3 ms latency.\"",
                    List.of(
                            "Acionar um plano B de contingência (modo local) com tempo de resposta quase instantâneo.",
                            "Desligar o servidor permanentemente quando a internet oscila.",
                            "Fazer backup manual em fita magnética uma vez por mês.",
                            "Duplicar o consumo de banda móvel do estudante."
                    ),
                    0,
                    "Isso mesmo! 'Fallback' é o mecanismo de contingência que mantém o sistema funcionando se o serviço principal falhar, e 'latency' é o tempo de resposta.",
                    "Our hybrid architecture uses a local RAG fallback to guarantee low latency even offline.",
                    "06-ingles-tecnico-cloud-apis-git"
            ),
            new QuizQuestion(
                    "quiz-daily-past-age",
                    "A2",
                    "Carreira & Daily",
                    "Qual opção corrige os dois erros clássicos de brasileiros na frase: 'Yesterday I work in the API and I have 20 years'?",
                    "Daily Standup & Personal Introduction Review",
                    List.of(
                            "Yesterday I worked on the API, and I am 20 years old.",
                            "Yesterday I working in the API, and I have 20 years old.",
                            "Yesterday I did worked on the API, and I am 20 years.",
                            "Tomorrow I worked on the API, and I has 20 years old."
                    ),
                    0,
                    "Certo! Com 'Yesterday' (passado concluído), o verbo regular 'work' recebe '-ed' (worked), e idade em inglês usa o verbo 'to be' (I am 20 years old).",
                    "Yesterday I worked on the API and wrote unit tests.",
                    "04-b2-comunicacao-profissional-daily-entrevista"
            ),
            new QuizQuestion(
                    "quiz-git-push-pull",
                    "A2",
                    "Cloud, APIs & Git",
                    "No Git e no GitHub, muitos brasileiros confundem 'push' e 'pull' por causa do som de 'puxar'. Qual é o significado real?",
                    "\"Always run 'git pull' to sync changes before running 'git push' and opening a Pull Request.\"",
                    List.of(
                            "Push significa empurrar (enviar commits ao repositório remoto) e Pull significa puxar (baixar atualizações).",
                            "Push significa puxar arquivos para sua máquina e Pull significa apagar o repositório.",
                            "Push e Pull são sinônimos e ambos apenas criam uma branch local.",
                            "Push fecha o código em produção e Pull compila o arquivo Dockerfile."
                    ),
                    0,
                    "Muito bem! 'Push' é falso amigo de 'puxar': em inglês, 'push' = empurrar (enviar código) e 'pull' = puxar (trazer código).",
                    "I pushed my commits to the branch and opened a pull request for code review.",
                    "06-ingles-tecnico-cloud-apis-git"
            ),
            new QuizQuestion(
                    "quiz-docs-must-should",
                    "B1",
                    "Microsoft Learn & IA",
                    "Ao fazer 'scanning' na documentação técnica abaixo, qual a diferença entre os modais 'must' e 'should'?",
                    "\"You must set the JAVA_HOME variable to JDK 25. You should also enable low-data mode on slow connections.\"",
                    List.of(
                            "'Must' indica requisito obrigatório (sem isso falha) e 'should' indica recomendação/boa prática.",
                            "'Must' é apenas uma sugestão opcional e 'should' proíbe a configuração.",
                            "Ambos indicam ações que aconteceram no passado distante.",
                            "'Must' significa 'talvez' e 'should' significa 'nunca'."
                    ),
                    0,
                    "Exato! Em manuais técnicos e RFCs, 'must' define o que é obrigatório para o sistema rodar, enquanto 'should' aponta uma boa prática recomendada.",
                    "You must validate AI outputs with reliable sources, and you should practice English daily.",
                    "03-b1-leitura-documentacao-tecnica"
            ),
            new QuizQuestion(
                    "quiz-false-friends-library-pretend",
                    "A2",
                    "Falsos Cognatos",
                    "Qual frase traduz corretamente: 'Eu pretendo estudar a biblioteca de código hoje'?",
                    "False friends: 'pretend vs. intend/plan' & 'library vs. bookstore'",
                    List.of(
                            "I intend (or plan) to study the code library today.",
                            "I pretend to study the code bookstore today.",
                            "I support to attend the informations library today.",
                            "I pretend to make a question about the library."
                    ),
                    0,
                    "Na mosca! 'Pretend' significa 'fingir' (para 'pretender', use 'intend' ou 'plan'). E 'library' é 'biblioteca' (enquanto 'livraria' é 'bookstore').",
                    "I plan to explore the new Java library in the official documentation today.",
                    "07-falsos-cognatos-e-erros-comuns-brasileiros"
            )
    );

    private final CopyOnWriteArrayList<QuizQuestion> generatedQuestions = new CopyOnWriteArrayList<>();
    private final AtomicInteger generatedSeq = new AtomicInteger(0);

    public QuizService(
            KnowledgeBaseLoader knowledgeLoader,
            Bm25RagRetriever ragRetriever,
            HybridLlmClient llmClient,
            ObjectMapper objectMapper
    ) {
        this(knowledgeLoader, ragRetriever, llmClient, objectMapper, null, null);
    }

    public QuizService(
            KnowledgeBaseLoader knowledgeLoader,
            Bm25RagRetriever ragRetriever,
            HybridLlmClient llmClient,
            ObjectMapper objectMapper,
            JsonDataStore dataStore
    ) {
        this(knowledgeLoader, ragRetriever, llmClient, objectMapper, dataStore, null);
    }

    @Autowired
    public QuizService(
            KnowledgeBaseLoader knowledgeLoader,
            Bm25RagRetriever ragRetriever,
            HybridLlmClient llmClient,
            ObjectMapper objectMapper,
            JsonDataStore dataStore,
            SessionMetricsService metricsService
    ) {
        this.knowledgeLoader = knowledgeLoader;
        this.ragRetriever = ragRetriever;
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.dataStore = dataStore;
        this.metricsService = metricsService;
        loadPersistedQuestions();
    }

    private void loadPersistedQuestions() {
        if (dataStore == null) {
            return;
        }
        List<QuizQuestion> saved = dataStore.readList(STORAGE_FILE, QuizQuestion.class);
        int maxSeq = 0;
        for (QuizQuestion q : saved) {
            if (q == null || q.id() == null || q.id().isBlank()) {
                continue;
            }
            generatedQuestions.add(q);
            Matcher m = GENERATED_ID_SEQ.matcher(q.id());
            if (m.find()) {
                maxSeq = Math.max(maxSeq, Integer.parseInt(m.group(1)));
            }
        }
        generatedSeq.set(Math.max(maxSeq, generatedQuestions.size()));
    }

    public List<QuizQuestion> listQuestions(String level, String category) {
        List<QuizQuestion> all = new ArrayList<>(baseQuestions.size() + generatedQuestions.size());
        all.addAll(baseQuestions);
        all.addAll(generatedQuestions);
        return all.stream()
                .filter(q -> level == null || level.isBlank() || "ALL".equalsIgnoreCase(level) || q.level().equalsIgnoreCase(level.trim()))
                .filter(q -> category == null || category.isBlank() || "ALL".equalsIgnoreCase(category)
                        || q.category().toLowerCase().contains(category.trim().toLowerCase()))
                .toList();
    }

    public Optional<QuizQuestion> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        String cleanId = id.trim();
        return baseQuestions.stream()
                .filter(q -> q.id().equalsIgnoreCase(cleanId))
                .findFirst()
                .or(() -> generatedQuestions.stream().filter(q -> q.id().equalsIgnoreCase(cleanId)).findFirst());
    }

    public QuizAnswerResponse checkAnswer(QuizAnswerRequest request) {
        QuizQuestion q = findById(request.questionId())
                .orElseThrow(() -> new IllegalArgumentException("Questão de quiz não encontrada: " + request.questionId()));
        int selected = request.selectedIndex();
        if (selected < 0 || selected >= q.options().size()) {
            throw new IllegalArgumentException(
                    "Índice de alternativa inválido (" + selected + ") para a questão " + q.id()
            );
        }
        boolean correct = selected == q.correctIndex();
        if (metricsService != null) {
            metricsService.recordQuizAnswer(q.category(), q.level(), correct);
        }
        String feedback = correct
                ? q.explanation()
                : "Resposta incorreta. A alternativa correta é: \"" + q.options().get(q.correctIndex()) + "\". " + q.explanation();
        String followUp = "Me explique mais exemplos práticos sobre: " + q.question();
        return new QuizAnswerResponse(
                q.id(),
                correct,
                request.selectedIndex(),
                q.correctIndex(),
                feedback,
                q.englishExample(),
                q.sourceDocId(),
                followUp
        );
    }

    /**
     * Gera novas questões interativas ancoradas nos documentos da base RAG (incluindo documentos
     * cadastrados ao vivo pelo Painel do Professor).
     */
    public GeneratedQuizBatch generateQuestions(QuizGenerationRequest request) {
        QuizGenerationRequest normalized = request == null
                ? new QuizGenerationRequest("", "B1", "", 1)
                : request;

        List<KnowledgeChunk> candidateChunks = resolveCandidateChunks(normalized);
        int count = normalized.count();
        List<QuizQuestion> created = new ArrayList<>(count);
        String generationMode = "Motor Pedagógico RAG Local (BM25)";

        for (int i = 0; i < count; i++) {
            int seq = generatedSeq.incrementAndGet();
            int chunkIdx = (!normalized.topic().isBlank() || !normalized.docId().isBlank())
                    ? (i % candidateChunks.size())
                    : ((seq - 1 + i) % candidateChunks.size());
            KnowledgeChunk chunk = candidateChunks.get(chunkIdx);

            Optional<LlmGeneratedQuestion> llmQuestion = tryGenerateWithLlm(chunk, normalized, seq);
            if (llmQuestion.isPresent()) {
                created.add(llmQuestion.get().question());
                generationMode = "IA Generativa + RAG (" + llmQuestion.get().providerLabel() + " · " + chunk.docId() + ")";
            } else {
                created.add(buildDeterministicRagQuestion(chunk, normalized, seq, i));
            }
        }

        generatedQuestions.addAll(created);
        persistGeneratedQuestions();
        return new GeneratedQuizBatch(
                List.copyOf(created),
                baseQuestions.size() + generatedQuestions.size(),
                generationMode
        );
    }

    public boolean isBaseQuestion(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return false;
        }
        return baseQuestions.stream().anyMatch(q -> q.id().equalsIgnoreCase(questionId.trim()));
    }

    public synchronized boolean removeGeneratedQuestion(String questionId) {
        if (questionId == null || questionId.isBlank()) {
            return false;
        }
        boolean removed = generatedQuestions.removeIf(q -> q.id().equalsIgnoreCase(questionId.trim()));
        if (removed) {
            persistGeneratedQuestions();
        }
        return removed;
    }

    public synchronized void resetGeneratedQuestions() {
        generatedQuestions.clear();
        generatedSeq.set(0);
        persistGeneratedQuestions();
    }

    private void persistGeneratedQuestions() {
        if (dataStore != null) {
            dataStore.writeList(STORAGE_FILE, List.copyOf(generatedQuestions));
        }
    }

    private List<KnowledgeChunk> resolveCandidateChunks(QuizGenerationRequest req) {
        Set<KnowledgeChunk> selected = new LinkedHashSet<>();
        List<KnowledgeChunk> allChunks = knowledgeLoader.getChunks();

        if (!req.docId().isBlank()) {
            allChunks.stream()
                    .filter(c -> c.docId().equalsIgnoreCase(req.docId()) || c.docId().contains(req.docId()))
                    .forEach(selected::add);
        }

        if (selected.isEmpty() && !req.topic().isBlank()) {
            List<KnowledgeChunk> retrieved = ragRetriever.retrieve(req.topic(), req.level(), 5);
            selected.addAll(retrieved);
        }

        if (selected.isEmpty()) {
            allChunks.stream()
                    .filter(c -> c.level().equalsIgnoreCase(req.level()) || "ALL".equalsIgnoreCase(c.level()))
                    .forEach(selected::add);
        }

        if (selected.isEmpty()) {
            selected.addAll(allChunks);
        }
        return List.copyOf(selected);
    }

    private Optional<LlmGeneratedQuestion> tryGenerateWithLlm(KnowledgeChunk chunk, QuizGenerationRequest req, int seq) {
        String topicFocus = req.topic().isBlank() ? chunk.section() : req.topic();
        String systemPrompt = """
                Você é o gerador pedagógico de questões da Dinga AI (Caso 3 - Microsoft Learn).
                Crie 1 questão INÉDITA de múltipla escolha (exatamente 4 alternativas distintas) sobre inglês aplicado/técnico para estudantes brasileiros (nível CEFR %s), ancorada ESTRITAMENTE no trecho RAG fornecido.
                Regras pedagógicas:
                - Os 3 distratores devem ser plausíveis e naturais (ex.: erros reais ou confusões comuns de brasileiros), nunca absurdos.
                - A explicação deve ser clara, acolhedora e personalizada em português, citando o conceito do trecho RAG.
                - Inclua um exemplo natural em inglês (englishExample) para treino de pronúncia (en-US).
                Retorne APENAS um JSON válido sem blocos markdown, com as chaves exatas:
                {"question":"...","codeOrDocSnippet":"...","options":["A","B","C","D"],"correctIndex":0,"explanation":"...","englishExample":"..."}
                """.formatted(req.level());
        String userPrompt = "Nível CEFR: " + req.level() + "\nFoco: " + topicFocus
                + "\nDocumento RAG (" + chunk.docId() + " - " + chunk.section() + "):\n" + chunk.content();

        Optional<HybridLlmClient.LlmResult> result = llmClient.generate(systemPrompt, userPrompt);
        if (result.isEmpty()) {
            return Optional.empty();
        }
        try {
            String raw = result.get().text().trim();
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return Optional.empty();
            }
            JsonNode node = objectMapper.readTree(raw.substring(start, end + 1));
            String qText = node.path("question").asString("").trim();
            String snippet = node.path("codeOrDocSnippet").asString("").trim();
            String explanation = node.path("explanation").asString("").trim();
            String englishEx = node.path("englishExample").asString("").trim();
            int correctIdx = node.path("correctIndex").asInt(0);
            JsonNode optsNode = node.path("options");

            if (qText.isBlank() || !optsNode.isArray() || optsNode.size() < 4) {
                return Optional.empty();
            }
            List<String> options = new ArrayList<>(4);
            Set<String> uniqueCheck = new LinkedHashSet<>();
            for (int i = 0; i < 4; i++) {
                String opt = optsNode.get(i).asString("").trim();
                if (opt.isBlank()) {
                    return Optional.empty();
                }
                options.add(opt);
                uniqueCheck.add(opt.toLowerCase());
            }
            if (uniqueCheck.size() < 4) {
                return Optional.empty();
            }
            if (correctIdx < 0 || correctIdx >= 4) {
                correctIdx = 0;
            }
            String effectiveLevel = "ALL".equalsIgnoreCase(chunk.level()) ? req.level() : chunk.level();
            QuizQuestion question = new QuizQuestion(
                    "quiz-gen-" + seq,
                    effectiveLevel,
                    chunk.category(),
                    qText,
                    snippet.isBlank() ? "\"" + englishEx + "\"" : snippet,
                    List.copyOf(options),
                    correctIdx,
                    explanation.isBlank() ? "Questão gerada com ancoragem no documento " + chunk.title() + "." : explanation,
                    englishEx.isBlank() ? "We practice technical English with curated RAG sources." : englishEx,
                    chunk.docId()
            );
            return Optional.of(new LlmGeneratedQuestion(question, result.get().providerLabel()));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private record LlmGeneratedQuestion(QuizQuestion question, String providerLabel) {
    }

    private QuizQuestion buildDeterministicRagQuestion(
            KnowledgeChunk chunk,
            QuizGenerationRequest req,
            int seq,
            int offset
    ) {
        List<String> bulletLines = extractBulletLines(chunk.content());
        String targetLine = selectBestBulletLine(bulletLines, req.topic(), seq, offset, chunk.content());

        String term = extractBoldTerm(targetLine).orElse(chunk.section());
        String englishQuote = extractEnglishQuote(targetLine)
                .or(() -> extractEnglishQuote(chunk.content()))
                .orElseGet(() -> buildFallbackEnglishQuote(chunk, term));

        String cleanSummary = cleanMarkdownLine(targetLine);
        if (cleanSummary.length() > 175) {
            cleanSummary = cleanSummary.substring(0, 172).trim() + "...";
        }

        String effectiveLevel = "ALL".equalsIgnoreCase(chunk.level()) ? req.level() : chunk.level();
        String conceptLabel = term.equalsIgnoreCase(chunk.section()) && !req.topic().isBlank()
                ? req.topic()
                : term;

        String questionText = String.format(
                "Segundo a base curada \"%s\" (seção: %s), qual alternativa descreve ou aplica corretamente \"%s\"?",
                chunk.title(),
                chunk.section(),
                conceptLabel
        );

        String correctOption = cleanSummary;
        List<String> distractors = List.of(
                "Traduzir palavra por palavra literalmente sem considerar falsos cognatos ou contexto técnico da documentação.",
                "Ignorar a validação humana (MTG-IA) e aceitar respostas sem fonte verificável (hallucination).",
                "Substituir verbos no passado por formas sem conjugação e usar 'informations' no plural contável."
        );

        int correctIndex = seq % 4;
        List<String> options = new ArrayList<>(4);
        int dIdx = 0;
        for (int i = 0; i < 4; i++) {
            if (i == correctIndex) {
                options.add(correctOption);
            } else {
                options.add(distractors.get(dIdx++));
            }
        }

        String explanation = String.format(
                "Ancorado no documento RAG '%s' (%s): %s",
                chunk.title(),
                chunk.section(),
                cleanSummary
        );

        return new QuizQuestion(
                "quiz-gen-" + seq,
                effectiveLevel,
                chunk.category(),
                questionText,
                "\"" + englishQuote + "\"",
                List.copyOf(options),
                correctIndex,
                explanation,
                englishQuote,
                chunk.docId()
        );
    }

    private static String selectBestBulletLine(
            List<String> bulletLines,
            String topic,
            int seq,
            int offset,
            String fullContent
    ) {
        if (bulletLines.isEmpty()) {
            return firstMeaningfulLine(fullContent);
        }
        if (topic != null && !topic.isBlank()) {
            String[] tokens = topic.toLowerCase().split("[^a-z0-9]+");
            List<String> matching = new ArrayList<>();
            for (String line : bulletLines) {
                String lower = line.toLowerCase();
                for (String tk : tokens) {
                    if (tk.length() >= 3 && lower.contains(tk)) {
                        matching.add(line);
                        break;
                    }
                }
            }
            if (!matching.isEmpty()) {
                return matching.get(offset % matching.size());
            }
        }
        return bulletLines.get((seq - 1 + offset) % bulletLines.size());
    }

    private static String buildFallbackEnglishQuote(KnowledgeChunk chunk, String term) {
        String doc = chunk.docId().toLowerCase();
        if (doc.contains("a1")) {
            return "I am a student in Brazil, and I study technology every night.";
        }
        if (doc.contains("a2") || doc.contains("falsos-cognatos")) {
            return "Actually, I practiced English yesterday because the documentation is in English.";
        }
        if (doc.contains("b2") || doc.contains("daily")) {
            return "Yesterday I worked on the backend API, and today I will review pull requests.";
        }
        if (doc.contains("mtg") || doc.contains("ods") || doc.contains("socratico")) {
            return "Responsible AI requires human validation, transparency, and curated RAG sources.";
        }
        String cleanTerm = term.replaceAll("[^A-Za-z0-9 /_-]", "").trim();
        if (cleanTerm.isEmpty() || cleanTerm.length() > 36) {
            cleanTerm = "technical documentation";
        }
        return "We apply " + cleanTerm + " when reading technical documentation on Microsoft Learn.";
    }

    private static List<String> extractBulletLines(String content) {
        List<String> bullets = new ArrayList<>();
        if (content == null) {
            return bullets;
        }
        for (String rawLine : content.split("\n")) {
            String trimmed = rawLine.trim();
            if ((trimmed.startsWith("- ") || trimmed.startsWith("* ")) && trimmed.length() > 18) {
                bullets.add(trimmed.substring(2).trim());
            }
        }
        return bullets;
    }

    private static String firstMeaningfulLine(String content) {
        if (content == null || content.isBlank()) {
            return "Prática de inglês técnico com fontes curadas do RAG e validação humana.";
        }
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return trimmed;
            }
        }
        return content.trim();
    }

    private static Optional<String> extractBoldTerm(String line) {
        if (line == null) {
            return Optional.empty();
        }
        Matcher m = BOLD_TERM.matcher(line);
        if (m.find()) {
            String term = m.group(1).replaceAll("[:：]$", "").trim();
            if (!term.isEmpty()) {
                return Optional.of(term);
            }
        }
        return Optional.empty();
    }

    private static Optional<String> extractEnglishQuote(String text) {
        if (text == null) {
            return Optional.empty();
        }
        List<String> quotes = DingaTutorService.extractEnglishExamples(text, List.of());
        if (!quotes.isEmpty()) {
            String first = quotes.getFirst();
            if (!first.startsWith("Actually, I study technology every night.")) {
                return Optional.of(first);
            }
        }
        return Optional.empty();
    }

    private static String cleanMarkdownLine(String line) {
        if (line == null) {
            return "";
        }
        return line.replaceAll("[*_`]", "").replaceAll("\\s+", " ").trim();
    }

    public record GeneratedQuizBatch(
            List<QuizQuestion> generated,
            int totalQuestions,
            String generationMode
    ) {
    }
}
