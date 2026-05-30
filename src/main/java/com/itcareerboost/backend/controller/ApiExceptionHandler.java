package com.itcareerboost.backend.controller;

import com.itcareerboost.backend.service.NotFoundException;
import com.itcareerboost.backend.service.UnauthorizedException;
import java.time.Instant;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(UnauthorizedException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public Map<String, Object> unauthorized(UnauthorizedException exception) {
    return error(exception.getMessage());
  }

  @ExceptionHandler(NotFoundException.class)
  @ResponseStatus(HttpStatus.NOT_FOUND)
  public Map<String, Object> notFound(NotFoundException exception) {
    return error(exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  @ResponseStatus(HttpStatus.BAD_REQUEST)
  public Map<String, Object> validation(MethodArgumentNotValidException exception) {
    return error("Request validation failed.");
  }

  private Map<String, Object> error(String message) {
    return Map.of("message", message, "timestamp", Instant.now().toString());
  }
}
