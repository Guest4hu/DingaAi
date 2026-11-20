package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Trecho indexado da base curada de conhecimento (RAG).
 */
public record KnowledgeChunk(
        String id,
        String docId,
        String title,
        String level,
        String category,
        String sourceRef,
        String section,
        String content,
        double score,
        List<String> tags
) {
    public KnowledgeChunk {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public KnowledgeChunk(
            String id,
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            String section,
            String content,
            double score
    ) {
        this(id, docId, title, level, category, sourceRef, section, content, score, List.of());
    }

    public KnowledgeChunk withScore(double newScore) {
        return new KnowledgeChunk(id, docId, title, level, category, sourceRef, section, content, newScore, tags);
    }
}
