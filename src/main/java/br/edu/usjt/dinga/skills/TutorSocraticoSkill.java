package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.llm.HybridLlmClient;
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
    public HybridLlmClient.GenerationOptions generationOptions() {
        return HybridLlmClient.GenerationOptions.of(0.55, 0.92);
    }

    @Override
    protected String buildFewShotExamples(String level, boolean lowDataMode) {
        return switch (level) {
            case "A1" -> """
                    Entrada: "Como falo 'eu trabalho com tecnologia' em inglês?"
                    Resposta ideal: No presente simples para rotina, usamos sujeito + verbo base: "I work with technology." E se fosse sobre um amigo (*he/she*)? Pense: o verbo ganha **-s** no final — como ficaria a frase?
                    """;
            case "B1" -> """
                    Entrada: "Por que 'information' não tem plural em inglês?"
                    Resposta ideal: **Information** é um substantivo incontável (*uncountable*), como *advice* e *feedback*, por isso nunca leva *-s*. Exemplo: "This documentation provides useful information." Como você pediria *algumas informações* sobre uma API sem usar plural?
                    """;
            case "B2" -> """
                    Entrada: "Qual a nuance entre 'I intend to' e 'I am planning to' em um plano técnico?"
                    Resposta ideal: **Intend to** soa mais formal e enfático sobre propósito (ótimo para RFCs e entrevistas), enquanto **plan to** é natural e colaborativo para dailies. Exemplo: "I intend to refactor the auth layer, and I plan to open the PR tomorrow." Como você reformularia uma meta da sua sprint com essa nuance?
                    """;
            default -> """
                    Entrada: "Qual a diferença entre 'actually' e 'currently'?"
                    Resposta ideal: **Actually** é falso cognato e significa **na verdade**; para **atualmente**, use **currently** ou **nowadays**. Exemplo: "Actually, I currently study English every night." Antes de avançar, tente montar 1 frase curta usando **actually**!
                    """;
        };
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
