package br.edu.usjt.dinga.model;

/**
 * Requisição para geração dinâmica de questões no Modo Quiz ancoradas na base RAG.
 */
public record QuizGenerationRequest(
        String topic,
        String level,
        String docId,
        Integer count
) {
    public QuizGenerationRequest {
        topic = topic == null ? "" : topic.trim();
        level = (level == null || level.isBlank() || "ALL".equalsIgnoreCase(level.trim()))
                ? "B1"
                : level.trim().toUpperCase();
        docId = (docId == null || "ALL".equalsIgnoreCase(docId.trim())) ? "" : docId.trim();
        count = (count == null || count < 1) ? 1 : Math.min(count, 5);
    }
}
