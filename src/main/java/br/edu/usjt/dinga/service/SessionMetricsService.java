package br.edu.usjt.dinga.service;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Service;

/**
 * Coleta métricas agregadas da sessão/execução do backend sem armazenar dados pessoais (LGPD):
 * acertos no quiz por categoria/nível, skills acionadas, guardrails LGPD/Anti-Plágio e economia de banda.
 */
@Service
public class SessionMetricsService {

    // Referência conservadora de uma página/aula multimídia tradicional (~180 KB por interação)
    private static final double TRADITIONAL_LESSON_INTERACTION_KB = 180.0;

    private final AtomicInteger totalChatTurns = new AtomicInteger(0);
    private final AtomicInteger lowDataChatTurns = new AtomicInteger(0);
    private final AtomicLong totalPayloadBytes = new AtomicLong(0);
    private final AtomicLong estimatedSavedBytes = new AtomicLong(0);
    private final AtomicInteger lgpdMaskCount = new AtomicInteger(0);
    private final AtomicInteger antiPlagiarismCount = new AtomicInteger(0);
    private final AtomicInteger rateLimitBlockedCount = new AtomicInteger(0);
    private final AtomicInteger simulationTurnsCount = new AtomicInteger(0);
    private final AtomicInteger simulationCompletedCount = new AtomicInteger(0);

    private final ConcurrentHashMap<String, AtomicInteger> skillCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuizCounter> quizByCategory = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, QuizCounter> quizByLevel = new ConcurrentHashMap<>();
    private final AtomicInteger totalQuizAnswered = new AtomicInteger(0);
    private final AtomicInteger totalQuizCorrect = new AtomicInteger(0);

    public void recordChatTurn(
            String skillId,
            boolean lowDataMode,
            double payloadSizeKb,
            boolean piiMasked,
            boolean antiPlagiarismTriggered
    ) {
        totalChatTurns.incrementAndGet();
        if (lowDataMode) {
            lowDataChatTurns.incrementAndGet();
        }
        long payloadBytes = Math.max(256L, Math.round(payloadSizeKb * 1024.0));
        totalPayloadBytes.addAndGet(payloadBytes);

        double referenceKb = lowDataMode ? TRADITIONAL_LESSON_INTERACTION_KB * 1.4 : TRADITIONAL_LESSON_INTERACTION_KB;
        double savedKb = Math.max(0.0, referenceKb - payloadSizeKb);
        estimatedSavedBytes.addAndGet(Math.round(savedKb * 1024.0));

        if (skillId != null && !skillId.isBlank()) {
            skillCounts.computeIfAbsent(skillId, k -> new AtomicInteger(0)).incrementAndGet();
        }
        if (piiMasked) {
            lgpdMaskCount.incrementAndGet();
        }
        if (antiPlagiarismTriggered) {
            antiPlagiarismCount.incrementAndGet();
        }
    }

    public void recordQuizAnswer(String category, String level, boolean correct) {
        totalQuizAnswered.incrementAndGet();
        if (correct) {
            totalQuizCorrect.incrementAndGet();
        }
        if (category != null && !category.isBlank()) {
            quizByCategory.computeIfAbsent(category, k -> new QuizCounter()).record(correct);
        }
        if (level != null && !level.isBlank()) {
            quizByLevel.computeIfAbsent(level.toUpperCase(), k -> new QuizCounter()).record(correct);
        }
    }

    public void recordSimulationTurn(boolean completed, boolean piiMasked) {
        simulationTurnsCount.incrementAndGet();
        if (completed) {
            simulationCompletedCount.incrementAndGet();
        }
        if (piiMasked) {
            lgpdMaskCount.incrementAndGet();
        }
    }

    public void recordRateLimitBlocked() {
        rateLimitBlockedCount.incrementAndGet();
    }

    public SessionMetricsSnapshot snapshot() {
        int qAnswered = totalQuizAnswered.get();
        int qCorrect = totalQuizCorrect.get();
        int overallQuizAccuracy = qAnswered > 0 ? (int) Math.round((qCorrect * 100.0) / qAnswered) : 0;

        Map<String, Integer> sortedSkills = new LinkedHashMap<>();
        skillCounts.entrySet().stream()
                .sorted(Map.Entry.<String, AtomicInteger>comparingByValue(Comparator.comparingInt(AtomicInteger::get)).reversed())
                .forEach(e -> sortedSkills.put(e.getKey(), e.getValue().get()));

        Map<String, QuizBucketStats> categoryStats = new LinkedHashMap<>();
        quizByCategory.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> categoryStats.put(e.getKey(), e.getValue().toStats()));

        Map<String, QuizBucketStats> levelStats = new LinkedHashMap<>();
        quizByLevel.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> levelStats.put(e.getKey(), e.getValue().toStats()));

        double payloadKb = round2(totalPayloadBytes.get() / 1024.0);
        double savedKb = round2(estimatedSavedBytes.get() / 1024.0);
        int savedPct = (payloadKb + savedKb) > 0
                ? (int) Math.round((savedKb * 100.0) / (payloadKb + savedKb))
                : 0;

        return new SessionMetricsSnapshot(
                totalChatTurns.get(),
                lowDataChatTurns.get(),
                payloadKb,
                savedKb,
                savedPct,
                lgpdMaskCount.get(),
                antiPlagiarismCount.get(),
                rateLimitBlockedCount.get(),
                simulationTurnsCount.get(),
                simulationCompletedCount.get(),
                qAnswered,
                qCorrect,
                overallQuizAccuracy,
                Collections.unmodifiableMap(sortedSkills),
                Collections.unmodifiableMap(categoryStats),
                Collections.unmodifiableMap(levelStats)
        );
    }

    public void reset() {
        totalChatTurns.set(0);
        lowDataChatTurns.set(0);
        totalPayloadBytes.set(0);
        estimatedSavedBytes.set(0);
        lgpdMaskCount.set(0);
        antiPlagiarismCount.set(0);
        rateLimitBlockedCount.set(0);
        simulationTurnsCount.set(0);
        simulationCompletedCount.set(0);
        totalQuizAnswered.set(0);
        totalQuizCorrect.set(0);
        skillCounts.clear();
        quizByCategory.clear();
        quizByLevel.clear();
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static final class QuizCounter {
        private final AtomicInteger answered = new AtomicInteger(0);
        private final AtomicInteger correct = new AtomicInteger(0);

        void record(boolean isCorrect) {
            answered.incrementAndGet();
            if (isCorrect) {
                correct.incrementAndGet();
            }
        }

        QuizBucketStats toStats() {
            int a = answered.get();
            int c = correct.get();
            int pct = a > 0 ? (int) Math.round((c * 100.0) / a) : 0;
            return new QuizBucketStats(a, c, pct);
        }
    }

    public record QuizBucketStats(int answered, int correct, int accuracyPercent) {
    }

    public record SessionMetricsSnapshot(
            int totalChatTurns,
            int lowDataChatTurns,
            double totalPayloadKb,
            double estimatedSavedKb,
            int estimatedSavedPercent,
            int lgpdMaskCount,
            int antiPlagiarismCount,
            int rateLimitBlockedCount,
            int simulationTurnsCount,
            int simulationCompletedCount,
            int totalQuizAnswered,
            int totalQuizCorrect,
            int quizAccuracyPercent,
            Map<String, Integer> skillUsage,
            Map<String, QuizBucketStats> quizByCategory,
            Map<String, QuizBucketStats> quizByLevel
    ) {
    }
}
