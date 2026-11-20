package br.edu.usjt.dinga.service;

import br.edu.usjt.dinga.config.RateLimitExceededException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Rate limiter leve em memória (janela deslizante de 60s por IP e bucket)
 * com limpeza automática (eviction) de entradas expiradas para evitar crescimento de memória.
 * Protege /api/chat, /api/chat/stream, /api/tts, /api/quiz/generate e /api/simulator contra rajadas.
 */
@Service
public class RateLimiterService {

    private static final long WINDOW_MS = 60_000L;
    private static final long EVICTION_INTERVAL_MS = 60_000L;
    private static final int EVICTION_SIZE_THRESHOLD = 512;
    private static final Map<String, Integer> BUCKET_LIMITS = Map.of(
            "chat", 25,
            "tts", 40,
            "quiz-generate", 15,
            "simulator", 25
    );

    private final boolean enabled;
    private final SessionMetricsService metricsService;
    private final ConcurrentHashMap<String, Deque<Long>> windows = new ConcurrentHashMap<>();
    private final AtomicLong lastEvictionMs = new AtomicLong(0L);

    public RateLimiterService(
            @Value("${dinga.rate-limit.enabled:true}") boolean enabled,
            SessionMetricsService metricsService
    ) {
        this.enabled = enabled;
        this.metricsService = metricsService;
    }

    public void checkLimit(String clientKey, String bucket) {
        if (!enabled) {
            return;
        }
        int limit = BUCKET_LIMITS.getOrDefault(bucket, 30);
        checkLimit(clientKey, bucket, limit, WINDOW_MS);
    }

    public void checkLimit(String clientKey, String bucket, int maxRequests, long windowMs) {
        if (!enabled) {
            return;
        }
        long now = System.currentTimeMillis();
        maybeEvictExpired(now, windowMs);

        String normalizedClient = (clientKey == null || clientKey.isBlank()) ? "unknown" : clientKey.trim();
        String key = normalizedClient + ":" + bucket;
        long[] retryAfterHolder = {-1L};

        windows.compute(key, (k, existing) -> {
            Deque<Long> timestamps = existing != null ? existing : new ArrayDeque<>();
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) >= windowMs) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxRequests) {
                long oldest = timestamps.peekFirst() != null ? timestamps.peekFirst() : now;
                retryAfterHolder[0] = Math.max(1L, (windowMs - (now - oldest) + 999L) / 1000L);
                return timestamps;
            }
            timestamps.addLast(now);
            return timestamps;
        });

        if (retryAfterHolder[0] > 0L) {
            if (metricsService != null) {
                metricsService.recordRateLimitBlocked();
            }
            throw new RateLimitExceededException(
                    "Muitas requisições em sequência para '" + bucket + "'. Aguarde " + retryAfterHolder[0] + "s e tente novamente.",
                    retryAfterHolder[0]
            );
        }
    }

    private void maybeEvictExpired(long now, long windowMs) {
        long previous = lastEvictionMs.get();
        boolean intervalElapsed = (now - previous) >= EVICTION_INTERVAL_MS;
        boolean sizeExceeded = windows.size() >= EVICTION_SIZE_THRESHOLD;
        if ((intervalElapsed || sizeExceeded) && lastEvictionMs.compareAndSet(previous, now)) {
            evictExpired(now, windowMs);
        }
    }

    /**
     * Remove entradas cujos timestamps já expiraram da janela deslizante, evitando crescimento ilimitado de memória.
     */
    public int evictExpired() {
        return evictExpired(System.currentTimeMillis(), WINDOW_MS);
    }

    public int evictExpired(long now, long windowMs) {
        AtomicInteger evicted = new AtomicInteger(0);
        for (String key : windows.keySet()) {
            windows.computeIfPresent(key, (k, timestamps) -> {
                while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) >= windowMs) {
                    timestamps.pollFirst();
                }
                if (timestamps.isEmpty()) {
                    evicted.incrementAndGet();
                    return null;
                }
                return timestamps;
            });
        }
        return evicted.get();
    }

    public int trackedKeyCount() {
        return windows.size();
    }

    public void reset() {
        windows.clear();
        lastEvictionMs.set(0L);
    }
}
