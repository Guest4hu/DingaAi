package br.edu.usjt.dinga.model;

/**
 * Representa um turno já respondido no Simulador de Entrevista / Daily Standup.
 */
public record SimulationTurn(
        int stepIndex,
        String stepLabel,
        String question,
        String answer,
        int clarityScore,
        int grammarScore,
        int technicalVocabScore,
        String feedback,
        String naturalVersion
) {
    public SimulationTurn {
        stepLabel = stepLabel == null ? "" : stepLabel.trim();
        question = question == null ? "" : question.trim();
        answer = answer == null ? "" : answer.trim();
        feedback = feedback == null ? "" : feedback.trim();
        naturalVersion = naturalVersion == null ? "" : naturalVersion.trim();
    }
}
