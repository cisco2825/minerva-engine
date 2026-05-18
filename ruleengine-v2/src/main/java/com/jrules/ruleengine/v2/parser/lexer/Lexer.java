package com.jrules.ruleengine.v2.parser.lexer;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokenizes a V2 expression string into a list of Tokens.
 * Tracks line and column positions for each token to enable
 * position-specific error messages in the Studio UI.
 */
public class Lexer {

    private String input;
    private int pos;
    private int line;
    private int column;

    public List<Token> tokenize(String expression) {
        this.input = expression;
        this.pos = 0;
        this.line = 1;
        this.column = 1;

        List<Token> tokens = new ArrayList<>();

        while (pos < input.length()) {
            skipWhitespace();
            if (pos >= input.length()) break;

            Token token = nextToken();
            if (token != null) {
                tokens.add(token);
            }
        }

        tokens.add(new Token(TokenType.EOF, "", line, column));
        return tokens;
    }

    private Token nextToken() {
        int startLine = line;
        int startCol = column;
        char c = input.charAt(pos);

        // Numbers
        if (Character.isDigit(c)) {
            return readNumber(startLine, startCol);
        }

        // Strings
        if (c == '"' || c == '\'') {
            return readString(startLine, startCol);
        }

        // Identifiers and keywords
        if (Character.isLetter(c) || c == '_') {
            return readIdentifierOrKeyword(startLine, startCol);
        }

        // Lookup reference @name
        if (c == '@') {
            advance();
            if (pos < input.length() && (Character.isLetter(input.charAt(pos)) || input.charAt(pos) == '_')) {
                int nameStart = pos;
                int nameStartCol = column;
                while (pos < input.length() && isIdentChar(input.charAt(pos))) {
                    advance();
                }
                String name = input.substring(nameStart, pos);
                return new Token(TokenType.AT, name, startLine, startCol);
            }
            return new Token(TokenType.AT, "@", startLine, startCol);
        }

        // Two-char operators
        if (pos + 1 < input.length()) {
            String two = input.substring(pos, pos + 2);
            switch (two) {
                case "!=": advance(); advance(); return new Token(TokenType.NEQ, "!=", startLine, startCol);
                case "==": advance(); advance(); return new Token(TokenType.EQ,  "==", startLine, startCol);
                case "<=": advance(); advance(); return new Token(TokenType.LTE, "<=", startLine, startCol);
                case ">=": advance(); advance(); return new Token(TokenType.GTE, ">=", startLine, startCol);
                case "<>": advance(); advance(); return new Token(TokenType.NEQ, "<>", startLine, startCol);
            }
        }

        // Single-char operators and delimiters
        advance();
        switch (c) {
            case '=': return new Token(TokenType.EQ, "=", startLine, startCol);
            case '<': return new Token(TokenType.LT, "<", startLine, startCol);
            case '>': return new Token(TokenType.GT, ">", startLine, startCol);
            case '+': return new Token(TokenType.PLUS, "+", startLine, startCol);
            case '-': return new Token(TokenType.MINUS, "-", startLine, startCol);
            case '*': return new Token(TokenType.STAR, "*", startLine, startCol);
            case '/': return new Token(TokenType.SLASH, "/", startLine, startCol);
            case '%': return new Token(TokenType.PERCENT, "%", startLine, startCol);
            case '(': return new Token(TokenType.LPAREN, "(", startLine, startCol);
            case ')': return new Token(TokenType.RPAREN, ")", startLine, startCol);
            case '[': return new Token(TokenType.LBRACKET, "[", startLine, startCol);
            case ']': return new Token(TokenType.RBRACKET, "]", startLine, startCol);
            case ',': return new Token(TokenType.COMMA, ",", startLine, startCol);
            case '.': return new Token(TokenType.DOT, ".", startLine, startCol);
            case '?': return new Token(TokenType.QUESTION, "?", startLine, startCol);
            case ':': return new Token(TokenType.COLON,     ":",  startLine, startCol);
            case ';': return new Token(TokenType.SEMICOLON, ";",  startLine, startCol);
            default:
                throw new LexerException("Unexpected character '" + c + "'", startLine, startCol);
        }
    }

    private Token readNumber(int startLine, int startCol) {
        int start = pos;
        while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
            advance();
        }
        if (pos < input.length() && input.charAt(pos) == '.') {
            advance();
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                advance();
            }
        }
        // scientific notation: e.g. 1e10, 1.5E-3
        if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
            advance();
            if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) {
                advance();
            }
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) {
                advance();
            }
        }
        return new Token(TokenType.NUMBER, input.substring(start, pos), startLine, startCol);
    }

    private Token readString(int startLine, int startCol) {
        char quote = input.charAt(pos);
        advance(); // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < input.length() && input.charAt(pos) != quote) {
            char c = input.charAt(pos);
            if (c == '\\' && pos + 1 < input.length()) {
                advance();
                char escaped = input.charAt(pos);
                switch (escaped) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case '\\': sb.append('\\'); break;
                    case '\'': sb.append('\''); break;
                    case '"': sb.append('"'); break;
                    default: sb.append('\\'); sb.append(escaped);
                }
            } else {
                sb.append(c);
            }
            advance();
        }
        if (pos >= input.length()) {
            throw new LexerException("Unterminated string literal", startLine, startCol);
        }
        advance(); // skip closing quote
        return new Token(TokenType.STRING, sb.toString(), startLine, startCol);
    }

    private Token readIdentifierOrKeyword(int startLine, int startCol) {
        int start = pos;
        while (pos < input.length() && isIdentChar(input.charAt(pos))) {
            advance();
        }
        String word = input.substring(start, pos);
        TokenType type = keyword(word);
        if (type == TokenType.TRUE || type == TokenType.FALSE) {
            return new Token(TokenType.BOOLEAN, word.toLowerCase(), startLine, startCol);
        }
        if (type == TokenType.NULL) {
            return new Token(TokenType.NULL, "null", startLine, startCol);
        }
        return new Token(type, word, startLine, startCol);
    }

    private TokenType keyword(String word) {
        switch (word.toUpperCase()) {
            case "AND":         return TokenType.AND;
            case "OR":          return TokenType.OR;
            case "NOT":         return TokenType.NOT;
            case "IN":          return TokenType.IN;
            case "BETWEEN":     return TokenType.BETWEEN;
            case "IS":          return TokenType.IS;
            case "CONTAINS":    return TokenType.CONTAINS;
            case "STARTS_WITH": return TokenType.STARTS_WITH;
            case "ENDS_WITH":   return TokenType.ENDS_WITH;
            case "MATCHES":     return TokenType.MATCHES;
            case "TABLE":       return TokenType.TABLE;
            case "TRUE":        return TokenType.TRUE;
            case "FALSE":       return TokenType.FALSE;
            case "NULL":        return TokenType.NULL;
            case "LET":         return TokenType.LET;
            default:            return TokenType.IDENTIFIER;
        }
    }

    private void skipWhitespace() {
        while (pos < input.length()) {
            char c = input.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\r') {
                advance();
            } else if (c == '\n') {
                line++;
                column = 1;
                pos++;
            } else {
                break;
            }
        }
    }

    private void advance() {
        if (pos < input.length() && input.charAt(pos) == '\n') {
            line++;
            column = 1;
        } else {
            column++;
        }
        pos++;
    }

    private boolean isIdentChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
