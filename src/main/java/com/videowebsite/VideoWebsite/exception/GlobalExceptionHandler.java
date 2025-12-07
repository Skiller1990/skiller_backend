package com.videowebsite.VideoWebsite.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleUserAlreadyExists(UserAlreadyExistsException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.FORBIDDEN.value());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(UserNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleUserNotFound(UserNotFoundException ex) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", HttpStatus.NOT_FOUND.value());
        body.put("message", ex.getMessage());
        body.put("timestamp", Instant.now().toString());
        return new ResponseEntity<>(body, HttpStatus.NOT_FOUND);
    }


    // Add other exception handlers if needed
}

