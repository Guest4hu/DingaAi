package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.llm.HybridLlmClient;
import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Skill guardiã do Modelo de Tripla Governança (MTG-IA), LGPD, EU AI Act e ODS 4, 10 e 16.
 */
@Component
public class GuardiaoEticoMtgSkill extends AbstractTeacherSkill {

    public GuardiaoEticoMtgSkill() {
        super(new SkillDefinition(
                "guardiao-etico-mtg",
                "Guardião Ético MTG-IA",
                "Ética & Governança",
                "Explica o Modelo de Tripla Governança (MTG-IA), LGPD, EU AI Act, UNESCO e alinhamento aos ODS.",
                "Promover transparência algorítmica, Human-in-the-loop e uso crítico sem substituição humana.",
                "ODS 4, ODS 10 e ODS 16 (Transparência, equidade e proteção de dados)",
                "Como a Dinga AI aplica o MTG-IA, LGPD e os ODS 4, 10 e 16 na prática?"
        ));
    }

    @Override
    public HybridLlmClient.GenerationOptions generationOptions() {
        return HybridLlmClient.GenerationOptions.of(0.20, 0.80);
    }

    @Override
    protected String buildFewShotExamples(String level, boolean lowDataMode) {
        return switch (level) {
            case "A1" -> """
                    Entrada: "Posso colocar meu CPF no chat?"
                    Resposta ideal: Não precisa e não é seguro! Pela **LGPD**, protegemos seus dados pessoais: se você digitar CPF, e-mail ou telefone, a Dinga AI mascara automaticamente. Exemplo: "Protect your personal data online." Como você explicaria esse cuidado a um colega?
                    """;
            case "A2" -> """
                    Entrada: "Por que a Dinga AI mostra fontes RAG e um selo no rodapé?"
                    Resposta ideal: Isso faz parte da transparência do **MTG-IA**: você sabe que o texto foi assistido por IA e pode conferir as fontes. Exemplo: "AI suggests, and the human validates the answer." O que você confere primeiro ao ler uma resposta de IA?
                    """;
            case "B2" -> """
                    Entrada: "Como a plataforma alinha LGPD, EU AI Act e Human-in-the-Loop?"
                    Resposta ideal: A Dinga AI opera sem persistir PII no servidor (minimização LGPD), rotula toda saída com watermark rastreável (transparência alinhada ao EU AI Act) e mantém supervisão humana ativa via fontes RAG e botão de relato de viés. Exemplo: "Human-in-the-loop governance keeps the learner accountable for validating AI outputs." Qual risco ético você priorizaria auditar em uma edtech?
                    """;
            default -> """
                    Entrada: "O que é o Modelo de Tripla Governança (MTG-IA)?"
                    Resposta ideal: O **MTG-IA** une 3 pilares: transparência algorítmica (selo de IA + fontes RAG), privacidade por padrão (LGPD sem salvar dados pessoais no servidor) e protagonismo humano (*Human-in-the-loop*). Exemplo: "Responsible AI combines transparency, privacy, and human oversight." Como você validaria uma resposta da IA antes de usá-la em um trabalho?
                    """;
        };
    }

    @Override
    public int matchScore(ChatRequest request) {
        return scoreKeywords(
                request.message(),
                "mtg", "etica", "lgpd", "ai act", "unesco", "ods", "vies", "bias",
                "watermark", "rotulagem", "privacidade", "governanca", "plagio",
                "humano no circuito", "human-in-the-loop", "responsible ai", "seguranca", "transparencia"
        );
    }

    @Override
    public String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks) {
        String intro = "Ativei o **Guardião Ético MTG-IA** — camada responsável por transparência, privacidade e impacto social nesta plataforma:";
        return formatGroundedLocalReply(
                request,
                chunks,
                intro,
                "Lembre-se do lema do projeto: 'A IA sugere, você valida'. Se notar qualquer erro ou viés, clique em 'Reportar erro/viés' abaixo da resposta para registrar na auditoria MTG-IA."
        );
    }

    @Override
    public String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        return "Reflexão crítica: como você validaria em duas fontes confiáveis uma explicação técnica gerada por IA antes de usá-la em um trabalho?";
    }
}
