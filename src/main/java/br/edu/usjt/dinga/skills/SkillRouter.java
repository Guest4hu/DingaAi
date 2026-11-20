package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.SkillDefinition;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Roteador que seleciona automaticamente (ou manualmente) a Skill pedagógica adequada.
 */
@Component
public class SkillRouter {

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
        String reason = best == defaultSkill && score <= 5
                ? "Roteamento automático padrão para tutoria socrática guiada."
                : "Roteamento automático por intenção da mensagem (pontuação de afinidade: " + score + ").";

        return new RoutingDecision(best, reason);
    }

    public List<SkillDefinition> listDefinitions() {
        return orderedSkills.stream().map(TeacherSkill::definition).toList();
    }

    public record RoutingDecision(TeacherSkill skill, String reasoning) {
    }
}
