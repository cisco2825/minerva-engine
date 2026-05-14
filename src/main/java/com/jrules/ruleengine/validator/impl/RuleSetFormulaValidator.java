package com.jrules.ruleengine.validator.impl;

import com.jrules.ruleengine.antlr.RuleSetFormulaLexer;
import com.jrules.ruleengine.antlr.RuleSetFormulaParser;
import com.jrules.ruleengine.antlr.RuleSetFormulaBaseVisitor;
import com.jrules.ruleengine.enums.RuleSetValidationErrorType;
import com.jrules.ruleengine.model.RuleSet.Criterion;
import com.jrules.ruleengine.model.RuleSet;
import com.jrules.ruleengine.model.RuleSetValidationError;
import com.jrules.ruleengine.model.RuleSetValidationResult;
import com.jrules.ruleengine.validator.RuleSetValidator;
import lombok.extern.slf4j.Slf4j;
import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.misc.ParseCancellationException;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Validates rule expressions using ANTLR parsing to ensure syntactic correctness
 * and semantic consistency of condition references.
 * {@inheritDoc}
 */
@Slf4j
@Service("ruleset-formula-validator")
public class RuleSetFormulaValidator implements RuleSetValidator {

  /**
   * Validates a rule file's expression for syntax and semantic correctness.
   * {@inheritDoc}
   */
  @Override
  public RuleSetValidationResult validate(RuleSet ruleSet) {
    log.info("Rule set formula validation started");
    RuleSetValidationResult result = new RuleSetValidationResult(new ArrayList<>());

    if (ruleSet == null || ruleSet.getFormula() == null || ruleSet.getFormula().trim().isEmpty()) {
      result.addError(new RuleSetValidationError(
          RuleSetValidationErrorType.RULESET_FORMULA_VALIDATION_FAILURE, "Rule expression is missing"));
      return result;
    }

    ParseTree tree = parseExpression(ruleSet.getFormula(), result);
    if (tree == null) return result;   // syntax errors already added

    Set<String> usedIds = extractIdentifiers(tree);
    Set<String> declared = getDeclaredCriteria(ruleSet);

    validateIdentifiers(usedIds, declared, result);

    return result;
  }

  // -----------------------------------------------------------------------
  // 1. ANTLR parsing + syntax error collection
  // -----------------------------------------------------------------------
  /**
   * Parses a rule expression using ANTLR grammar and collects syntax errors.
   * 
   * <p>Creates a lexer and parser with custom error listeners to capture
   * all syntax errors during parsing. If parsing fails, errors are added
   * to the validation result.
   * 
   * @param expression the rule expression string to parse
   * @param result the validation result to add errors to
   * @return the parsed tree if successful, null if syntax errors occurred
   */
  private ParseTree parseExpression(String expression, RuleSetValidationResult result) {
    List<String> syntaxErrors = new ArrayList<>();

    RuleSetFormulaLexer lexer = new RuleSetFormulaLexer(CharStreams.fromString(expression));
    RuleSetFormulaParser parser = getRuleSetFormulaParser(lexer, syntaxErrors);

    ParseTree tree = null;
    try {
      tree = parser.parse(); // attempt parse
    } catch (ParseCancellationException e) {
      syntaxErrors.add(e.getMessage());
    } catch (Exception e) {
      syntaxErrors.add("Unexpected rule expression validation error");
    }

    if (!syntaxErrors.isEmpty()) {
      syntaxErrors.forEach(error -> result.addError(new RuleSetValidationError(
          RuleSetValidationErrorType.RULESET_FORMULA_VALIDATION_FAILURE, error)));
      return null;
    }

    return tree;
  }

  /**
   * Creates and configures a RuleSetFormulaParser with error listeners.
   * 
   * <p>Sets up both lexer and parser error listeners to collect syntax errors
   * during parsing. Uses DefaultErrorStrategy for error recovery.
   * 
   * @param lexer the configured lexer for tokenization
   * @param syntaxErrors list to collect syntax error messages
   * @return configured parser with error listeners attached
   */
  private static RuleSetFormulaParser getRuleSetFormulaParser(
      RuleSetFormulaLexer lexer,
      List<String> syntaxErrors
  ) {
    CommonTokenStream tokens = new CommonTokenStream(lexer);
    RuleSetFormulaParser parser = new RuleSetFormulaParser(tokens);

    parser.setErrorHandler(new DefaultErrorStrategy());

    // Collect lexer errors
    lexer.removeErrorListeners();
    lexer.addErrorListener(new BaseErrorListener() {
      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                              int line, int charPos, String msg, RecognitionException e) {
        syntaxErrors.add("line " + line + ":" + charPos + " " + msg);
      }
    });

    // Collect parser errors
    parser.removeErrorListeners();
    parser.addErrorListener(new BaseErrorListener() {
      @Override
      public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol,
                              int line, int charPos, String msg, RecognitionException e) {
        syntaxErrors.add("line " + line + ":" + charPos + " " + msg);
      }
    });

    return parser;
  }

  // -----------------------------------------------------------------------
  // 2. Extract IDENTIFIER tokens using ANTLR visitor
  // -----------------------------------------------------------------------
  
  /**
   * Extracts all identifier tokens from the parsed expression tree.
   * 
   * <p>Uses ANTLR visitor pattern to traverse the parse tree and collect
   * all IDENTIFIER tokens. These represent condition names referenced
   * in the rule expression.
   * 
   * @param tree the parsed expression tree to traverse
   * @return set of all identifier strings found in the expression
   */
  private Set<String> extractIdentifiers(ParseTree tree) {
    return new RuleSetFormulaBaseVisitor<Set<String>>() {

      @Override
      public Set<String> visitTerminal(TerminalNode node) {
        Set<String> ids = new HashSet<>();
        if (node.getSymbol().getType() == RuleSetFormulaLexer.IDENTIFIER) {
          ids.add(node.getText());
        }
        return ids;
      }

      @Override
      protected Set<String> aggregateResult(Set<String> agg, Set<String> next) {
        if (agg == null) return next;
        if (next != null) agg.addAll(next);
        return agg;
      }

    }.visit(tree);
  }

  // -----------------------------------------------------------------------
  // 3. Condition name validations
  // -----------------------------------------------------------------------
  
  /**
   * Extracts the names of all declared conditions from the rule file.
   * 
   * @param ruleSet the rule file containing condition definitions
   * @return set of declared criterion names, empty set if no conditions defined
   */
  private Set<String> getDeclaredCriteria(RuleSet ruleSet) {
    return ruleSet.getCriteria() == null
           ? new HashSet<>()
           : ruleSet.getCriteria().stream()
                    .map(Criterion::getName)
                    .collect(Collectors.toSet());
  }

  /**
   * Validates that all used identifiers are declared and vice versa.
   * 
   * <p>Performs cross-reference validation to ensure:
   * <ul>
   *   <li>All identifiers used in the expression are declared as conditions</li>
   *   <li>All declared criteria are actually used in the expression</li>
   * </ul>
   * 
   * @param used set of identifiers found in the rule expression
   * @param declared set of criterion names declared in the rule file
   * @param result validation result to add errors to
   */
  private void validateIdentifiers(Set<String> used,
                                   Set<String> declared,
                                   RuleSetValidationResult result) {

    // used but not declared
    used.stream()
        .filter(u -> !declared.contains(u))
        .forEach(u -> result.addError(new com.jrules.ruleengine.model.RuleSetValidationError(
            RuleSetValidationErrorType.RULESET_FORMULA_VALIDATION_FAILURE,
            "Condition '" + u + "' used in rule expression but not defined in conditions[]")));

    // declared but unused
    declared.stream()
            .filter(d -> !used.contains(d))
            .forEach(d -> result.addError(new com.jrules.ruleengine.model.RuleSetValidationError(
                RuleSetValidationErrorType.RULESET_FORMULA_VALIDATION_FAILURE,
                "Condition '" + d + "' is defined but never used in rule expression")));
  }
}
