package br.edu.usjt.dinga.model;

/**
 * Turno anterior da conversa mantido apenas na sessão do navegador (conformidade LGPD)
 * e enviado ao tutor para avaliação contextual do micro-desafio.
 */
public record ChatTurn(
        String question,
        String reply,
        String practicePrompt,
        String skillId
) {
    public ChatTurn {
        question = question == null ? "" : question.trim();
        reply = reply == null ? "" : reply.trim();
        practicePrompt = practicePrompt == null ? "" : practicePrompt.trim();
        skillId = skillId == null ? "" : skillId.trim();
    }
}
