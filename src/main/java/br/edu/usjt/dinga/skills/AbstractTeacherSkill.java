package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.ChatTurn;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import java.util.List;
import java.util.Optional;

/**
 * Base compartilhada para as Skills da Dinga AI, com memória de conversa para avaliar
 * respostas aos micro-desafios e formatação ancorada no RAG.
 */
public abstract class AbstractTeacherSkill implements TeacherSkill {

    private final SkillDefinition definition;

    protected AbstractTeacherSkill(SkillDefinition definition) {
        this.definition = definition;
    }

    @Override
    public SkillDefinition definition() {
        return definition;
    }

    protected static int scoreKeywords(String message, String... keywords) {
        String normalized = Bm25RagRetriever.normalizeText(message);
        int score = 0;
        for (String keyword : keywords) {
            if (normalized.contains(Bm25RagRetriever.normalizeText(keyword))) {
                score += 10;
            }
        }
        return score;
    }

    @Override
    public String buildSystemPrompt(ChatRequest request, List<KnowledgeChunk> chunks) {
        StringBuilder sb = new StringBuilder();
        sb.append("Você é a Dinga AI, tutora inclusiva e ética de idiomas (Projeto Educação de Idiomas — Caso 3).\n");
        sb.append("Skill ativa: ").append(definition.name()).append(" (").append(definition.id()).append(")\n");
        sb.append("Regra pedagógica obrigatória: ").append(definition.pedagogyRule()).append("\n");
        sb.append("Perfil do aluno: Nível ").append(request.level())
                .append(" | Objetivo: ").append(request.goal()).append("\n");
        if (request.lowDataMode()) {
            sb.append("Modo baixa conectividade ativo: seja objetivo, use frases curtas e sem texto excessivo.\n");
        }
        sb.append("Responda em português claro com exemplos práticos em inglês (entre aspas duplas), NÃO dê cola pronta sem explicação, ");
        sb.append("ancore sua explicação nos trechos da base curada abaixo e termine com um convite ativo à prática.\n\n");

        if (request.history() != null && !request.history().isEmpty()) {
            sb.append("=== MEMÓRIA DE CONVERSA (ÚLTIMOS TURNOS) ===\n");
            List<ChatTurn> history = request.history();
            int start = Math.max(0, history.size() - 3);
            for (int i = start; i < history.size(); i++) {
                ChatTurn turn = history.get(i);
                sb.append("Turno ").append(i - start + 1).append(":\n");
                if (!turn.question().isBlank()) {
                    sb.append("- Aluno disse: ").append(turn.question()).append("\n");
                }
                if (!turn.practicePrompt().isBlank()) {
                    sb.append("- Micro-desafio proposto pela Dinga AI: ").append(turn.practicePrompt()).append("\n");
                }
            }
            sb.append("DIRETRIZ DE MEMÓRIA ATIVA: Se a mensagem atual do aluno for uma tentativa de responder ao último micro-desafio acima, ");
            sb.append("inicie sua resposta avaliando, elogiando acertos e corrigindo construtivamente a tentativa dele na hora!\n\n");
        }

        sb.append("=== FONTES CURADAS DO RAG ===\n");
        if (chunks.isEmpty()) {
            sb.append("(Nenhum trecho específico encontrado na base curada; admita limites com transparência e foque na orientação didática.)\n");
        } else {
            for (int i = 0; i < chunks.size(); i++) {
                KnowledgeChunk c = chunks.get(i);
                sb.append("[").append(i + 1).append("] ").append(c.title())
                        .append(" — ").append(c.section())
                        .append(" (Nível: ").append(c.level()).append(")\n")
                        .append(c.content()).append("\n\n");
            }
        }
        return sb.toString();
    }

    protected String formatGroundedLocalReply(
            ChatRequest request,
            List<KnowledgeChunk> chunks,
            String intro,
            String coachingTip
    ) {
        StringBuilder sb = new StringBuilder();

        Optional<String> challengeFeedback = evaluatePreviousChallengeIfApplicable(request);
        if (challengeFeedback.isPresent()) {
            sb.append(challengeFeedback.get()).append("\n\n");
        }

        sb.append(intro).append("\n\n");

        if (chunks.isEmpty()) {
            sb.append("Não encontrei um trecho exato na biblioteca curada para essa busca específica. ");
            sb.append("Para manter a confiabilidade (sem inventar informações), tente reformular com termos como ");
            sb.append("**presente simples**, **passado**, **Microsoft Learn**, **API**, **falsos cognatos**, **daily** ou **LGPD/MTG-IA**.\n\n");
        } else {
            int maxChunks = request.lowDataMode() ? Math.min(2, chunks.size()) : chunks.size();
            sb.append("**Pontos-chave encontrados na biblioteca curada (RAG):**\n");
            for (int i = 0; i < maxChunks; i++) {
                KnowledgeChunk c = chunks.get(i);
                sb.append("\n### ").append(i + 1).append(". ").append(c.section())
                        .append(" _(").append(c.title()).append(" · Nível ").append(c.level()).append(")_\n");
                sb.append(trimForMode(c.content(), request.lowDataMode())).append("\n");
            }
            sb.append("\n");
        }

        sb.append("**Orientação da Skill (`").append(definition.name()).append("`):** ").append(coachingTip);
        return sb.toString().trim();
    }

    /**
     * Avalia a tentativa do aluno caso haja memória de conversa com um micro-desafio anterior.
     */
    protected Optional<String> evaluatePreviousChallengeIfApplicable(ChatRequest request) {
        Optional<ChatTurn> maybeLast = request.lastTurn();
        if (maybeLast.isEmpty()) {
            return Optional.empty();
        }
        ChatTurn last = maybeLast.get();
        String challenge = last.practicePrompt();
        if (challenge.isBlank()) {
            return Optional.empty();
        }

        String msg = request.message().trim();
        String normMsg = Bm25RagRetriever.normalizeText(msg);
        String normChallenge = Bm25RagRetriever.normalizeText(challenge);

        // Se for uma nova pergunta independente em português (não uma resposta ao desafio), não força avaliação
        boolean looksLikeNewStandaloneQuestion = msg.endsWith("?")
                && (normMsg.startsWith("qual ") || normMsg.startsWith("o que ") || normMsg.startsWith("como ")
                || normMsg.startsWith("onde ") || normMsg.startsWith("quais ") || normMsg.startsWith("por que "))
                && !normMsg.contains("certo") && !normMsg.contains("correto") && !normMsg.contains("acertei");
        if (looksLikeNewStandaloneQuestion) {
            return Optional.empty();
        }

        StringBuilder eval = new StringBuilder();
        eval.append("### Avaliação do seu Micro-desafio (Memória de Conversa)\n");
        eval.append("- **Desafio anterior:** _\"").append(challenge).append("\"_\n");
        eval.append("- **Sua resposta:** _\"").append(msg).append("\"_\n");

        // Caso 1: Desafio do Tutor Socrático ("Na verdade, eu estudo tecnologia todas as noites" / actually vs currently)
        if (normChallenge.contains("na verdade") || normChallenge.contains("falso cognato")) {
            if (normMsg.contains("actually")) {
                eval.append("- **Feedback da Dinga AI:** Excelente! Você usou **\"actually\"** corretamente no sentido de *\"na verdade\"* (evitando o falso cognato com *atualmente*).\n");
                if (normMsg.contains("study") && (normMsg.contains("technology") || normMsg.contains("tech"))) {
                    eval.append("- **Estrutura em inglês:** Muito bem construída! Modelo de referência: *\"Actually, I study technology every night.\"*");
                } else {
                    eval.append("- **Sugestão de frase completa:** *\"Actually, I study technology every night.\"*");
                }
            } else if (normMsg.contains("currently") || normMsg.contains("nowadays")) {
                eval.append("- **Feedback da Dinga AI:** Quase lá! Você usou **currently/nowadays** (que significam *\"atualmente\"*), mas o desafio pedia *\"Na verdade\"*. Para *\"na verdade\"*, use **\"actually\"**!\n");
                eval.append("- **Correção sugerida:** *\"Actually, I study technology every night.\"*");
            } else {
                eval.append("- **Feedback da Dinga AI:** Boa tentativa! Para expressar *\"Na verdade\"* em inglês, lembre-se de usar **\"Actually\"** no início da frase: *\"Actually, I study technology every night.\"*");
            }
            return Optional.of(eval.toString());
        }

        // Caso 2: Desafio de Daily Standup (Yesterday / Today)
        if (normChallenge.contains("daily") || normChallenge.contains("what did you do yesterday")) {
            boolean hasPast = normMsg.contains("worked") || normMsg.contains("studied") || normMsg.contains("fixed")
                    || normMsg.contains("tested") || normMsg.contains("did") || normMsg.contains("created") || normMsg.contains("reviewed");
            boolean hasTrapWork = normMsg.contains("yesterday")
                    && normMsg.matches(".*\\bwork\\b.*")
                    && !normMsg.contains("worked");
            if (hasTrapWork) {
                eval.append("- **Feedback da Dinga AI:** Ótima iniciativa! Ajuste apenas o verbo no passado: com *\"Yesterday\"*, troque *\"work\"* por **\"worked\"**.\n");
            } else if (hasPast) {
                eval.append("- **Feedback da Dinga AI:** Muito bem! Você aplicou o verbo no **Simple Past** adequadamente para relatar o que fez ontem.\n");
            } else {
                eval.append("- **Feedback da Dinga AI:** Boa tentativa de *daily*! Lembre-se de usar o verbo no passado (*worked, studied, fixed*) para *yesterday* e *will / I'm going to* para *today*.\n");
            }
            eval.append("- **Exemplo modelo de Daily:** *\"Yesterday I worked on the backend API, and today I will write unit tests.\"*");
            return Optional.of(eval.toString());
        }

        // Caso 3: Desafio de Inglês Técnico (grounding / RAG vs hallucination)
        if (normChallenge.contains("grounding") || normChallenge.contains("hallucination")) {
            boolean mentionsGrounding = normMsg.contains("grounding") || normMsg.contains("rag") || normMsg.contains("fonte")
                    || normMsg.contains("base") || normMsg.contains("ancora") || normMsg.contains("documento");
            boolean mentionsHallucination = normMsg.contains("hallucination") || normMsg.contains("alucinacao")
                    || normMsg.contains("inventa") || normMsg.contains("falso") || normMsg.contains("sem fonte");
            if (mentionsGrounding && mentionsHallucination) {
                eval.append("- **Feedback da Dinga AI:** Resposta certeira! Você conectou perfeitamente **grounding/RAG** (ancoragem em documentos reais e verificáveis) com a prevenção de **hallucinations** (invenções do modelo).\n");
            } else {
                eval.append("- **Feedback da Dinga AI:** Bom resumo! Para deixar completo em entrevistas técnicas, destaque os dois lados: **Grounding (RAG)** ancora o modelo em fontes confiáveis, enquanto **Hallucination** é quando a IA gera dados plausíveis mas falsos.\n");
            }
            eval.append("- **Exemplo em inglês técnico:** *\"Grounding with RAG anchors the model in trusted documents to prevent hallucinations.\"*");
            return Optional.of(eval.toString());
        }

        // Caso 4: Desafio de Diagnóstico & Apresentação Pessoal (quem você é e o que está estudando)
        if (normChallenge.contains("quem voce e") || normChallenge.contains("estudando hoje")) {
            if (normMsg.contains("i am") || normMsg.contains("i'm") || normMsg.contains("my name") || normMsg.contains("study")) {
                eval.append("- **Feedback da Dinga AI:** Parabéns pela apresentação em inglês! Você já estruturou sua identidade e rotina de estudos com clareza.\n");
            } else {
                eval.append("- **Feedback da Dinga AI:** Ótimo começo! Tente combinar **\"I am...\"** (quem você é) com **\"I am studying...\"** (o que está estudando hoje).\n");
            }
            eval.append("- **Exemplo para praticar pronúncia:** *\"I am a technology student and today I am studying English with Dinga AI.\"*");
            return Optional.of(eval.toString());
        }

        // Caso geral (qualquer outro micro-desafio ou revisão)
        if (normMsg.contains("have") && normMsg.contains("year")) {
            eval.append("- **Correção gramatical:** Para idade em inglês, substitua *\"I have X years\"* por **\"I am X years old\"**.\n");
        } else if (normMsg.contains("informations")) {
            eval.append("- **Correção gramatical:** *Information* é incontável em inglês; use sempre **\"information\"** no singular.\n");
        } else {
            eval.append("- **Feedback da Dinga AI:** Recebi sua resposta ao desafio! Você demonstrou engajamento ativo no método socrático. Compare sua construção com as fontes curadas abaixo para refinar ainda mais seu vocabulário.");
        }
        return Optional.of(eval.toString());
    }

    private static String trimForMode(String text, boolean lowDataMode) {
        if (!lowDataMode || text.length() <= 380) {
            return text;
        }
        return text.substring(0, 380).trim() + "...";
    }
}
