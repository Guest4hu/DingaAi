package br.edu.usjt.dinga.model;

/**
 * Requisição de verificação de resposta no Modo Quiz / Desafio Rápido.
 */
public record QuizAnswerRequest(
        String questionId,
        int selectedIndex
) {
    public QuizAnswerRequest {
        questionId = questionId == null ? "" : questionId.trim();
    }
}
