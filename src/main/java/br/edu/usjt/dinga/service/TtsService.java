package br.edu.usjt.dinga.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Serviço híbrido de síntese de voz (Text-to-Speech):
 * 1) Online natural (MP3 via Google Translate TTS, sem exigir API key).
 * 2) Fallback offline local (WAV via eSpeak-NG instalado no sistema Linux).
 *
 * Resolve o bloqueio da Web Speech API em navegadores Linux/Flatpak (como Vivaldi/Chromium
 * sem speech-dispatcher) e garante acessibilidade auditiva mesmo em baixa conectividade.
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);
    private static final int MAX_TEXT_CHARS = 1200;
    private static final int ONLINE_CHUNK_LIMIT = 180;
    private static final int CACHE_MAX_ENTRIES = 32;

    private final HttpClient httpClient;
    private final Map<String, TtsAudio> cache;

    public record TtsAudio(byte[] bytes, String contentType, String engine) {}

    public TtsService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.cache = Collections.synchronizedMap(new LinkedHashMap<>(CACHE_MAX_ENTRIES + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, TtsAudio> eldest) {
                return size() > CACHE_MAX_ENTRIES;
            }
        });
    }

    public TtsAudio synthesize(String rawText, String rawLang, boolean preferOffline) {
        String cleaned = sanitizeForSpeech(rawText);
        if (cleaned.isBlank()) {
            throw new IllegalArgumentException("Texto vazio para síntese de voz.");
        }
        String normalizedLang = normalizeLang(rawLang);
        String cacheKey = (preferOffline ? "off:" : "on:") + normalizedLang + ":" + cleaned;

        TtsAudio cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        if (!preferOffline) {
            Optional<TtsAudio> online = synthesizeOnlineMp3(cleaned, normalizedLang);
            if (online.isPresent()) {
                cache.put(cacheKey, online.get());
                return online.get();
            }
        }

        Optional<TtsAudio> local = synthesizeWithEspeakNg(cleaned, normalizedLang);
        if (local.isPresent()) {
            cache.put(cacheKey, local.get());
            return local.get();
        }

        // Se preferOffline foi solicitado mas espeak-ng não está disponível, tenta online como último recurso
        if (preferOffline) {
            Optional<TtsAudio> onlineFallback = synthesizeOnlineMp3(cleaned, normalizedLang);
            if (onlineFallback.isPresent()) {
                cache.put(cacheKey, onlineFallback.get());
                return onlineFallback.get();
            }
        }

        throw new IllegalStateException("Nenhum motor de síntese de voz disponível no servidor.");
    }

    String sanitizeForSpeech(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1")
                .replaceAll("[#*`_~>•]+", " ")
                .replaceAll("https?://\\S+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (text.length() > MAX_TEXT_CHARS) {
            int cut = text.lastIndexOf(' ', MAX_TEXT_CHARS);
            text = text.substring(0, cut > 0 ? cut : MAX_TEXT_CHARS).trim();
        }
        return text;
    }

    String normalizeLang(String rawLang) {
        String lower = rawLang == null ? "" : rawLang.trim().toLowerCase(Locale.ROOT);
        if (lower.startsWith("pt")) {
            return "pt-BR";
        }
        return "en-US";
    }

    List<String> splitIntoChunks(String text, int maxLen) {
        List<String> chunks = new ArrayList<>();
        String remaining = text.trim();
        while (remaining.length() > maxLen) {
            int splitAt = -1;
            for (int i = Math.min(maxLen, remaining.length() - 1); i >= maxLen / 2; i--) {
                char c = remaining.charAt(i);
                if (c == '.' || c == '!' || c == '?' || c == ';' || c == ':') {
                    splitAt = i + 1;
                    break;
                }
            }
            if (splitAt < 0) {
                splitAt = remaining.lastIndexOf(' ', maxLen);
            }
            if (splitAt <= 0) {
                splitAt = maxLen;
            }
            chunks.add(remaining.substring(0, splitAt).trim());
            remaining = remaining.substring(splitAt).trim();
        }
        if (!remaining.isEmpty()) {
            chunks.add(remaining);
        }
        return chunks;
    }

    private Optional<TtsAudio> synthesizeOnlineMp3(String text, String lang) {
        List<String> chunks = splitIntoChunks(text, ONLINE_CHUNK_LIMIT);
        ByteArrayOutputStream combined = new ByteArrayOutputStream();
        String tl = lang.equals("pt-BR") ? "pt-BR" : "en";

        for (String chunk : chunks) {
            String encoded = URLEncoder.encode(chunk, StandardCharsets.UTF_8);
            String url = "https://translate.googleapis.com/translate_tts?ie=UTF-8&client=tw-ob&tl="
                    + tl + "&q=" + encoded;
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36")
                    .GET()
                    .build();
            try {
                HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
                if (resp.statusCode() != 200 || resp.body() == null || resp.body().length < 128) {
                    log.debug("Online TTS falhou com HTTP {} para idioma {}", resp.statusCode(), lang);
                    return Optional.empty();
                }
                combined.write(resp.body());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            } catch (IOException e) {
                log.debug("Online TTS indisponível ({}), tentando fallback local.", e.getMessage());
                return Optional.empty();
            }
        }

        byte[] result = combined.toByteArray();
        if (result.length < 128) {
            return Optional.empty();
        }
        return Optional.of(new TtsAudio(result, "audio/mpeg", "Neural Online TTS"));
    }

    private Optional<TtsAudio> synthesizeWithEspeakNg(String text, String lang) {
        Path tempWav = null;
        try {
            tempWav = Files.createTempFile("dinga-tts-", ".wav");
            String voice = lang.equals("pt-BR") ? "pt-br" : "en-us";
            String speed = lang.equals("pt-BR") ? "155" : "140";

            ProcessBuilder pb = new ProcessBuilder(
                    "espeak-ng",
                    "-v", voice,
                    "-s", speed,
                    "-w", tempWav.toAbsolutePath().toString(),
                    "--",
                    text
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            boolean finished = process.waitFor(8, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("Timeout ao executar espeak-ng local.");
                return Optional.empty();
            }
            if (process.exitValue() != 0) {
                log.debug("espeak-ng retornou código {}", process.exitValue());
                return Optional.empty();
            }
            byte[] wavBytes = Files.readAllBytes(tempWav);
            if (wavBytes.length < 44) {
                return Optional.empty();
            }
            return Optional.of(new TtsAudio(wavBytes, "audio/wav", "eSpeak-NG Offline"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException e) {
            log.debug("espeak-ng não disponível no ambiente: {}", e.getMessage());
            return Optional.empty();
        } finally {
            if (tempWav != null) {
                try {
                    Files.deleteIfExists(tempWav);
                } catch (IOException ignored) {
                    // ignore cleanup failure
                }
            }
        }
    }
}
