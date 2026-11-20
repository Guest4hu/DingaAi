package br.edu.usjt.dinga.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Memória pedagógica compacta do aluno (mantida apenas no cliente — conformidade LGPD).
 * Injetada no prompt para conectar explicações entre turnos sem persistência no servidor.
 */
public record LearnerState(
        List<String> weakPoints,
        List<String> masteredTopics,
        List<String> recentTopics,
        Integer quizAccuracyPercent
) {
    private static final int MAX_ITEMS = 8;
    private static final int MAX_RECENT_ITEMS = 6;
    private static final int MAX_ITEM_CHARS = 80;

    public LearnerState {
        weakPoints = normalizeList(weakPoints, MAX_ITEMS);
        masteredTopics = normalizeList(masteredTopics, MAX_ITEMS);
        recentTopics = normalizeList(recentTopics, MAX_RECENT_ITEMS);
        if (quizAccuracyPercent != null) {
            if (quizAccuracyPercent < 0) {
                quizAccuracyPercent = null;
            } else if (quizAccuracyPercent > 100) {
                quizAccuracyPercent = 100;
            }
        }
    }

    public static LearnerState empty() {
        return new LearnerState(List.of(), List.of(), List.of(), null);
    }

    public boolean isEmpty() {
        return weakPoints.isEmpty()
                && masteredTopics.isEmpty()
                && recentTopics.isEmpty()
                && quizAccuracyPercent == null;
    }

    public LearnerState sanitized(UnaryOperator<String> sanitizer) {
        if (isEmpty() || sanitizer == null) {
            return this;
        }
        return new LearnerState(
                weakPoints.stream().map(sanitizer).toList(),
                masteredTopics.stream().map(sanitizer).toList(),
                recentTopics.stream().map(sanitizer).toList(),
                quizAccuracyPercent
        );
    }

    private static List<String> normalizeList(List<String> raw, int maxItems) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        Set<String> seenLower = new LinkedHashSet<>();
        List<String> clean = new ArrayList<>();
        for (String item : raw) {
            if (item == null) {
                continue;
            }
            String trimmed = item.replaceAll("\\s+", " ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.length() > MAX_ITEM_CHARS) {
                trimmed = trimmed.substring(0, MAX_ITEM_CHARS).trim();
            }
            String key = trimmed.toLowerCase();
            if (seenLower.add(key)) {
                clean.add(trimmed);
                if (clean.size() >= maxItems) {
                    break;
                }
            }
        }
        return List.copyOf(clean);
    }
}
