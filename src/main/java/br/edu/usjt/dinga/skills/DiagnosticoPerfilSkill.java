package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Skill de nivelamento inicial, diagnóstico de objetivo e recomendação de trilha inclusiva.
 */
@Component
public class DiagnosticoPerfilSkill extends AbstractTeacherSkill {

    public DiagnosticoPerfilSkill() {
        super(new SkillDefinition(
                "diagnostico-perfil",
                "Diagnóstico & Trilha",
                "Nivelamento",
                "Identifica o nível atual (A1–B2), objetivo de estudo e recomenda uma trilha adaptada sem criar bolhas.",
                "Adaptar ritmo e formato ao contexto do estudante mantendo diversidade cultural e autonomia.",
                "ODS 4 e ODS 10 (Inclusão e acesso personalizado)",
                "Quero montar meu plano de estudos: estou no nível A2 e preciso ler documentação técnica em inglês."
        ));
    }

    @Override
    public int matchScore(ChatRequest request) {
        return scoreKeywords(
                request.message(),
                "nivel", "nivelamento", "comecar", "iniciante", "plano de estudo",
                "trilha", "diagnostico", "perfil", "por onde comeco", "meu objetivo", "a1", "a2", "b1", "b2"
        );
    }

    @Override
    public String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks) {
        String levelPlan = switch (request.level()) {
            case "A1" -> "1) Verbo *to be* e apresentações pessoais; 2) *Simple Present* com frases curtas da sua rotina; 3) Falsos cognatos básicos (*actually*, *push/pull*).";
            case "B1" -> "1) Técnicas de *skimming* e *scanning* no Microsoft Learn; 2) Vocabulário de IA, Cloud e APIs; 3) Modais (*must/should/may*) em manuais técnicos.";
            case "B2" -> "1) Estrutura de *daily standup* em inglês; 2) Argumentação técnica para entrevistas e e-mails; 3) Leitura crítica de diretrizes de IA Responsável.";
            default -> "1) Consolidação de *Simple Past* e conectores (*because, however*); 2) Falsos cognatos para brasileiros; 3) Primeiros passos em termos de tecnologia e IA.";
        };

        String intro = String.format(
                "Mapeamos seu perfil inicial: **Nível %s** | **Objetivo:** %s.\n\n**Trilha recomendada para começar hoje:**\n%s",
                request.level(),
                request.goal(),
                levelPlan
        );

        return formatGroundedLocalReply(
                request,
                chunks,
                intro,
                "Comece com 10 minutos por dia escrevendo 2 frases reais do seu cotidiano ou área de estudo — sem depender de tradução automática literal."
        );
    }

    @Override
    public String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        return "Escreva 2 frases curtas em inglês contando quem você é e o que está estudando hoje (pode misturar português se precisar de ajuda).";
    }
}
