package br.edu.usjt.dinga.model;

import java.util.List;
import java.util.Optional;

/**
 * Requisição de conversa enviada pelo estudante ao tutor Dinga AI.
 */
public record ChatRequest(
        String message,
        String level,
        String goal,
        String preferredSkill,
        Boolean lowDataMode,
        List<ChatTurn> history,
        LearnerState learnerState
) {
    public ChatRequest {
        message = message == null ? "" : message.trim();
        level = (level == null || level.isBlank()) ? "A2" : level.trim().toUpperCase();
        goal = (goal == null || goal.isBlank()) ? "Inglês para Tecnologia / Microsoft Learn" : goal.trim();
        preferredSkill = (preferredSkill == null || preferredSkill.isBlank()) ? "auto" : preferredSkill.trim();
        lowDataMode = Boolean.TRUE.equals(lowDataMode);
        history = history == null ? List.of() : List.copyOf(history);
        learnerState = learnerState == null ? LearnerState.empty() : learnerState;
    }

    public ChatRequest(
            String message,
            String level,
            String goal,
            String preferredSkill,
            Boolean lowDataMode
    ) {
        this(message, level, goal, preferredSkill, lowDataMode, List.of(), LearnerState.empty());
    }

    public ChatRequest(
            String message,
            String level,
            String goal,
            String preferredSkill,
            Boolean lowDataMode,
            List<ChatTurn> history
    ) {
        this(message, level, goal, preferredSkill, lowDataMode, history, LearnerState.empty());
    }

    public Optional<ChatTurn> lastTurn() {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(history.getLast());
    }
}
