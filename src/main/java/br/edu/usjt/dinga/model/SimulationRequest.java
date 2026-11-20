package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Requisição para iniciar ou avançar uma rodada do Simulador de Daily Standup / Entrevista.
 */
public record SimulationRequest(
        String mode,
        String level,
        Integer stepIndex,
        String answer,
        List<SimulationTurn> history
) {
    public SimulationRequest {
        mode = (mode == null || mode.isBlank()) ? "daily" : mode.trim().toLowerCase();
        level = (level == null || level.isBlank()) ? "B1" : level.trim().toUpperCase();
        stepIndex = (stepIndex == null || stepIndex < 1) ? 1 : stepIndex;
        answer = answer == null ? "" : answer.trim();
        history = history == null ? List.of() : List.copyOf(history);
    }
}
