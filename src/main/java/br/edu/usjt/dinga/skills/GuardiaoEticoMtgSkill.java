package br.edu.usjt.dinga.skills;

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
