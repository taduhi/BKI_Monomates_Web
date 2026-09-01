package com.monomates.api.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(NotFoundException.class)
  ResponseEntity<ApiErrorResponse> notFound(
    NotFoundException e,
    HttpServletRequest r
  ) {
    return out(HttpStatus.NOT_FOUND, e.getMessage(), r, null);
  }

  @ExceptionHandler(UnauthorizedException.class)
  ResponseEntity<ApiErrorResponse> unauthorized(
    UnauthorizedException e,
    HttpServletRequest r
  ) {
    return out(HttpStatus.UNAUTHORIZED, e.getMessage(), r, null);
  }

  @ExceptionHandler(ConflictException.class)
  ResponseEntity<ApiErrorResponse> conflict(
    ConflictException e,
    HttpServletRequest r
  ) {
    return out(HttpStatus.CONFLICT, e.getMessage(), r, null);
  }

  @ExceptionHandler(BusinessRuleException.class)
  ResponseEntity<ApiErrorResponse> rule(
    BusinessRuleException e,
    HttpServletRequest r
  ) {
    return out(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage(), r, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiErrorResponse> validation(
    MethodArgumentNotValidException e,
    HttpServletRequest r
  ) {
    Map<String, String> f = new LinkedHashMap<>();
    e.getBindingResult()
      .getFieldErrors()
      .forEach(x -> f.putIfAbsent(x.getField(), x.getDefaultMessage()));
    return out(HttpStatus.BAD_REQUEST, "Request validation failed.", r, f);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  ResponseEntity<ApiErrorResponse> constraint(
    ConstraintViolationException e,
    HttpServletRequest r
  ) {
    return out(HttpStatus.BAD_REQUEST, e.getMessage(), r, null);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiErrorResponse> integrity(
    DataIntegrityViolationException e,
    HttpServletRequest r
  ) {
    return out(
      HttpStatus.CONFLICT,
      "The operation conflicts with existing data.",
      r,
      null
    );
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiErrorResponse> unknown(Exception e, HttpServletRequest r) {
    return out(
      HttpStatus.INTERNAL_SERVER_ERROR,
      "An unexpected server error occurred.",
      r,
      null
    );
  }

  private ResponseEntity<ApiErrorResponse> out(
    HttpStatus s,
    String m,
    HttpServletRequest r,
    Map<String, String> f
  ) {
    return ResponseEntity.status(s).body(
      new ApiErrorResponse(
        Instant.now(),
        s.value(),
        s.getReasonPhrase(),
        m,
        r.getRequestURI(),
        f
      )
    );
  }
}
