package br.edu.usjt.dinga.config;

/**
 * Exceção lançada quando um cliente excede o limite de requisições por minuto em endpoints protegidos.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = Math.max(1L, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
