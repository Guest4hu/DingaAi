package br.edu.usjt.dinga.model;

/**
 * Resumo de um documento curado disponível na biblioteca RAG.
 */
public record KnowledgeDocumentSummary(
        String docId,
        String title,
        String level,
        String category,
        String sourceRef,
        int chunkCount
) {
}
