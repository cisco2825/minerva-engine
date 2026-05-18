package com.jrules.ruleengine.v2.api.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.databind.exc.ValueInstantiationException;
import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.parser.ParseException;
import com.jrules.ruleengine.v2.parser.lexer.LexerException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.ProblemHandling;

import java.net.URI;
import java.util.stream.Collectors;

/**
 * Maps V2-specific exceptions to RFC 7807 Problem responses.
 * ProblemHandling provides built-in handlers for Spring MVC exceptions
 * (malformed JSON, validation failures, method-not-allowed, etc.).
 */
@RestControllerAdvice
public class V2ExceptionHandler implements ProblemHandling {

    private static final String BASE = "https://ruleengine.jrules.com/problems/";

    // ── Expression parsing errors → 400 ──────────────────────────────────────

    @ExceptionHandler(LexerException.class)
    public ResponseEntity<Problem> handleLexerException(LexerException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "expression-lex-error"))
                .withTitle("Expression Syntax Error")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(ex.getMessage())
                .with("line",   ex.getLine())
                .with("column", ex.getColumn())
                .build();
        return create(ex, problem, request);
    }

    @ExceptionHandler(ParseException.class)
    public ResponseEntity<Problem> handleParseException(ParseException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "expression-parse-error"))
                .withTitle("Expression Parse Error")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(ex.getMessage())
                .with("line",   ex.getLine())
                .with("column", ex.getColumn())
                .build();
        return create(ex, problem, request);
    }

    // ── Missing context value → 422 ───────────────────────────────────────────

    @ExceptionHandler(MissingValueException.class)
    public ResponseEntity<Problem> handleMissingValue(MissingValueException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "missing-context-value"))
                .withTitle("Missing Context Value")
                .withStatus(Status.UNPROCESSABLE_ENTITY)
                .withDetail(ex.getMessage())
                .with("path", ex.getPath())
                .build();
        return create(ex, problem, request);
    }

    // ── Evaluation errors → 422 ───────────────────────────────────────────────

    @ExceptionHandler(EvaluationException.class)
    public ResponseEntity<Problem> handleEvaluationException(EvaluationException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "evaluation-error"))
                .withTitle("Evaluation Error")
                .withStatus(Status.UNPROCESSABLE_ENTITY)
                .withDetail(ex.getMessage())
                .build();
        return create(ex, problem, request);
    }

    // ── Not found → 404 ──────────────────────────────────────────────────────

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Problem> handleNotFound(NotFoundException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "not-found"))
                .withTitle("Not Found")
                .withStatus(Status.NOT_FOUND)
                .withDetail(ex.getMessage())
                .build();
        return create(ex, problem, request);
    }

    // ── Bad request (illegal arguments from storage layer) → 400 ─────────────

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Problem> handleIllegalArgument(IllegalArgumentException ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "bad-request"))
                .withTitle("Bad Request")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(ex.getMessage())
                .build();
        return create(ex, problem, request);
    }

    // ── Malformed / unreadable request body → 400 ────────────────────────────
    // Overrides MessageNotReadableAdviceTrait's default to give a human-readable message.

    @Override
    public ResponseEntity<Problem> handleMessageNotReadableException(HttpMessageNotReadableException ex,
                                                                      NativeWebRequest request) {
        String detail = buildDeserializationMessage(ex.getCause());
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "invalid-request-body"))
                .withTitle("Invalid Request Body")
                .withStatus(Status.BAD_REQUEST)
                .withDetail(detail)
                .build();
        return create(ex, problem, request);
    }

    private String buildDeserializationMessage(Throwable cause) {
        // @JsonCreator threw IllegalArgumentException (e.g. bad Operator token)
        if (cause instanceof ValueInstantiationException vie && vie.getCause() != null) {
            return vie.getCause().getMessage();
        }
        // Jackson couldn't coerce a value to the target type (enum name mismatch, wrong number type, etc.)
        if (cause instanceof InvalidFormatException ife) {
            String path = ife.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining(" → "));
            String badValue = String.valueOf(ife.getValue());
            if (ife.getTargetType() != null && ife.getTargetType().isEnum()) {
                String accepted = java.util.Arrays.stream(ife.getTargetType().getEnumConstants())
                        .map(Object::toString)
                        .collect(Collectors.joining(", "));
                return "Invalid value '" + badValue + "' at '" + path + "'. Accepted values: " + accepted;
            }
            return "Invalid value '" + badValue + "' at '" + path
                    + "' — expected type: " + (ife.getTargetType() != null
                            ? ife.getTargetType().getSimpleName() : "unknown");
        }
        // Unknown field in the JSON
        if (cause instanceof UnrecognizedPropertyException upe) {
            return "Unknown field '" + upe.getPropertyName()
                    + "' — check the request schema";
        }
        // Generic fallback — still better than raw Jackson internals
        return cause != null ? cause.getMessage() : "Request body is malformed or contains invalid values";
    }

    // ── Catch-all → 500 ───────────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Problem> handleGeneric(Exception ex, NativeWebRequest request) {
        Problem problem = Problem.builder()
                .withType(URI.create(BASE + "internal-error"))
                .withTitle("Internal Server Error")
                .withStatus(Status.INTERNAL_SERVER_ERROR)
                .withDetail("An unexpected error occurred. Please contact support.")
                .build();
        return create(ex, problem, request);
    }
}
