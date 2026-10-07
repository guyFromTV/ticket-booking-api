package com.ticketing.booking.web;

import com.ticketing.booking.exception.HoldNotUsableException;
import com.ticketing.booking.exception.InvalidRequestException;
import com.ticketing.booking.exception.NotFoundException;
import com.ticketing.booking.exception.SeatsUnavailableException;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates domain failures into RFC 7807 problem responses, so every error has
 * the same machine-readable shape instead of a stack trace or an HTML page.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String BASE_TYPE = "https://api.ticketing.com/problems/";

    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Resource not found", exception.getMessage(), "not-found");
    }

    @ExceptionHandler(SeatsUnavailableException.class)
    public ProblemDetail handleSeatsUnavailable(SeatsUnavailableException exception) {
        ProblemDetail problem = problem(
                HttpStatus.CONFLICT, "Seats unavailable", exception.getMessage(), "seats-unavailable");
        // Let the client highlight exactly which seats to re-pick.
        problem.setProperty("unavailableSeats", exception.getUnavailableSeats());
        return problem;
    }

    @ExceptionHandler(HoldNotUsableException.class)
    public ProblemDetail handleHoldNotUsable(HoldNotUsableException exception) {
        return problem(HttpStatus.CONFLICT, "Hold not usable", exception.getMessage(), "hold-not-usable");
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidRequestException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage(), "invalid-request");
    }

    /**
     * Raised when two transactions change the same row and the {@code @Version}
     * check loses. Retrying the request is the correct client response, hence 409.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException exception) {
        log.warn("Optimistic lock conflict: {}", exception.getMessage());
        return problem(
                HttpStatus.CONFLICT,
                "Concurrent modification",
                "Another request changed these seats first. Retry the operation.",
                "concurrent-modification");
    }

    /** Field-level Bean Validation failures on a request body. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "invalid" : error.getDefaultMessage(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST, "Validation failed", "One or more fields are invalid", "validation-failed");
        problem.setProperty("fieldErrors", fieldErrors);
        return problem;
    }

    /** Validation failures on query parameters and path variables. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        return problem(
                HttpStatus.BAD_REQUEST, "Validation failed", exception.getMessage(), "validation-failed");
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        return problem;
    }
}
