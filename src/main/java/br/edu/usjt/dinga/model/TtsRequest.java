package br.edu.usjt.dinga.model;

/**
 * Requisição de síntese de voz (TTS) para pronúncia em Inglês ou explicação em Português.
 */
public record TtsRequest(
        String text,
        String lang,
        Boolean offline
) {
    public TtsRequest {
        text = text == null ? "" : text.trim();
        lang = (lang == null || lang.isBlank()) ? "en-US" : lang.trim();
        offline = offline != null && offline;
    }
}
