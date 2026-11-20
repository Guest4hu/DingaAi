package br.edu.usjt.dinga.model;

/**
 * Registro armazenado de reporte ético ou erro identificado por estudante/professor.
 */
public record FeedbackReport(
        String id,
        String watermarkId,
        String category,
        String comment,
        String createdAt
) {
}
