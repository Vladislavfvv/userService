package com.innowise.demo.exception;

import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Обработчик глобальных исключений для всех контроллеров в приложении.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    // ================= User Exceptions =================
    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
        return buildErrorResponse("USER_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        return buildErrorResponse("USER_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
    }

    // ================= CardInfo Exceptions =================
    @ExceptionHandler(CardInfoNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCardInfoNotFound(CardInfoNotFoundException ex) {
        return buildErrorResponse("CARD_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CardAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleCardAlreadyExists(CardAlreadyExistsException ex) {
        return buildErrorResponse("CARD_ALREADY_EXISTS", ex.getMessage(), HttpStatus.CONFLICT);
    }

    // ================= Validation & Bad Request =================
    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ErrorResponse> handleInvalidRequest(InvalidRequestException ex) {
        return buildErrorResponse("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        // Собираем все ошибки валидации в одно сообщение
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .reduce((first, second) -> first + "; " + second)
                .orElse("Validation error: Please check your input data");
        
        // Если сообщение слишком длинное, возвращаем общее сообщение
        if (message.length() > 200) {
            message = "Validation error: Please check your input data. " + 
                      ex.getBindingResult().getFieldErrors().size() + " field(s) have errors.";
        }
        
        return buildErrorResponse("VALIDATION_ERROR", message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        String message = "Invalid request format. Please check your JSON data and field names.";
        // Если это ошибка парсинга даты или другого типа, добавляем более конкретное сообщение
        if (ex.getMessage() != null && ex.getMessage().contains("JSON parse error")) {
            message = "Invalid JSON format. Please check your request body and ensure all fields are correct.";
        }
        return buildErrorResponse("BAD_REQUEST", message, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(BadRequestException ex) {
        return buildErrorResponse("BAD_REQUEST", ex.getMessage(), HttpStatus.BAD_REQUEST);
    }

    // ================= Resource Not Found =================
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFound(ResourceNotFoundException ex) {
        return buildErrorResponse("RESOURCE_NOT_FOUND", ex.getMessage(), HttpStatus.NOT_FOUND);
    }

    // ================= Access Denied =================
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return buildErrorResponse("ACCESS_DENIED", ex.getMessage(), HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(IllegalStateException ex) {
        // Если это ошибка связанная с отсутствием email в токене, возвращаем 403
        String message = ex.getMessage();
        if (message != null && (message.contains("Email") || message.contains("token") || message.contains("Authentication"))) {
            return buildErrorResponse("ACCESS_DENIED", "Access denied: You can only update your own information.", HttpStatus.FORBIDDEN);
        }
        return buildErrorResponse("BAD_REQUEST", message != null ? message : "Illegal state", HttpStatus.BAD_REQUEST);
    }

    // ================= Fallback =================
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAll(Exception ex) {
        // Логируем полную ошибку для отладки
        org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);
        log.error("Unexpected error occurred", ex);
        
        // Возвращаем понятное сообщение пользователю
        String message = "An unexpected error occurred. Please check your input data and try again.";
        // Если это известная ошибка, возвращаем более конкретное сообщение
        if (ex.getMessage() != null && !ex.getMessage().isEmpty()) {
            // Для некоторых ошибок возвращаем более понятное сообщение
            if (ex.getMessage().contains("NullPointerException") || 
                ex.getMessage().contains("IllegalArgumentException")) {
                message = "Invalid request data. Please check your input and try again.";
            }
        }
        return buildErrorResponse("INTERNAL_ERROR", message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // ================= Helper Method =================
    private ResponseEntity<ErrorResponse> buildErrorResponse(String code, String message, HttpStatus status) {
        ErrorResponse error = new ErrorResponse(code, message);
        return ResponseEntity.status(status).body(error);
    }

}
