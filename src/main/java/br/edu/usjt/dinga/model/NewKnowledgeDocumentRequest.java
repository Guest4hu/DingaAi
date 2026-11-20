package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * DTO para cadastro ao vivo de novo tópico/documento na biblioteca RAG pelo Painel do Professor.
 */
public record NewKnowledgeDocumentRequest(
        String title,
        String level,
        String category,
        String sourceRef,
        String section,
        String content,
        List<String> tags
) {
    public NewKnowledgeDocumentRequest(
            String title,
            String level,
            String category,
            String sourceRef,
            String section,
            String content
    ) {
        this(title, level, category, sourceRef, section, content, List.of());
    }

    public NewKnowledgeDocumentRequest {
        title = title == null ? "" : title.trim();
        level = (level == null || level.isBlank()) ? "B1" : level.trim().toUpperCase();
        category = (category == null || category.isBlank()) ? "Curadoria Docente ao Vivo" : category.trim();
        sourceRef = (sourceRef == null || sourceRef.isBlank()) ? "Painel do Professor — Dinga AI" : sourceRef.trim();
        section = (section == null || section.isBlank()) ? "Tópico Adicionado pelo Professor" : section.trim();
        content = content == null ? "" : content.trim();
        tags = tags == null ? List.of() : tags.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
