package br.edu.usjt.dinga.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configurações de provedor LLM híbrido (Gemini, Groq, OpenAI-compatível, Ollama ou fallback local).
 */
@ConfigurationProperties(prefix = "dinga.llm")
public record LlmProperties(
        String provider,
        String geminiApiKey,
        String geminiModel,
        String groqApiKey,
        String groqModel,
        String openaiApiKey,
        String openaiBaseUrl,
        String openaiModel,
        String ollamaUrl,
        String ollamaModel
) {
    public LlmProperties {
        provider = normalize(provider, "auto");
        geminiApiKey = blankToEmpty(geminiApiKey);
        geminiModel = normalize(geminiModel, "gemini-3.8-flash");
        groqApiKey = blankToEmpty(groqApiKey);
        groqModel = normalize(groqModel, "llama-3.1-8b-instant");
        openaiApiKey = blankToEmpty(openaiApiKey);
        openaiBaseUrl = normalize(openaiBaseUrl, "https://api.openai.com/v1");
        openaiModel = normalize(openaiModel, "gpt-4o-mini");
        ollamaUrl = blankToEmpty(ollamaUrl);
        ollamaModel = normalize(ollamaModel, "llama3.2");
    }

    /**
     * Indica qual provedor remoto/local está ativo no momento para exibição de transparência na UI.
     */
    public String resolvedProviderName() {
        String p = provider.toLowerCase();
        if ("local".equals(p)) {
            return "local";
        }
        if ("gemini".equals(p) && hasText(geminiApiKey)) {
            return "Gemini (" + geminiModel + ")";
        }
        if ("groq".equals(p) && hasText(groqApiKey)) {
            return "Groq (" + groqModel + ")";
        }
        if ("openai".equals(p) && hasText(openaiApiKey)) {
            return "OpenAI (" + openaiModel + ")";
        }
        if ("ollama".equals(p) && hasText(ollamaUrl)) {
            return "Ollama (" + ollamaModel + ")";
        }
        if (!"auto".equals(p)) {
            return "local";
        }
        if (hasText(geminiApiKey)) {
            return "Gemini (" + geminiModel + ")";
        }
        if (hasText(groqApiKey)) {
            return "Groq (" + groqModel + ")";
        }
        if (hasText(openaiApiKey)) {
            return "OpenAI (" + openaiModel + ")";
        }
        if (hasText(ollamaUrl)) {
            return "Ollama (" + ollamaModel + ")";
        }
        return "local";
    }

    public boolean isRemoteLlmConfigured() {
        return !"local".equals(resolvedProviderName());
    }

    public static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String normalize(String value, String fallback) {
        return hasText(value) ? value.trim() : fallback;
    }

    private static String blankToEmpty(String value) {
        return value == null ? "" : value.trim();
    }
}
