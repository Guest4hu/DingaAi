package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Resposta de cada etapa do Simulador multi-turno, incluindo avaliação do turno e boletim final ao concluir.
 */
public record SimulationResponse(
        String mode,
        String modeTitle,
        String level,
        int currentStep,
        int totalSteps,
        boolean completed,
        String stepLabel,
        String question,
        String hint,
        String exampleAnswer,
        TurnEvaluation turnEvaluation,
        FinalReport finalReport,
        String executionMode,
        boolean piiMasked
) {
    public record TurnEvaluation(
            int stepIndex,
            String stepLabel,
            int clarityScore,
            int grammarScore,
            int technicalVocabScore,
            String feedback,
            String naturalVersion,
            String strength,
            String improvement
    ) {
    }

    public record FinalReport(
            int clarityScore,
            int grammarScore,
            int technicalVocabScore,
            int overallScore,
            String summary,
            List<String> strengths,
            List<String> improvements,
            String recommendedNextStep
    ) {
    }
}
