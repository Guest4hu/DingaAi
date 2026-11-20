package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Resumo de um documento curado disponível na biblioteca RAG.
 */
public record KnowledgeDocumentSummary(
        String docId,
        String title,
        String level,
        String category,
        String sourceRef,
        int chunkCount,
        List<String> tags
) {
    public KnowledgeDocumentSummary {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }

    public KnowledgeDocumentSummary(
            String docId,
            String title,
            String level,
            String category,
            String sourceRef,
            int chunkCount
    ) {
        this(docId, title, level, category, sourceRef, chunkCount, List.of());
    }
}
