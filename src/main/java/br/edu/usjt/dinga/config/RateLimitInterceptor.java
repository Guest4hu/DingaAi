package br.edu.usjt.dinga.config;

import br.edu.usjt.dinga.service.RateLimiterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.net.InetAddress;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Interceptador HTTP que aplica rate limiting leve nos endpoints de geração/síntese
 * com controle seguro de X-Forwarded-For (somente proxies confiáveis + validação estrita de IP).
 */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private static final Set<String> LOOPBACK_PROXIES = Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1");

    private final RateLimiterService rateLimiter;
    private final boolean trustProxy;

    public RateLimitInterceptor(RateLimiterService rateLimiter) {
        this(rateLimiter, false);
    }

    @Autowired
    public RateLimitInterceptor(
            RateLimiterService rateLimiter,
            @Value("${dinga.rate-limit.trust-proxy:false}") boolean trustProxy
    ) {
        this.rateLimiter = rateLimiter;
        this.trustProxy = trustProxy;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String uri = request.getRequestURI();
        String bucket = resolveBucket(uri);
        if (bucket != null) {
            String clientIp = extractClientIp(request);
            rateLimiter.checkLimit(clientIp, bucket);
        }
        return true;
    }

    private static String resolveBucket(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.equals("/api/chat") || uri.equals("/api/chat/stream")) {
            return "chat";
        }
        if (uri.equals("/api/tts")) {
            return "tts";
        }
        if (uri.equals("/api/quiz/generate")) {
            return "quiz-generate";
        }
        if (uri.startsWith("/api/simulator/")) {
            return "simulator";
        }
        return null;
    }

    /**
     * Extrai o IP do cliente de forma segura:
     * - Ignora X-Forwarded-For se a conexão não vier de um proxy confiável (trustProxy=true ou loopback).
     * - Valida estritamente o formato IPv4/IPv6 para impedir spoofing/injeção de chaves no mapa de rate limit.
     */
    public String extractClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        String safeRemote = (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr.trim() : "local";

        boolean fromTrustedProxy = trustProxy || isLoopbackProxy(safeRemote);
        if (!fromTrustedProxy) {
            return safeRemote;
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] parts = forwarded.split(",");
            for (String part : parts) {
                String candidate = part.trim();
                if (isValidIp(candidate)) {
                    return candidate;
                }
            }
        }
        return safeRemote;
    }

    private static boolean isLoopbackProxy(String addr) {
        return LOOPBACK_PROXIES.contains(addr);
    }

    private static boolean isValidIp(String value) {
        if (value == null || value.isBlank() || value.length() > 45) {
            return false;
        }
        try {
            InetAddress.ofLiteral(value);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
