package br.edu.usjt.dinga.model;

/**
 * Resultado da correção imediata de uma questão do Modo Quiz / Desafio Rápido.
 */
public record QuizAnswerResponse(
        String questionId,
        boolean correct,
        int selectedIndex,
        int correctIndex,
        String explanation,
        String englishExample,
        String sourceDocId,
        String followUpPrompt
) {
}
