package br.edu.usjt.dinga.model;

/**
 * Requisição para registro de erro, alucinação ou viés conforme pilar de auditoria MTG-IA.
 */
public record FeedbackReportRequest(
        String watermarkId,
        String category,
        String comment
) {
    public FeedbackReportRequest {
        watermarkId = watermarkId == null ? "" : watermarkId.trim();
        category = (category == null || category.isBlank()) ? "Outro" : category.trim();
        comment = comment == null ? "" : comment.trim();
    }
}
