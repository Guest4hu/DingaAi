package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.SimulationRequest;
import br.edu.usjt.dinga.model.SimulationResponse;
import br.edu.usjt.dinga.model.SimulationTurn;
import br.edu.usjt.dinga.skills.PraticaAtivaSkill;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Simulador guiado multi-turno de Daily Standup (3 etapas) e Entrevista Técnica (4 etapas).
 * Utiliza Gemini 3.8 Flash (via HybridLlmClient) com fallback pedagógico local determinístico.
 */
@Service
public class SimulatorService {

    private static final Pattern TECH_TERMS_PATTERN = Pattern.compile(
            "\\b(api|endpoint|payload|backend|frontend|bug|fix|fixed|deploy|deployed|test|tests|unit|integration|git|branch|commit|pull request|pr|code review|database|sql|cloud|azure|docker|latency|fallback|rag|llm|prompt|sprint|blocker|feature|refactor|documentation|service|java|spring|javascript|python|pipeline|release|staging|production|ticket|issue)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PAST_VERB_PATTERN = Pattern.compile(
            "\\b(worked|fixed|tested|implemented|created|built|finished|reviewed|deployed|updated|refactored|solved|debugged|wrote|had|did|was|were|studied|learned|pushed|merged|opened)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FUTURE_PLAN_PATTERN = Pattern.compile(
            "\\b(will|going to|plan to|intend to|today|working on|focus on|continue|start|finish|implement|review|test|deploy)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern BLOCKER_PATTERN = Pattern.compile(
            "\\b(no blockers?|don't have any blockers?|blocked|waiting|need help|issue|impediment|review|access|permission|dependency|clear|smooth|nothing)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final List<StepTemplate> DAILY_STEPS = List.of(
            new StepTemplate(
                    "Yesterday",
                    "What did you work on yesterday?",
                    "Use o passado simples (ex.: worked on, fixed, tested, implemented) e cite uma entrega objetiva.",
                    "Yesterday I worked on the REST API endpoint and wrote unit tests for the authentication service."
            ),
            new StepTemplate(
                    "Today",
                    "What are your main tasks for today?",
                    "Use 'Today I will...' ou 'Today I plan to...' para indicar foco claro da sprint.",
                    "Today I will review my team's pull requests and implement the fallback handler for low connectivity."
            ),
            new StepTemplate(
                    "Blockers",
                    "Do you have any blockers or anything you need help with?",
                    "Diga 'I don't have any blockers right now' ou explique o impedimento de forma direta e colaborativa.",
                    "No blockers so far, I am just waiting for a quick code review on my feature branch."
            )
    );

    private static final List<StepTemplate> INTERVIEW_STEPS = List.of(
            new StepTemplate(
                    "Introduction",
                    "Tell me briefly about yourself and your background in technology.",
                    "Apresente seu curso/área, tecnologias principais e objetivo profissional (evite 'I have X years').",
                    "Hello! I am a technology student in Brazil, and I focus on Java backend development, APIs, and applied AI."
            ),
            new StepTemplate(
                    "Technical Project",
                    "Can you describe a technical project or API you worked on recently?",
                    "Descreva problema, arquitetura e seu papel usando verbos no passado (built, designed, integrated).",
                    "Recently I built an AI English tutor using Java, Spring Boot, and local BM25 RAG to support low-connectivity students."
            ),
            new StepTemplate(
                    "Problem Solving",
                    "Tell me about a technical bug or challenge you faced and how you solved it.",
                    "Estruture em 3 partes: contexto do erro, causa raiz e solução aplicada.",
                    "When our remote LLM API hit rate limits, I implemented a deterministic local RAG fallback so the app kept responding in 3 ms."
            ),
            new StepTemplate(
                    "Career & Learning",
                    "What are you learning right now, and how do you use English in your routine?",
                    "Conecte sua rotina de estudos (Microsoft Learn, documentação técnica, projetos) ao seu próximo objetivo.",
                    "Currently I am studying Cloud architecture on Microsoft Learn and reading official documentation in English every day."
            )
    );

    private final HybridLlmClient llmClient;
    private final ObjectMapper objectMapper;
    private final SessionMetricsService metricsService;

    public SimulatorService(HybridLlmClient llmClient, ObjectMapper objectMapper) {
        this(llmClient, objectMapper, null);
    }

    @Autowired
    public SimulatorService(
            HybridLlmClient llmClient,
            ObjectMapper objectMapper,
            SessionMetricsService metricsService
    ) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
    }

    public SimulationResponse start(String rawMode, String rawLevel) {
        String mode = normalizeMode(rawMode);
        String level = normalizeLevel(rawLevel);
        List<StepTemplate> steps = stepsFor(mode);
        StepTemplate first = steps.getFirst();

        return new SimulationResponse(
                mode,
                modeTitle(mode),
                level,
                1,
                steps.size(),
                false,
                first.label(),
                first.question(),
                first.hint(),
                first.exampleAnswer(),
                null,
                null,
                "Pronto para iniciar",
                false
        );
    }

    public SimulationResponse submitAnswer(SimulationRequest request) {
        String mode = normalizeMode(request.mode());
        String level = normalizeLevel(request.level());
        List<StepTemplate> steps = stepsFor(mode);

        if (request.answer().isBlank()) {
            throw new IllegalArgumentException("Escreva sua resposta em inglês para avançar no simulador.");
        }
        if (request.answer().length() > 1500) {
            throw new IllegalArgumentException("Resposta muito longa (máximo de 1500 caracteres).");
        }

        int stepIdx = Math.min(Math.max(1, request.stepIndex()), steps.size());
        StepTemplate currentTemplate = steps.get(stepIdx - 1);

        // Guardrail LGPD antes de enviar ao LLM externo
        DingaTutorService.PiiSanitizationResult sanitized = DingaTutorService.sanitizePii(request.answer());
        String safeAnswer = sanitized.sanitizedText();
        boolean piiMasked = sanitized.anyMasked();

        EvaluatedTurnResult evaluated = tryEvaluateTurnWithLlm(mode, level, currentTemplate, stepIdx, safeAnswer)
                .orElseGet(() -> evaluateTurnLocally(mode, currentTemplate, stepIdx, safeAnswer));

        SimulationResponse.TurnEvaluation turnEval = evaluated.evaluation();
        String executionMode = evaluated.executionMode();

        List<SimulationTurn> allTurns = new ArrayList<>(request.history());
        allTurns.add(new SimulationTurn(
                stepIdx,
                currentTemplate.label(),
                currentTemplate.question(),
                safeAnswer,
                turnEval.clarityScore(),
                turnEval.grammarScore(),
                turnEval.technicalVocabScore(),
                turnEval.feedback(),
                turnEval.naturalVersion()
        ));

        boolean completed = stepIdx >= steps.size();
        if (metricsService != null) {
            metricsService.recordSimulationTurn(completed, piiMasked);
        }

        if (!completed) {
            StepTemplate next = steps.get(stepIdx);
            return new SimulationResponse(
                    mode,
                    modeTitle(mode),
                    level,
                    stepIdx + 1,
                    steps.size(),
                    false,
                    next.label(),
                    next.question(),
                    next.hint(),
                    next.exampleAnswer(),
                    turnEval,
                    null,
                    executionMode,
                    piiMasked
            );
        }

        SimulationResponse.FinalReport finalReport = buildFinalReport(mode, level, allTurns, turnEval);
        return new SimulationResponse(
                mode,
                modeTitle(mode),
                level,
                steps.size(),
                steps.size(),
                true,
                currentTemplate.label(),
                currentTemplate.question(),
                currentTemplate.hint(),
                currentTemplate.exampleAnswer(),
                turnEval,
                finalReport,
                executionMode,
                piiMasked
        );
    }

    private Optional<EvaluatedTurnResult> tryEvaluateTurnWithLlm(
            String mode,
            String level,
            StepTemplate step,
            int stepIndex,
            String safeAnswer
    ) {
        String systemPrompt = """
                Você é o avaliador do Simulador de Entrevista / Daily Standup da Dinga AI (nível CEFR do aluno: %s).
                Avalie a resposta em inglês do aluno para a etapa "%s" (%s).
                Seja acolhedor, objetivo e técnico (foco em brasileiros aprendendo inglês para TI).
                Retorne APENAS JSON válido sem markdown, com as chaves exatas:
                {"clarityScore":0-100,"grammarScore":0-100,"technicalVocabScore":0-100,"feedback":"...","naturalVersion":"...","strength":"...","improvement":"..."}
                """.formatted(level, step.label(), modeTitle(mode));

        String userPrompt = "Pergunta feita ao aluno: " + step.question()
                + "\nResposta do aluno: " + safeAnswer;

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
            int clarity = clampScore(node.path("clarityScore").asInt(75));
            int grammar = clampScore(node.path("grammarScore").asInt(75));
            int vocab = clampScore(node.path("technicalVocabScore").asInt(75));
            String feedback = node.path("feedback").asString("").trim();
            String natural = node.path("naturalVersion").asString("").trim();
            String strength = node.path("strength").asString("").trim();
            String improvement = node.path("improvement").asString("").trim();

            if (feedback.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new EvaluatedTurnResult(
                    new SimulationResponse.TurnEvaluation(
                            stepIndex,
                            step.label(),
                            clarity,
                            grammar,
                            vocab,
                            feedback,
                            natural.isBlank() ? step.exampleAnswer() : natural,
                            strength.isBlank() ? "Boa disposição para responder em inglês no contexto da etapa." : strength,
                            improvement.isBlank() ? "Continue praticando verbos de ação e termos técnicos da sua rotina." : improvement
                    ),
                    "IA Generativa (" + result.get().providerLabel() + ")"
            ));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private EvaluatedTurnResult evaluateTurnLocally(
            String mode,
            StepTemplate step,
            int stepIndex,
            String answer
    ) {
        PraticaAtivaSkill.GrammarAnalysis grammarAnalysis = PraticaAtivaSkill.analyzeBrazilianPatterns(answer);
        int wordCount = answer.trim().isEmpty() ? 0 : answer.trim().split("\\s+").length;

        int grammarScore = Math.max(45, 95 - (grammarAnalysis.issues().size() * 14));
        int clarityScore = wordCount >= 10 ? 88 : (wordCount >= 6 ? 76 : 60);
        if (wordCount > 55) {
            clarityScore = Math.max(70, clarityScore - 8);
        }

        // Verifica aderência ao tempo verbal / intenção da etapa
        String stepLower = step.label().toLowerCase(Locale.ROOT);
        boolean matchesStepIntent = true;
        String stepTip = "";
        if ("yesterday".equals(stepLower) || "problem solving".equals(stepLower) || "technical project".equals(stepLower)) {
            matchesStepIntent = PAST_VERB_PATTERN.matcher(answer).find();
            if (!matchesStepIntent) {
                clarityScore = Math.max(55, clarityScore - 10);
                stepTip = "Para relatar entregas concluídas, prefira verbos no passado simples (worked, fixed, built, tested).";
            }
        } else if ("today".equals(stepLower)) {
            matchesStepIntent = FUTURE_PLAN_PATTERN.matcher(answer).find();
            if (!matchesStepIntent) {
                clarityScore = Math.max(58, clarityScore - 8);
                stepTip = "Deixe o plano do dia explícito usando 'Today I will...' ou 'I plan to...'.";
            }
        } else if ("blockers".equals(stepLower)) {
            matchesStepIntent = BLOCKER_PATTERN.matcher(answer).find();
            if (!matchesStepIntent) {
                clarityScore = Math.max(60, clarityScore - 8);
                stepTip = "Em blockers, sinalize claramente se há impedimentos ('No blockers right now' ou 'I am blocked by...').";
            }
        }

        int techHits = countMatches(TECH_TERMS_PATTERN, answer);
        int vocabScore = Math.min(96, 62 + (techHits * 11));
        if (wordCount < 5) {
            vocabScore = Math.min(vocabScore, 58);
        }

        StringBuilder feedback = new StringBuilder();
        if (grammarAnalysis.issues().isEmpty()) {
            feedback.append("Estrutura gramatical limpa e compreensível para a etapa **")
                    .append(step.label()).append("**. ");
        } else {
            PraticaAtivaSkill.GrammarIssue firstIssue = grammarAnalysis.issues().getFirst();
            feedback.append("Encontrei ").append(grammarAnalysis.issues().size())
                    .append(" ponto(s) gramatical(is) de atenção: ")
                    .append(firstIssue.title()).append(" — ").append(firstIssue.explanation()).append(" ");
        }
        if (!stepTip.isBlank()) {
            feedback.append(stepTip).append(" ");
        }
        if (techHits >= 2) {
            feedback.append("Ótimo uso de vocabulário técnico em inglês!");
        } else {
            feedback.append("Você pode enriquecer a resposta com termos como API, endpoint, pull request, test ou blocker.");
        }

        String naturalVersion = !grammarAnalysis.naturalSuggestion().isBlank() && !grammarAnalysis.issues().isEmpty()
                ? grammarAnalysis.naturalSuggestion()
                : (wordCount < 6 ? step.exampleAnswer() : answer);

        String strength = techHits >= 2
                ? "Uso natural de vocabulário técnico (" + step.label() + ")."
                : (grammarAnalysis.issues().isEmpty()
                        ? "Boa clareza e ausência de erros típicos de tradução literal."
                        : "Comunicação objetiva mantendo o foco na pergunta.");

        String improvement = !grammarAnalysis.issues().isEmpty()
                ? grammarAnalysis.issues().getFirst().title() + ": " + grammarAnalysis.issues().getFirst().explanation()
                : (!stepTip.isBlank()
                        ? stepTip
                        : "Inclua 1 detalhe técnico extra (ferramenta, teste ou métrica) para soar ainda mais sênior.");

        return new EvaluatedTurnResult(
                new SimulationResponse.TurnEvaluation(
                        stepIndex,
                        step.label(),
                        clampScore(clarityScore),
                        clampScore(grammarScore),
                        clampScore(vocabScore),
                        feedback.toString().trim(),
                        naturalVersion,
                        strength,
                        improvement
                ),
                "Avaliador Local Dinga AI (Modo Baixa Conectividade)"
        );
    }

    private SimulationResponse.FinalReport buildFinalReport(
            String mode,
            String level,
            List<SimulationTurn> turns,
            SimulationResponse.TurnEvaluation lastEval
    ) {
        int avgClarity = (int) Math.round(turns.stream().mapToInt(SimulationTurn::clarityScore).average().orElse(75));
        int avgGrammar = (int) Math.round(turns.stream().mapToInt(SimulationTurn::grammarScore).average().orElse(75));
        int avgVocab = (int) Math.round(turns.stream().mapToInt(SimulationTurn::technicalVocabScore).average().orElse(75));
        int overall = (int) Math.round((avgClarity + avgGrammar + avgVocab) / 3.0);

        Set<String> strengths = new LinkedHashSet<>();
        Set<String> improvements = new LinkedHashSet<>();

        if (avgClarity >= 80) {
            strengths.add("Boa clareza e objetividade na condução das " + turns.size() + " etapas.");
        } else {
            improvements.add("Expandir respostas muito curtas usando estrutura Contexto → Ação → Resultado.");
        }
        if (avgGrammar >= 82) {
            strengths.add("Consistência gramatical sem armadilhas frequentes de tradução literal.");
        } else {
            improvements.add("Revisar verbos no passado simples e preposições técnicas nas frases corrigidas.");
        }
        if (avgVocab >= 80) {
            strengths.add("Uso consistente de vocabulário técnico de tecnologia e engenharia de software.");
        } else {
            improvements.add("Incorporar termos técnicos do Microsoft Learn e da rotina ágil (endpoint, PR, fallback, blocker).");
        }
        if (lastEval != null && !lastEval.strength().isBlank()) {
            strengths.add(lastEval.strength());
        }
        if (lastEval != null && !lastEval.improvement().isBlank() && improvements.size() < 3) {
            improvements.add(lastEval.improvement());
        }
        if (strengths.isEmpty()) {
            strengths.add("Completou todas as etapas da simulação em inglês com autonomia.");
        }

        String summary = overall >= 85
                ? "Excelente desempenho no simulador de " + modeTitle(mode) + " (nível " + level + ")! Suas respostas demonstram segurança e clareza profissional."
                : (overall >= 70
                        ? "Bom desempenho geral no simulador de " + modeTitle(mode) + " (" + level + "). Pequenos ajustes de gramática e vocabulário técnico vão elevar sua fluência."
                        : "Ótimo primeiro passo no simulador de " + modeTitle(mode) + "! Pratique repetir em voz alta as versões naturais sugeridas e salve os termos no Caderno de Vocabulário.");

        String nextStep = overall >= 85
                ? "Experimente subir um nível CEFR ou alternar para o modo " + ("daily".equals(mode) ? "Entrevista Técnica" : "Daily Standup") + "."
                : "Salve as frases corrigidas no Caderno de Vocabulário (SRS) e refaça a rodada ouvindo a pronúncia em 0.75x.";

        return new SimulationResponse.FinalReport(
                avgClarity,
                avgGrammar,
                avgVocab,
                overall,
                summary,
                List.copyOf(strengths),
                List.copyOf(improvements),
                nextStep
        );
    }

    private static int countMatches(Pattern pattern, String text) {
        int count = 0;
        var matcher = pattern.matcher(text);
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int clampScore(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String normalizeMode(String raw) {
        if (raw != null && raw.trim().toLowerCase(Locale.ROOT).startsWith("interview")) {
            return "interview";
        }
        return "daily";
    }

    private static String normalizeLevel(String raw) {
        if (raw == null || raw.isBlank()) {
            return "B1";
        }
        String up = raw.trim().toUpperCase(Locale.ROOT);
        return switch (up) {
            case "A1", "A2", "B1", "B2" -> up;
            default -> "B1";
        };
    }

    private static List<StepTemplate> stepsFor(String mode) {
        return "interview".equals(mode) ? INTERVIEW_STEPS : DAILY_STEPS;
    }

    private static String modeTitle(String mode) {
        return "interview".equals(mode) ? "Entrevista Técnica" : "Daily Standup";
    }

    private record StepTemplate(String label, String question, String hint, String exampleAnswer) {
    }

    private record EvaluatedTurnResult(SimulationResponse.TurnEvaluation evaluation, String executionMode) {
    }
}
