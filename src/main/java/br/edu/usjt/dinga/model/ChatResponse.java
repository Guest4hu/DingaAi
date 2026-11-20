package br.edu.usjt.dinga.model;

import java.util.List;

/**
 * Resposta completa retornada pelo endpoint /api/chat com Skill, fontes RAG, exemplos em inglês e MTG-IA.
 */
public record ChatResponse(
        String reply,
        SkillDefinition activeSkill,
        String skillReasoning,
        List<KnowledgeChunk> retrievedSources,
        String practicePrompt,
        MtgMetadata mtg,
        List<String> englishExamples,
        LearnerStateDelta learnerStateDelta
) {
    public ChatResponse {
        englishExamples = englishExamples == null ? List.of() : List.copyOf(englishExamples);
        learnerStateDelta = learnerStateDelta == null ? LearnerStateDelta.empty() : learnerStateDelta;
    }

    public ChatResponse(
            String reply,
            SkillDefinition activeSkill,
            String skillReasoning,
            List<KnowledgeChunk> retrievedSources,
            String practicePrompt,
            MtgMetadata mtg
    ) {
        this(reply, activeSkill, skillReasoning, retrievedSources, practicePrompt, mtg, List.of(), LearnerStateDelta.empty());
    }

    public ChatResponse(
            String reply,
            SkillDefinition activeSkill,
            String skillReasoning,
            List<KnowledgeChunk> retrievedSources,
            String practicePrompt,
            MtgMetadata mtg,
            List<String> englishExamples
    ) {
        this(reply, activeSkill, skillReasoning, retrievedSources, practicePrompt, mtg, englishExamples, LearnerStateDelta.empty());
    }
}
