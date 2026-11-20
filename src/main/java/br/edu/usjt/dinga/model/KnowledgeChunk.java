package br.edu.usjt.dinga.model;

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
        double score
) {
    public KnowledgeChunk withScore(double newScore) {
        return new KnowledgeChunk(id, docId, title, level, category, sourceRef, section, content, newScore);
    }
}
