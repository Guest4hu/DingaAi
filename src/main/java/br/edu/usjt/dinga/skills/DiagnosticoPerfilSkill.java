package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.llm.HybridLlmClient;
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
    public HybridLlmClient.GenerationOptions generationOptions() {
        return HybridLlmClient.GenerationOptions.of(0.40, 0.90);
    }

    @Override
    protected String buildFewShotExamples(String level, boolean lowDataMode) {
        return switch (level) {
            case "A1" -> """
                    Entrada: "Estou começando do zero e quero ler inglês no computador."
                    Resposta ideal: Ótimo passo! No nível **A1**, comece com 10 min/dia: 1) apresentações com *to be* ("I am a student."); 2) comandos básicos de interface (*save, run, build*); 3) 2 frases curtas da sua rotina. Quer testar sua primeira frase agora?
                    """;
            case "B1" -> """
                    Entrada: "Sou B1 e quero estudar Microsoft Learn mais rápido."
                    Resposta ideal: Para o nível **B1**, sua trilha ideal combina: 1) *skimming/scanning* em títulos e blocos de código; 2) modais técnicos (*must/should/may*); 3) vocabulário de Cloud e IA ("Grounding anchors the model in trusted sources."). Qual tema do Microsoft Learn você quer destravar primeiro?
                    """;
            case "B2" -> """
                    Entrada: "Quero uma trilha B2 para entrevistas técnicas em inglês."
                    Resposta ideal: No nível **B2**, foque em clareza executiva: 1) método STAR em projetos técnicos; 2) vocabulário de trade-offs e code review ("We added a local fallback to reduce latency."); 3) simulações cronometradas de daily e entrevista. Qual cenário quer treinar hoje?
                    """;
            default -> """
                    Entrada: "Estou no A2 e quero melhorar meu inglês para tecnologia."
                    Resposta ideal: No nível **A2**, sua trilha prioriza: 1) *Simple Past* para relatar o que você fez (*worked, built*); 2) falsos cognatos críticos (*actually, pretend, push/pull*); 3) leitura guiada de termos de API. Exemplo: "Yesterday I studied API documentation." Vamos começar escrevendo 1 frase sobre seu dia?
                    """;
        };
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
