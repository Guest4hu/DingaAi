package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Skill padrão: tutoria socrática que explica o porquê e guia o aluno sem dar respostas prontas acríticas.
 */
@Component
public class TutorSocraticoSkill extends AbstractTeacherSkill {

    public TutorSocraticoSkill() {
        super(new SkillDefinition(
                "tutor-socratico",
                "Tutor Socrático",
                "Pedagogia Ativa",
                "Explica gramática, vocabulário e falsos cognatos passo a passo usando perguntas guiadas.",
                "Nunca entregar tradução pronta sem explicar a regra e convidar o aluno a pensar criticamente.",
                "ODS 4 (Educação de Qualidade e pensamento crítico)",
                "Qual a diferença entre 'actually' e 'currently' e como não errar em uma frase?"
        ));
    }

    @Override
    public int matchScore(ChatRequest request) {
        // Pontuação base como fallback padrão e afinidade explícita para dúvidas de gramática/vocabulário
        return 5 + scoreKeywords(
                request.message(),
                "diferenca", "significa", "gramatica", "verbo", "traduz", "explica",
                "falso cognato", "actually", "pretend", "present", "past", "porque", "como usa"
        );
    }

    @Override
    public String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks) {
        String intro = String.format(
                "Vamos investigar sua dúvida no **nível %s** com foco em entendimento real (sem decoreba):",
                request.level()
        );
        return formatGroundedLocalReply(
                request,
                chunks,
                intro,
                "Antes de seguir, tente aplicar a regra em uma frase sua. Se quiser, envie sua tentativa aqui que eu reviso os pontos de melhoria com você!"
        );
    }

    @Override
    public String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        return "Desafio rápido: como você diria em inglês 'Na verdade, eu estudo tecnologia todas as noites'? (Cuidado com o falso cognato!)";
    }
}
