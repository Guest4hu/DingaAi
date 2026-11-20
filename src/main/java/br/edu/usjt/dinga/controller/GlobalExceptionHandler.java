package br.edu.usjt.dinga.controller;

import br.edu.usjt.dinga.config.RateLimitExceededException;
import br.edu.usjt.dinga.model.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Tratamento global de exceções da API REST em formato JSON padronizado (timestamp, status, error, message, path).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatus(ResponseStatusException ex, HttpServletRequest request) {
        int code = ex.getStatusCode().value();
        HttpStatus resolved = HttpStatus.resolve(code);
        String reason = resolved != null ? resolved.getReasonPhrase() : "Error";
        String message = ex.getReason() != null && !ex.getReason().isBlank() ? ex.getReason() : reason;
        return ResponseEntity.status(code)
                .body(ApiErrorResponse.of(code, reason, message, request.getRequestURI()));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleRateLimit(RateLimitExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ApiErrorResponse.of(
                        HttpStatus.TOO_MANY_REQUESTS.value(),
                        HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiErrorResponse> handleBadRequest(IllegalArgumentException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> handleUnreadableJson(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Corpo JSON inválido ou malformado.",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingParam(MissingServletRequestParameterException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Parâmetro obrigatório ausente: " + ex.getParameterName(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Arquivo excede o limite máximo permitido (512 KB).",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ApiErrorResponse> handleMissingPart(MissingServletRequestPartException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Arquivo ou parte obrigatória ausente na requisição: " + ex.getRequestPartName(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(
                        HttpStatus.BAD_REQUEST.value(),
                        HttpStatus.BAD_REQUEST.getReasonPhrase(),
                        "Parâmetro com formato inválido: " + ex.getName(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiErrorResponse.of(
                        HttpStatus.NOT_FOUND.value(),
                        HttpStatus.NOT_FOUND.getReasonPhrase(),
                        "Recurso não encontrado: " + request.getRequestURI(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiErrorResponse.of(
                        HttpStatus.METHOD_NOT_ALLOWED.value(),
                        HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                        "Método HTTP não suportado para este endpoint: " + ex.getMethod(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiErrorResponse.of(
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(),
                        HttpStatus.UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(),
                        "Tipo de conteúdo (Content-Type) não suportado.",
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiErrorResponse> handleServiceUnavailable(IllegalStateException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiErrorResponse.of(
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        HttpStatus.SERVICE_UNAVAILABLE.getReasonPhrase(),
                        ex.getMessage(),
                        request.getRequestURI()
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Erro inesperado em {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of(
                        HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "Erro interno inesperado ao processar a solicitação.",
                        request.getRequestURI()
                ));
    }
}
