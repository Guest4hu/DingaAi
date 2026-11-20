package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.SkillDefinition;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Roteador que seleciona automaticamente (ou manualmente) a Skill pedagógica adequada,
 * com suporte a Continuidade de Skill (Conversation Stickiness) em perguntas de continuação.
 */
@Component
public class SkillRouter {

    private static final int MAX_SHORT_FOLLOWUP_CHARS = 90;
    private static final int MAX_SHORT_FOLLOWUP_WORDS = 14;
    private static final Pattern CONTINUATION_CUE_PATTERN = Pattern.compile(
            "\\b(mais exemplos?|outro exemplo|outros exemplos|pode dar outro|me da outro|e no caso|e sobre|e na pratica|"
                    + "continue|continua|continuar|explica melhor|nao entendi|como assim|pode repetir|mais detalhes|"
                    + "aprofund[ae]|e como funciona|e se|entendi|certo|beleza|ok|sim|valeu|obrigad[oa]|"
                    + "give me another|more examples?|explain better|what about|go on)\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern GRAMMAR_DOMAIN_SHIFT_PATTERN = Pattern.compile(
            "\\b(diferenca|gramatica|verbo|traduz|falso cognato|actually|currently|pretend|present|past|significa)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private final Map<String, TeacherSkill> skillsById;
    private final List<TeacherSkill> orderedSkills;
    private final TeacherSkill defaultSkill;

    public SkillRouter(
            DiagnosticoPerfilSkill diagnostico,
            TutorSocraticoSkill tutor,
            InglesTecnicoSkill tecnico,
            PraticaAtivaSkill pratica,
            GuardiaoEticoMtgSkill etico
    ) {
        this.orderedSkills = List.of(diagnostico, tutor, tecnico, pratica, etico);
        Map<String, TeacherSkill> map = new LinkedHashMap<>();
        for (TeacherSkill skill : orderedSkills) {
            map.put(skill.definition().id(), skill);
        }
        this.skillsById = Map.copyOf(map);
        this.defaultSkill = tutor;
    }

    public RoutingDecision route(ChatRequest request) {
        String preferred = request.preferredSkill();
        if (preferred != null && !preferred.isBlank() && !"auto".equalsIgnoreCase(preferred)) {
            TeacherSkill chosen = skillsById.get(preferred);
            if (chosen != null) {
                return new RoutingDecision(
                        chosen,
                        "Selecionada manualmente pelo estudante/apresentador (" + chosen.definition().name() + ")."
                );
            }
        }

        TeacherSkill best = orderedSkills.stream()
                .max(Comparator.comparingInt(skill -> skill.matchScore(request)))
                .orElse(defaultSkill);

        int score = best.matchScore(request);

        // Continuidade de Skill (Conversation Stickiness): mantém a skill anterior em perguntas curtas/continuação
        // caso não haja mudança clara de intenção para outra skill.
        Optional<ChatTurn> maybeLastTurn = request.lastTurn();
        if (maybeLastTurn.isPresent()) {
            ChatTurn lastTurn = maybeLastTurn.get();
            TeacherSkill previousSkill = skillsById.get(lastTurn.skillId());
            if (previousSkill != null && previousSkill != best) {
                String normMsg = Bm25RagRetriever.normalizeText(request.message());
                boolean hasExplicitContinuationCue = CONTINUATION_CUE_PATTERN.matcher(normMsg).find();
                boolean idiomNaPraticaOnly = hasExplicitContinuationCue
                        && "pratica-ativa".equals(best.definition().id())
                        && score <= 10
                        && normMsg.contains("na pratica");
                boolean clearSpecializedShift = best != defaultSkill && score >= 10 && !idiomNaPraticaOnly;
                boolean hasGrammarDomainShift = GRAMMAR_DOMAIN_SHIFT_PATTERN.matcher(normMsg).find();
                boolean clearDefaultShift = best == defaultSkill
                        && score >= 20
                        && (!hasExplicitContinuationCue || hasGrammarDomainShift);

                if (!clearSpecializedShift && !clearDefaultShift && isContinuationOrShortFollowUp(request.message(), lastTurn)) {
                    return new RoutingDecision(
                            previousSkill,
                            "Continuidade de Skill (Stickiness): mantendo " + previousSkill.definition().name() + " do turno anterior."
                    );
                }
            }
        }

        String reason = best == defaultSkill && score <= 5
                ? "Roteamento automático padrão para tutoria socrática guiada."
                : "Roteamento automático por intenção da mensagem (pontuação de afinidade: " + score + ").";

        return new RoutingDecision(best, reason);
    }

    private static boolean isContinuationOrShortFollowUp(String message, ChatTurn lastTurn) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String trimmed = message.trim();
        String norm = Bm25RagRetriever.normalizeText(trimmed);
        if (CONTINUATION_CUE_PATTERN.matcher(norm).find()) {
            return true;
        }
        int wordCount = trimmed.split("\\s+").length;
        if (trimmed.length() <= MAX_SHORT_FOLLOWUP_CHARS && wordCount <= MAX_SHORT_FOLLOWUP_WORDS) {
            return true;
        }
        return !lastTurn.practicePrompt().isBlank() && !trimmed.endsWith("?");
    }

    public List<SkillDefinition> listDefinitions() {
        return orderedSkills.stream().map(TeacherSkill::definition).toList();
    }

    public record RoutingDecision(TeacherSkill skill, String reasoning) {
    }
}
