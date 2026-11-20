package br.edu.usjt.dinga.skills;

import br.edu.usjt.dinga.model.ChatRequest;
import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.model.SkillDefinition;
import java.util.List;

/**
 * Contrato modular para cada Skill pedagógica do tutor Dinga AI.
 */
public interface TeacherSkill {

    SkillDefinition definition();

    /**
     * Pontua a aderência desta Skill à mensagem do aluno (0 = sem afinidade).
     */
    int matchScore(ChatRequest request);

    /**
     * Constrói o prompt de sistema ancorado nos trechos recuperados pelo RAG.
     */
    String buildSystemPrompt(ChatRequest request, List<KnowledgeChunk> chunks);

    /**
     * Gera resposta estruturada diretamente em Java + RAG quando não houver API key remota.
     */
    String buildLocalResponse(ChatRequest request, List<KnowledgeChunk> chunks);

    /**
     * Sugere um micro-desafio ou pergunta socrática complementar.
     */
    String buildPracticePrompt(ChatRequest request, List<KnowledgeChunk> chunks);
}
