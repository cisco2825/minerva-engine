package com.jrules.ruleengine.evaluator.formula.impl;

import com.jrules.ruleengine.antlr.RuleSetFormulaEvaluatorVisitor;
import com.jrules.ruleengine.antlr.RuleSetFormulaLexer;
import com.jrules.ruleengine.antlr.RuleSetFormulaParser;
import com.jrules.ruleengine.evaluator.formula.FormulaEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.springframework.stereotype.Component;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import java.util.Map;

/**
 * Evaluates boolean expressions using ANTLR-generated parser and visitor.
 * <p>
 * This class provides functionality to parse and evaluate rule expressions
 * containing logical operators (AND, OR) and criterion references. It uses
 * ANTLR grammar to parse expressions and a visitor pattern to evaluate them
 * against provided criteria results.
 * </p>
 *
 * @author Shubham Thakur
 */
@Slf4j
@Component("antlr-formula-evaluator")
public class AntlrFormulaEvaluator implements FormulaEvaluator {

    /**
     * Evaluates a boolean expression against a map of condition results.
     * {@inheritDoc}
     */
    @Override
    public boolean evaluate(String expr, Map<String, Boolean> results) {
        if (expr == null || expr.trim().isEmpty()) {
            log.error("Expression cannot be null or empty");
            throw new IllegalArgumentException("Expression cannot be null or empty");
        }
        
        if (results == null) {
            log.error("Results map cannot be null");
            throw new IllegalArgumentException("Results map cannot be null");
        }
        
        log.debug("Evaluating expression: '{}' with results: {}", expr, results);
        
        try {
            RuleSetFormulaLexer lexer = new RuleSetFormulaLexer(CharStreams.fromString(expr));
            RuleSetFormulaParser parser = new RuleSetFormulaParser(new CommonTokenStream(lexer));
            
            // Parse rule into ANTLR parse tree
            RuleSetFormulaParser.ExprContext tree = parser.expr();
            
            if (parser.getNumberOfSyntaxErrors() > 0) {
                log.error("Syntax errors found while parsing expression: '{}'", expr);
                throw Problem.valueOf(Status.BAD_REQUEST, "Failed to parse expression due to syntax errors: " + expr);
            }
            
            // Evaluate using visitor
            RuleSetFormulaEvaluatorVisitor visitor = new RuleSetFormulaEvaluatorVisitor(results);
            boolean result = visitor.visit(tree);
            
            log.debug("Expression '{}' evaluated to: {}", expr, result);
            return result;
            
        } catch (RecognitionException e) {
            log.error("Recognition error while parsing expression '{}': {}", expr, e.getMessage(), e);
            throw new RuntimeException("Failed to parse expression: " + expr, e);
        } catch (ParseCancellationException e) {
            log.error("Parse cancellation while parsing expression '{}': {}", expr, e.getMessage(), e);
            throw new RuntimeException("Expression parsing was cancelled: " + expr, e);
        } catch (Exception e) {
            log.error("Unexpected error while evaluating expression '{}': {}", expr, e.getMessage(), e);
            throw new RuntimeException("Failed to evaluate expression: " + expr, e);
        }
    }
}