package br.edu.usjt.dinga.model;

import java.time.Instant;

/**
 * Resposta de erro padronizada retornada pelo GlobalExceptionHandler (@RestControllerAdvice).
 */
public record ApiErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public static ApiErrorResponse of(int status, String error, String message, String path) {
        return new ApiErrorResponse(
                Instant.now().toString(),
                status,
                error,
                message == null || message.isBlank() ? error : message,
                path == null ? "" : path
        );
    }
}
