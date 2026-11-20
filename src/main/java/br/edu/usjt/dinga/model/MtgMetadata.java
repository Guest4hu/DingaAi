package br.edu.usjt.dinga.model;

/**
 * Metadados de transparência, rastreabilidade, guardrails ativos (LGPD/anti-plágio)
 * e telemetria de baixa conectividade (MTG-IA + Caso 3).
 */
public record MtgMetadata(
        String aiGeneratedLabel,
        String watermarkId,
        String executionMode,
        int sourcesCount,
        String humanInTheLoopTip,
        String privacyNote,
        long responseTimeMs,
        double payloadSizeKb,
        int estimatedBandwidthSavedPercent,
        boolean lowDataModeActive,
        boolean piiMasked,
        boolean antiPlagiarismTriggered,
        double groundednessScore
) {
    public MtgMetadata {
        groundednessScore = Math.max(0.0, Math.min(1.0, Math.round(groundednessScore * 100.0) / 100.0));
    }

    public MtgMetadata(
            String aiGeneratedLabel,
            String watermarkId,
            String executionMode,
            int sourcesCount,
            String humanInTheLoopTip,
            String privacyNote
    ) {
        this(aiGeneratedLabel, watermarkId, executionMode, sourcesCount, humanInTheLoopTip, privacyNote,
                1L, 1.0, 92, false, false, false, 0.0);
    }

    public MtgMetadata(
            String aiGeneratedLabel,
            String watermarkId,
            String executionMode,
            int sourcesCount,
            String humanInTheLoopTip,
            String privacyNote,
            long responseTimeMs,
            double payloadSizeKb,
            int estimatedBandwidthSavedPercent,
            boolean lowDataModeActive
    ) {
        this(aiGeneratedLabel, watermarkId, executionMode, sourcesCount, humanInTheLoopTip, privacyNote,
                responseTimeMs, payloadSizeKb, estimatedBandwidthSavedPercent, lowDataModeActive, false, false, 0.0);
    }

    public MtgMetadata(
            String aiGeneratedLabel,
            String watermarkId,
            String executionMode,
            int sourcesCount,
            String humanInTheLoopTip,
            String privacyNote,
            long responseTimeMs,
            double payloadSizeKb,
            int estimatedBandwidthSavedPercent,
            boolean lowDataModeActive,
            boolean piiMasked,
            boolean antiPlagiarismTriggered
    ) {
        this(aiGeneratedLabel, watermarkId, executionMode, sourcesCount, humanInTheLoopTip, privacyNote,
                responseTimeMs, payloadSizeKb, estimatedBandwidthSavedPercent, lowDataModeActive,
                piiMasked, antiPlagiarismTriggered, 0.0);
    }
}
