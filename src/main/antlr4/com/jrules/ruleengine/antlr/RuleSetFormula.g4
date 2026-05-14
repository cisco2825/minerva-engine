grammar RuleSetFormula;

// entry
parse
    : expr EOF
    ;

expr
    : orExpr
    ;

// lowest precedence
orExpr
    : andExpr (OR andExpr)*
    ;

// medium precedence
andExpr
    : notExpr (AND notExpr)*
    ;

// highest precedence (NOT)
notExpr
    : NOT notExpr          // recursive for multiple NOTs: NOT NOT A
    | atom
    ;

// base element
atom
    : IDENTIFIER
    | LPAREN expr RPAREN
    ;

// lexer rules
AND: [Aa][Nn][Dd];
OR:  [Oo][Rr];
NOT: [Nn][Oo][Tt];

IDENTIFIER: [a-zA-Z_][a-zA-Z0-9_]*;

LPAREN: '(';
RPAREN: ')';

WS: [ \t\r\n]+ -> skip;
