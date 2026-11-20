package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.model.KnowledgeChunk;
import br.edu.usjt.dinga.rag.Bm25RagRetriever;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verificador determinístico pós-geração (0 ms):
 * 1) Calcula índice de ancoragem (groundednessScore ∈ [0.0, 1.0]) comparando termos-chave da resposta com os chunks RAG.
 * 2) Executa checagem de saída LGPD/PII para garantir que nenhum CPF, e-mail ou telefone vaze na resposta final.
 */
public final class GroundednessVerifier {

    private static final Pattern BOLD_SPAN = Pattern.compile("\\*\\*([^*]{2,60})\\*\\*");
    private static final Pattern LGPD_PLACEHOLDER = Pattern.compile("\\[(?:E-MAIL|CPF|TELEFONE) PROTEGIDO — LGPD\\]");
    private static final Set<String> SHORT_TECH_WHITELIST = Set.of("ia", "ai", "pr", "ti", "db", "ui", "ux", "qa");
    private static final Set<String> STOPWORDS = Set.of(
            // Português
            "que", "com", "para", "por", "uma", "uns", "umas", "dos", "das", "nos", "nas", "pelo", "pela",
            "pelos", "pelas", "como", "mais", "mas", "sem", "sob", "sobre", "entre", "apos", "antes", "depois",
            "quando", "onde", "qual", "quais", "quem", "seu", "sua", "seus", "suas", "meu", "minha", "meus",
            "minhas", "este", "esta", "estes", "estas", "esse", "essa", "esses", "essas", "isso", "isto",
            "aquilo", "voce", "voces", "eles", "elas", "nosso", "nossa", "ser", "estar", "ter", "fazer",
            "pode", "podem", "deve", "devem", "sao", "foi", "era", "tem", "nao", "sim", "muito", "bem",
            "tambem", "ainda", "aqui", "agora", "hoje", "sempre", "nunca", "tudo", "cada", "outro", "outra",
            "outros", "outras", "mesmo", "mesma", "assim", "entao", "porque", "pois", "caso", "modo",
            "exemplo", "exemplos", "frase", "frases", "resposta", "skill", "nivel", "dinga",
            // Inglês
            "the", "and", "for", "with", "that", "this", "from", "into", "your", "you", "are", "was", "were",
            "have", "has", "had", "not", "but", "can", "will", "would", "could", "all", "any", "one", "two",
            "use", "using", "used", "about", "when", "what", "which", "how", "why", "who", "they", "them",
            "their", "our", "its", "there", "here", "then", "than", "also", "very", "just", "only", "more"
    );

    private GroundednessVerifier() {
    }

    public static VerificationResult verify(String rawReply, List<KnowledgeChunk> sources) {
        DingaTutorService.PiiSanitizationResult piiCheck = DingaTutorService.sanitizePii(rawReply);
        String safeReply = piiCheck.sanitizedText();
        double score = computeGroundednessScore(safeReply, sources);
        return new VerificationResult(
                safeReply,
                score,
                piiCheck.anyMasked(),
                piiCheck.maskedTypes()
        );
    }

    public static double computeGroundednessScore(String reply, List<KnowledgeChunk> sources) {
        if (reply == null || reply.isBlank() || sources == null || sources.isEmpty()) {
            return 0.0;
        }

        Set<String> sourceTerms = new LinkedHashSet<>();
        Set<String> salientSourceTerms = new LinkedHashSet<>();

        for (KnowledgeChunk chunk : sources) {
            if (chunk == null) {
                continue;
            }
            addTerms(chunk.title(), sourceTerms);
            addTerms(chunk.section(), sourceTerms);
            addTerms(chunk.category(), sourceTerms);
            addTerms(chunk.content(), sourceTerms);

            addTerms(chunk.title(), salientSourceTerms);
            addTerms(chunk.section(), salientSourceTerms);
            if (chunk.content() != null) {
                Matcher bold = BOLD_SPAN.matcher(chunk.content());
                while (bold.find()) {
                    addTerms(bold.group(1), salientSourceTerms);
                }
            }
        }

        if (sourceTerms.isEmpty()) {
            return 0.0;
        }

        Set<String> replyTerms = new LinkedHashSet<>();
        addTerms(LGPD_PLACEHOLDER.matcher(reply).replaceAll(" "), replyTerms);
        if (replyTerms.isEmpty()) {
            return 0.0;
        }

        int matchedReplyTerms = 0;
        for (String term : replyTerms) {
            if (matchesTermSet(term, sourceTerms)) {
                matchedReplyTerms++;
            }
        }
        double precisionOverlap = (double) matchedReplyTerms / replyTerms.size();

        double salientCoverage = 0.0;
        if (!salientSourceTerms.isEmpty()) {
            int matchedSalient = 0;
            for (String salient : salientSourceTerms) {
                if (matchesTermSet(salient, replyTerms)) {
                    matchedSalient++;
                }
            }
            // Normaliza cobertura saliente com teto prático (citar ~40% dos termos salientes já representa forte ancoragem)
            salientCoverage = Math.min(1.0, ((double) matchedSalient / salientSourceTerms.size()) / 0.40);
        }

        double blended = (0.65 * precisionOverlap) + (0.35 * salientCoverage);
        double clamped = Math.max(0.0, Math.min(1.0, blended));
        return Math.round(clamped * 100.0) / 100.0;
    }

    private static void addTerms(String text, Set<String> target) {
        if (text == null || text.isBlank()) {
            return;
        }
        String norm = Bm25RagRetriever.normalizeText(text);
        if (norm.isBlank()) {
            return;
        }
        for (String token : norm.split("[^a-z0-9]+")) {
            if (token.isEmpty()) {
                continue;
            }
            if ((token.length() >= 3 || SHORT_TECH_WHITELIST.contains(token)) && !STOPWORDS.contains(token)) {
                target.add(token);
            }
        }
    }

    private static boolean matchesTermSet(String term, Set<String> set) {
        if (set.contains(term)) {
            return true;
        }
        if (term.length() >= 5) {
            String stem = term.substring(0, 5);
            for (String candidate : set) {
                if (candidate.length() >= 5 && candidate.startsWith(stem)) {
                    return true;
                }
            }
        }
        return false;
    }

    public record VerificationResult(
            String sanitizedReply,
            double groundednessScore,
            boolean outputPiiMasked,
            List<String> outputMaskedTypes
    ) {
    }
}
