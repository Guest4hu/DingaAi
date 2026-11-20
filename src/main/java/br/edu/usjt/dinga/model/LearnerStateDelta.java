package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Atualização incremental sugerida pelo backend após um turno do tutor
 * para que o cliente atualize o LearnerState local (LGPD).
 */
public record LearnerStateDelta(
        List<String> addedWeakPoints,
        List<String> addedMasteredTopics,
        String focusTopic
) {
    public LearnerStateDelta {
        addedWeakPoints = addedWeakPoints == null ? List.of() : List.copyOf(addedWeakPoints);
        addedMasteredTopics = addedMasteredTopics == null ? List.of() : List.copyOf(addedMasteredTopics);
        focusTopic = focusTopic == null ? "" : focusTopic.trim();
    }

    public static LearnerStateDelta empty() {
        return new LearnerStateDelta(List.of(), List.of(), "");
    }
}
