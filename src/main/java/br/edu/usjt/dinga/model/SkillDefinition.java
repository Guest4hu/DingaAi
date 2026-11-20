package br.edu.usjt.dinga.model;

/**
 * Metadados descritivos de uma Skill pedagógica da Dinga AI.
 */
public record SkillDefinition(
        String id,
        String name,
        String badge,
        String description,
        String pedagogyRule,
        String odsAlignment,
        String samplePrompt
) {
}
