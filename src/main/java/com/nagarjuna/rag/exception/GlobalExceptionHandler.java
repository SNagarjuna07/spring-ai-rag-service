package com.nagarjuna.rag.exception;

import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(
            MethodArgumentNotValidException ex
    ) {

        Map<String, String> fieldErrors = new HashMap<>();

        ex
                .getBindingResult()
                .getFieldErrors()
                .forEach(err ->
                        fieldErrors
                                .put(
                                        err.getField(),
                                        err.getDefaultMessage()
                                )
                );

        return ResponseEntity
                .badRequest()
                .body(
                        body(
                                HttpStatus.BAD_REQUEST,
                                "Validation failed",
                                fieldErrors
                        )
                );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(
            ConstraintViolationException ex
    ) {

        return ResponseEntity
                .badRequest()
                .body(
                        body(
                                HttpStatus.BAD_REQUEST,
                                ex.getMessage(),
                                null)
                );
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSizeViolation(
            ConstraintViolationException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.CONTENT_TOO_LARGE)
                .body(
                        body(
                                HttpStatus.CONTENT_TOO_LARGE,
                                "File exceeds max upload size (20 MB)",
                                null)
                );
    }

    @ExceptionHandler(DocumentProcessingException.class)
    public ResponseEntity<Map<String, Object>> handleDocumentProcessingViolation(
            DocumentProcessingException ex
    ) {

        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_CONTENT)
                .body(
                        body(
                                HttpStatus.UNPROCESSABLE_CONTENT,
                                ex.getMessage(),
                                null)
                );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        body(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                "Unexpected error: " + ex.getMessage(),
                                null
                        )
                );
    }

    private Map<String, Object> body(
            HttpStatus status,
            String message,
            Map<String, String> details
    ) {

        Map<String, Object> map = new HashMap<>();

        map.put("timestamp", Instant.now());
        map.put("status", status.value());
        map.put("error", status.getReasonPhrase());
        map.put("message", message);

        if (details != null) {
            map.put("details", details);
        }

        return map;
    }
}
