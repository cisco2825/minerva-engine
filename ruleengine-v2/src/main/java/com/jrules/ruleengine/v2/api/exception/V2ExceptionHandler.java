package com.jrules.ruleengine.v2.api.exception;

import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.exception.MissingValueException;
import com.jrules.ruleengine.v2.exception.NotFoundException;
import com.jrules.ruleengine.v2.parser.ParseException;
import com.jrules.ruleengine.v2.parser.lexer.LexerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.NativeWebRequest;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;
import org.zalando.problem.spring.web.advice.ProblemHandling;

import java.net.URI;

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
