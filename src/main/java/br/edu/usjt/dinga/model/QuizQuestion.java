package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Questão interativa do Modo Quiz / Desafio Rápido do Microsoft Learn.
 */
public record QuizQuestion(
        String id,
        String level,
        String category,
        String question,
        String codeOrDocSnippet,
        List<String> options,
        int correctIndex,
        String explanation,
        String englishExample,
        String sourceDocId
) {
}
