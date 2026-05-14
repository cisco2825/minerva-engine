# Rule Engine V2 — Design Document

> **Audience:** Engineers building on or extending V2.  
> **Scope:** Architectural decisions, expression language, evaluation model, validation pipeline, and API contract.  
> **What is not covered:** Deployment, infrastructure, or Studio frontend specifics.

---

## 1. Motivation

V1 is a working rule engine in production. V2 is not an evolution of V1 — it is a ground-up redesign inspired by Finbox Sentinel, targeting credit decisioning and broader fintech workflows.

The two systems coexist in a multi-module Maven project. V2 reuses V1's infrastructure (Redis, S3) but shares no business logic.

**The core problem V1 could not solve cleanly:**  
V1's rule model requires writing logic twice — once in named sub-conditions, and again in a formula string that references them (`c1 AND (c2 OR c3)`). This is hard to read, error-prone, and does not map to how credit teams think about rules. V2 replaces this with direct boolean expressions and rule chaining.

---

## 2. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                       Studio / API Client                        │
└────────────────────────────┬────────────────────────────────────┘
                             │ POST /api/v2/policy/validate
                             │ POST /api/v2/policy/evaluate
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                     PolicyV2Controller                           │
└────────────┬────────────────────────────────┬───────────────────┘
             │                                │
             ▼                                ▼
┌────────────────────┐            ┌───────────────────────────────┐
│  RequestValidator  │            │   Policy Type Dispatcher       │
│  (7 stages)        │            │   RULE_CHAIN → RuleChainEval  │
└────────────────────┘            │   DECISION_TABLE → TableEval  │
                                  │   SCORECARD → ScorecardEval   │
                                  └───────────┬───────────────────┘
                                              │
                                              ▼
                                  ┌───────────────────────┐
                                  │  ExpressionEvaluator  │
                                  │  (walks AST)          │
                                  └──────┬────────────────┘
                                         │
                    ┌────────────────────┼────────────────────┐
                    ▼                    ▼                     ▼
          ┌──────────────┐    ┌──────────────────┐   ┌──────────────┐
          │ FunctionReg  │    │  LookupResolver  │   │  TableEval   │
          │ (builtins +  │    │  (INLINE / FILE) │   │ (TABLE() fn) │
          │  UDFs)       │    └──────────────────┘   └──────────────┘
          └──────────────┘
```

### Module structure

```
ruleengine/                   ← parent pom (aggregator)
├── ruleengine-v1/            ← unchanged production engine
└── ruleengine-v2/            ← this document
```

V2 declares V1 as a Maven dependency to reuse `S3Service` and Redis infrastructure. The only change to V1 is a parent reference added to its `pom.xml`.

---

## 3. Evaluation Request Shape

Every call to `/evaluate` and `/validate` carries the full policy definition plus runtime data in the request body. The engine is **stateless** — no server-side policy storage in V2 phase 1.

```json
{
  "policy": { ... },
  "udfs": [ ... ],
  "tables": { "tableName": { ... } },
  "lookups": {
    "approved_cities": { "type": "INLINE", "values": ["Mumbai", "Delhi"] },
    "pincode_blocklist": { "type": "FILE", "fileRef": "bucket/blocklist.csv", "format": "CSV", "extractKey": "pincode" }
  },
  "context": {
    "applicant": { "income": 75000, "cibil_score": 720, "city": "Mumbai" }
  },
  "traceLevel": "STANDARD"
}
```

**Why context is in the request, not fetched server-side:**  
Context enrichment (bureau pull, bank statement parsing) is an orchestration concern. Keeping the engine stateless makes it predictable, easy to test, and horizontally scalable. An orchestration layer can pre-fetch context and pass it in.

---

## 4. Policy Types

### 4.1 RULE_CHAIN

Rules evaluated in priority order. Each rule has a direct boolean expression, an `onPass` action, and an `onFail` action. Evaluation stops when a `STOP` action is reached.

```json
{
  "type": "RULE_CHAIN",
  "rules": [
    {
      "name": "income_check",
      "priority": 1,
      "expression": "applicant.income >= 30000",
      "onPass": { "type": "CONTINUE" },
      "onFail": { "type": "STOP", "outcome": "REJECTED", "reason": "Income below minimum" },
      "onMissing": "FAIL"
    },
    {
      "name": "cibil_check",
      "priority": 2,
      "expression": "applicant.cibil_score >= 700",
      "onPass": { "type": "STOP", "outcome": "APPROVED" },
      "onFail": { "type": "STOP", "outcome": "MANUAL_REVIEW" },
      "onMissing": "SKIP"
    }
  ],
  "defaultAction": { "type": "STOP", "outcome": "MANUAL_REVIEW" }
}
```

**Why rule chaining, not conditions + formula:**  
The V1 model (and many BRE platforms) separate logic into named sub-conditions and a formula string combining them. This describes the same logic twice. The formula is meaningless without cross-referencing the condition list, and it does not produce per-rule outcomes — just a single pass/fail. Rule chaining maps naturally to how credit teams think: sequential filters, each with its own outcome.

### 4.2 DECISION_TABLE

A structured table of input conditions mapped to an output value. Used for eligibility tiers, interest rate bands, product mapping, etc.

```json
{
  "type": "DECISION_TABLE",
  "table": {
    "name": "loanTierTable",
    "hitPolicy": "FIRST",
    "inputs": [
      { "name": "income",     "param": "applicant.income",      "datatype": "NUMBER" },
      { "name": "cibil",      "param": "applicant.cibil_score", "datatype": "NUMBER" }
    ],
    "output": { "name": "tier", "datatype": "TEXT" },
    "rows": [
      { "priority": 1, "conditions": [{ "type": "CONDITION", "operator": "gte", "value": 100000 }, { "type": "CONDITION", "operator": "gte", "value": 750 }], "output": "PLATINUM" },
      { "priority": 2, "conditions": [{ "type": "CONDITION", "operator": "gte", "value": 50000  }, { "type": "ANY" }],                                          "output": "GOLD" },
      { "priority": 3, "conditions": [{ "type": "ANY" },                                           { "type": "ANY" }],                                          "output": "STANDARD" }
    ]
  }
}
```

**Hit policies:** `FIRST` (return first matching row), `UNIQUE` (assert exactly one row matches).

**Why ALL hit policy is not supported:**  
`ALL` would return a list of output values. `TABLE()` is callable from inside rule expressions — e.g. `TABLE("tierTable", applicant.income, applicant.cibil) = "PLATINUM"`. This requires a scalar return value. A list-returning `TABLE()` would break expression semantics. `ALL` can be added in a future version once list-returning expression semantics are defined.

**Cell condition types:**
- `ANY` — always matches (wildcard)
- `CONDITION` — structured: `operator` + `value` (single) or `values` (list for `in`, `notIn`, `bt`)

### 4.3 SCORECARD

Evaluates each variable against bands of conditions, assigns points, sums them, and maps the total to an outcome via thresholds.

```json
{
  "type": "SCORECARD",
  "scorecard": {
    "name": "creditScorecard",
    "variables": [
      {
        "name": "income_band",
        "param": "applicant.income",
        "onMissing": "SKIP",
        "defaultPoints": 0,
        "bands": [
          { "operator": "gte", "value": 100000, "points": 30, "label": "High income" },
          { "operator": "gte", "value": 50000,  "points": 20, "label": "Medium income" },
          { "operator": "lt",  "value": 50000,  "points": 5,  "label": "Low income" }
        ]
      }
    ],
    "thresholds": [
      { "min": 60, "max": 100, "outcome": "APPROVED",       "label": "Low risk" },
      { "min": 40, "max": 59,  "outcome": "MANUAL_REVIEW",  "label": "Medium risk" },
      { "min": 0,  "max": 39,  "outcome": "REJECTED",       "label": "High risk" }
    ],
    "defaultOutcome": "MANUAL_REVIEW"
  }
}
```

**Band matching:** First-match-wins (top to bottom within each variable). Bands use structured operator+value, not expressions — band conditions always check one variable against a threshold; a full expression parser is overkill and would make the Studio UI harder to build.

---

## 5. Expression Language

### 5.1 Grammar (operator precedence, lowest to highest)

```
expression   → ternary
ternary      → or ( '?' ternary ':' ternary )?
or           → and ( OR and )*
and          → not ( AND not )*
not          → NOT not | comparison
comparison   → addSub (
                  ( '=' | '!=' | '<' | '<=' | '>' | '>=' ) addSub
                | BETWEEN addSub AND addSub
                | NOT BETWEEN addSub AND addSub
                | IN ( list | @name )
                | NOT IN ( list | @name )
                | IS NULL
                | IS NOT NULL
                | CONTAINS addSub
                | STARTS_WITH addSub
                | ENDS_WITH addSub
                | MATCHES addSub
               )?
addSub       → mulDiv ( ('+' | '-') mulDiv )*
mulDiv       → unary ( ('*' | '/' | '%') unary )*
unary        → '-' unary | primary
primary      → '(' expression ')'
             | NUMBER | STRING | BOOLEAN | NULL
             | @name
             | '[' (expression (',' expression)*)? ']'
             | IDENTIFIER ('.' IDENTIFIER)*
             | IDENTIFIER '(' (expression (',' expression)*)? ')'
             | TABLE '(' (expression (',' expression)*)? ')'
```

### 5.2 Types

| Type | Literals | Notes |
|---|---|---|
| NUMBER | `42`, `3.14`, `1.5e6` | All numbers are `double` internally |
| TEXT | `"hello"`, `'world'` | Single or double quoted, escape sequences supported |
| BOOLEAN | `true`, `false` | Case-insensitive |
| NULL | `null` | Explicit null |
| DATE | `"2024-01-15"` | ISO-8601 string; date ops parse automatically |

### 5.3 Operators

| Category | Operators |
|---|---|
| Logical | `AND`, `OR`, `NOT` |
| Comparison | `=`, `!=`, `<>`, `<`, `<=`, `>`, `>=` |
| Range | `BETWEEN low AND high`, `NOT BETWEEN` |
| Membership | `IN [list]`, `IN @lookup`, `NOT IN` |
| Null check | `IS NULL`, `IS NOT NULL` |
| String | `CONTAINS`, `STARTS_WITH`, `ENDS_WITH`, `MATCHES` (regex) |
| Arithmetic | `+`, `-`, `*`, `/`, `%` |
| Ternary | `condition ? thenValue : elseValue` |

### 5.4 Context paths

Dotted notation traverses the nested context map:

```
applicant.bureau.cibil_score
applicant.employment.monthly_income
```

A missing segment throws `MissingValueException`, handled by the rule's `onMissing` policy.

### 5.5 Lookup references

```
applicant.city IN @approved_cities
applicant.pincode NOT IN @blocked_pincodes
```

`@name` resolves to the lookup's value list. Lookups can be `INLINE` (values in the request) or `FILE` (fetched from S3, Redis-cached).

### 5.6 Expression examples

```
# Simple comparison
applicant.income >= 50000

# Compound
applicant.income >= 50000 AND applicant.cibil_score >= 700

# BETWEEN
applicant.age BETWEEN 21 AND 60

# IN with inline list
applicant.employment_type IN ["SALARIED", "SELF_EMPLOYED"]

# IN with lookup
applicant.city IN @tier_1_cities

# Ternary
applicant.employment_type = "SALARIED" ? applicant.gross_salary : applicant.net_profit

# Function call
DATEDIFF(TODAY(), applicant.date_of_birth, "YEARS") >= 21

# Decision table lookup from a rule expression
TABLE("loanTierTable", applicant.income, applicant.cibil_score) = "PLATINUM"

# UDF call
DTI(applicant.total_emi, applicant.income) <= 0.5

# IS NULL check
applicant.co_applicant_income IS NULL
```

---

## 6. Built-in Functions

| Category | Function | Signature | Description |
|---|---|---|---|
| Math | `ABS` | `ABS(n)` | Absolute value |
| | `ROUND` | `ROUND(n, scale?)` | Round to `scale` decimal places (default 0) |
| | `FLOOR` | `FLOOR(n)` | Floor |
| | `CEIL` | `CEIL(n)` | Ceiling |
| | `MIN` | `MIN(a, b, ...)` | Minimum of arguments |
| | `MAX` | `MAX(a, b, ...)` | Maximum of arguments |
| | `POW` | `POW(base, exp)` | Power |
| | `SQRT` | `SQRT(n)` | Square root |
| String | `UPPER` | `UPPER(s)` | Uppercase |
| | `LOWER` | `LOWER(s)` | Lowercase |
| | `TRIM` | `TRIM(s)` | Strip whitespace |
| | `LENGTH` | `LENGTH(s)` | String length |
| | `CONCAT` | `CONCAT(s1, s2, ...)` | Concatenate |
| | `SUBSTR` | `SUBSTR(s, start, length?)` | Substring |
| | `REPLACE` | `REPLACE(s, find, replace)` | Replace all occurrences |
| Date | `TODAY` | `TODAY()` | Current date as `yyyy-MM-dd` string |
| | `DATE` | `DATE(s)` | Parse date string |
| | `DATEDIFF` | `DATEDIFF(d1, d2, unit?)` | Difference in DAYS / MONTHS / YEARS |
| | `DATEADD` | `DATEADD(date, amount, unit)` | Add DAYS / MONTHS / YEARS |
| Logic | `IF` | `IF(condition, thenVal, elseVal)` | Conditional (eager — prefer ternary for short-circuit) |
| | `COALESCE` | `COALESCE(v1, v2, ...)` | First non-null value |
| | `NULLIF` | `NULLIF(a, b)` | Returns null if `a = b`, else `a` |
| Type | `TO_NUMBER` | `TO_NUMBER(v)` | Convert to number |
| | `TO_STRING` | `TO_STRING(v)` | Convert to string |
| Special | `TABLE` | `TABLE("name", arg1, ...)` | Evaluate a decision table inline |

`TABLE` is not a regular function — it accesses the `tables` map from the request. The first argument must be a string literal (table name). The remaining arguments are evaluated as input values and matched against the table's input columns in order.

---

## 7. User-Defined Functions (UDFs)

UDFs are named, parameterized, pure functions defined in the evaluation request. They use the same expression grammar as rule expressions.

```json
{
  "udfs": [
    {
      "name": "DTI",
      "params": [
        { "name": "total_emi", "type": "NUMBER" },
        { "name": "income",    "type": "NUMBER" }
      ],
      "expression": "total_emi / income",
      "returnType": "NUMBER"
    },
    {
      "name": "FOIR",
      "params": [
        { "name": "fixed_obligations", "type": "NUMBER" },
        { "name": "gross_income",      "type": "NUMBER" }
      ],
      "expression": "fixed_obligations / gross_income",
      "returnType": "NUMBER"
    }
  ]
}
```

Usage in a rule:
```
DTI(applicant.total_emi, applicant.monthly_income) <= 0.45
AND FOIR(applicant.fixed_obligations, applicant.monthly_income) <= 0.55
```

**Key constraints:**
- UDF bodies can only reference declared params and other UDFs/built-ins — **not context paths**. This keeps UDFs pure, reusable, and testable independent of context shape.
- UDFs are per-request. No server-side UDF storage in phase 1.
- Built-in names take precedence over UDF names (a UDF shadowing a built-in produces a validation warning).

**Why not Groovy/JEXL for UDFs:**  
Scripting languages allow arbitrary code execution — a security and governance risk in a multi-tenant system. The no-code philosophy requires expressions writable and readable by business analysts, not developers. The same expression grammar used for rules is familiar, auditable, and sandboxed by design.

---

## 8. Lookups

Lookups are named value lists referenced via `@name` in any expression.

| Type | Definition | Resolution |
|---|---|---|
| `INLINE` | `"values": ["A", "B", "C"]` | Values returned directly from the request |
| `FILE` | `"fileRef": "bucket/key.json"`, `"format": "JSON"/"CSV"`, `"extractKey": "columnName"` | Fetched from S3 via V1's `S3Service`; Redis-cached by `fileRef` |

**Why a unified `@name` concept instead of `REQUEST_LOOKUP` / `STORED_LOOKUP`:**  
Earlier naming proposals (request lookup vs stored lookup) leaked the implementation source into the user-facing model. A rule author should not need to know or care where a lookup comes from — they reference it by name. The engine resolves the source transparently.

---

## 9. `onMissing` Behavior

Each rule (and each scorecard variable) has an `onMissing` field controlling what happens when a context path referenced in the expression is absent.

| Value | Behavior |
|---|---|
| `FAIL` | Evaluation stops; returns an error. Default for rules. |
| `PASS` | Rule treated as passed; chain continues with `onPass` action. |
| `SKIP` | Rule is removed from the chain entirely for this evaluation — not PASS, not FAIL. |

**Why SKIP is essential:**  
Without SKIP, segment-specific rules (e.g., "GST number must be valid" for self-employed applicants only) would have to hard-fail when the field is absent — even for applicants to whom the rule doesn't apply. PASS is incorrect too (it grants the benefit of the doubt to inapplicable rules). SKIP treats the rule as irrelevant for this context, which is the only semantically correct outcome.

Scorecard variables default to `SKIP` (a variable for which data is unavailable simply contributes no points, which is usually the right behaviour).

---

## 10. Validation Pipeline

`POST /api/v2/policy/validate` runs all 7 stages before returning. **All errors are collected — the pipeline never stops at the first failure.**

| Stage | What is checked |
|---|---|
| **Stage 0** — Request structure | Policy is present, type is set, ID is non-blank |
| **Stage 1** — UDFs | Unique names, no built-in shadowing, param names/types, expression parse check |
| **Stage 2** — Lookups | INLINE has values, FILE has valid `fileRef`/format, extractKey warnings |
| **Stage 3** — Tables | Input/output/hitPolicy present, row condition count matches input column count, cell values match operator arity |
| **Stage 4** — Policy structure | RULE_CHAIN has rules, DECISION_TABLE has a table, SCORECARD has variables |
| **Stage 5** — Rule structure | Unique rule names, expressions present, `onPass`/`onFail` defined, STOP actions have outcomes |
| **Stage 6** — Expression semantics | Each expression is lexed and parsed; AST is walked to check unknown functions, wrong UDF argument counts, unknown `@lookup` references, `TABLE()` name must be a string literal and the table must exist |

**Validation response:**

```json
{
  "valid": false,
  "errors": [
    {
      "code": "EXPR.UNKNOWN_FUNCTION",
      "severity": "ERROR",
      "stage": "STAGE_6_EXPRESSION",
      "location": "policy.rules[income_check].expression",
      "message": "Unknown function: 'CALCULATE_DTI'",
      "suggestion": "Known functions: [ABS, CEIL, COALESCE, ...]",
      "position": { "line": 1, "column": 14, "length": 13 }
    }
  ],
  "warnings": [
    {
      "code": "RULE.NO_STOP_ACTION",
      "severity": "WARNING",
      "stage": "STAGE_5_RULE",
      "location": "policy.rules",
      "message": "No rule has a STOP action — policy will always fall through to the default action"
    }
  ]
}
```

The `position` field (line/column/length) is used by the Studio editor to underline the exact token in the expression input.

---

## 11. Trace Levels

Configurable per evaluation request via `traceLevel`.

| Level | What is included in the response |
|---|---|
| `MINIMAL` | `outcome`, `policyId`, `version`, `triggeredBy` |
| `STANDARD` | + per-rule/variable/row results, skipped rules (default) |
| `FULL` | + full expression text, resolved values at each step (Studio debugging) |

Use `MINIMAL` in high-throughput production paths. Use `FULL` when debugging a policy in Studio.

---

## 12. API

### `POST /api/v2/policy/validate`

Validates a policy definition without executing it. Returns errors and warnings across all 7 stages.

**Request:** `ValidationRequest` — `policy`, `udfs`, `tables`, `lookups` (no `context`, no `traceLevel`)  
**Response:** `ValidationResult` — `valid`, `errors[]`, `warnings[]`  
**Status codes:** `200 OK` always (validation errors are in the body, not HTTP status)

### `POST /api/v2/policy/evaluate`

Evaluates a policy against a context. Policy type is dispatched automatically.

**Request:** `EvaluationRequest` — `policy`, `udfs`, `tables`, `lookups`, `context`, `traceLevel`  
**Response:** `EvaluationResult` — unified model for all three policy types  
**Status codes:** `200 OK`, `400 Bad Request` (expression syntax error), `422 Unprocessable Entity` (semantic/evaluation error), `500 Internal Server Error`

### Error responses (RFC 7807 Problem JSON)

```json
{
  "type": "https://ruleengine.jrules.com/problems/expression-parse-error",
  "title": "Expression Parse Error",
  "status": 400,
  "detail": "Expected BETWEEN or IN after NOT at 1:18",
  "line": 1,
  "column": 18
}
```

| Exception | HTTP status | Problem type |
|---|---|---|
| `LexerException` | 400 | `expression-lex-error` |
| `ParseException` | 400 | `expression-parse-error` |
| `MissingValueException` | 422 | `missing-context-value` |
| `EvaluationException` | 422 | `evaluation-error` |
| Spring MVC (bad JSON, wrong method, etc.) | varies | handled by `ProblemHandling` |
| Unhandled `Exception` | 500 | `internal-error` (detail suppressed) |

---

## 13. Design Decisions & Rejected Alternatives

### D1 — Rule chaining with direct expressions

**Decided:** Each rule is a direct boolean expression with `onPass`/`onFail` actions.  
**Rejected:** Conditions + formula (V1 model) — named sub-conditions combined via a formula string.  
**Why rejected:** Logic is described twice. The formula string (`c1 AND (c2 OR c3)`) is meaningless without cross-referencing the named conditions. Rule chaining with direct expressions is self-contained, readable, and produces richer per-rule outcomes.

### D2 — Hand-written recursive descent parser

**Decided:** Recursive descent parser in Java, position-tracked.  
**Rejected:** ANTLR4 (used in V1).  
**Why rejected for this use case:** ANTLR default error messages are cryptic (`mismatched input 'x' expecting {...}`). For Studio UI — where business users see validation errors highlighted in an expression editor — error message quality matters. A hand-written parser gives full control over what gets reported, where, and with what suggestion text. ANTLR remains a viable future option if the grammar grows significantly.  
**Rejected:** SpEL, MVEL, JEXL.  
**Why:** Full expression freedom is a security risk in a multi-tenant system. These engines allow arbitrary code execution. The grammar must be controlled.

### D3 — UDFs: single-expression, pure functions

**Decided:** UDFs are named parameterized single-expression functions; body uses the same grammar as rules.  
**Rejected:** Multi-step UDFs (step[] + return).  
**Why rejected:** Adds model complexity without covering real use cases that a single expression cannot handle. Most fintech computations (DTI, FOIR, EMI) are single arithmetic expressions.  
**Constraint enforced:** UDF bodies cannot reference context paths — only their declared params and other functions. This keeps UDFs pure and reusable across contexts.

### D4 — Decision tables: TABLE() callable from rule expressions

**Decided:** Decision tables are both a top-level policy type and callable from rule expressions via `TABLE("name", args...)`.  
**Rejected:** Keeping tables as standalone policies only (not callable from expressions).  
**Why rejected:** Cross-referencing unlocks powerful compositions — e.g., "approve if TABLE(loanTierTable) is PLATINUM AND applicant.income >= 100000". This was required from day one, not deferred.  
**Why ALL hit policy is excluded:** `TABLE()` must return a scalar for use in expressions. `ALL` returns a list, which breaks expression semantics. Excluded until list-returning expression semantics are formally defined.

### D5 — Scorecards: structured bands, not expression bands

**Decided:** Band conditions use structured `operator + value`, not expression strings.  
**Why:** Band conditions always test one variable against a threshold — never compound logic. Structured model maps directly to Studio UI (dropdown for operator, input for value). Parsing a full expression for what is always a simple threshold check is unnecessary complexity.

### D6 — Lookups: unified `@name` concept

**Decided:** Single `@name` reference for all lookups; two source types (`INLINE`, `FILE`) transparent to rule authors.  
**Rejected:** Separate `REQUEST_LOOKUP` / `STORED_LOOKUP` naming.  
**Why rejected:** Source names leaked the implementation into the user model. A rule author references `@approved_cities` — they don't need to know or care whether that list came from the request body or an S3 file.

### D7 — `onMissing` per rule: FAIL / PASS / SKIP

**Decided:** Three-value `onMissing` policy on each rule.  
**Why SKIP in particular:** Segment-specific rules (apply only to self-employed, only to co-applicant cases) need to be invisible when the data is absent. `FAIL` incorrectly blocks; `PASS` incorrectly grants. `SKIP` correctly treats the rule as irrelevant.

### D8 — Multi-module Maven, V2 depends on V1

**Decided:** V2 is a separate Maven module. V2 reuses V1's `S3Service` and Redis via Maven dependency.  
**Constraint:** Only change to V1 is adding a `<parent>` reference in its `pom.xml`. No V1 source is touched.  
**Why V2 depends on V1 (not shared module):** A `ruleengine-common` module would require refactoring V1 to extract its infrastructure into a separate artifact. That touches production code unnecessarily. V2 declaring V1 as a dependency achieves reuse with zero V1 risk.

### D9 — Three trace levels

**Decided:** `MINIMAL` / `STANDARD` / `FULL` trace depth, configurable per request.  
**Why not always-full:** FULL trace involves serializing resolved values for every AST sub-expression. In high-throughput production scoring (50k+ decisions/day), this overhead is unacceptable. MINIMAL is appropriate for production; FULL for Studio debugging.

### D10 — Context in request only (phase 1)

**Decided:** Applicant context is passed in the evaluation request.  
**Not implemented:** Server-side context enrichment (bureau fetch, bank statement, etc.).  
**Why deferred:** Context enrichment is an orchestration concern, not an engine concern. Mixing them makes the engine harder to test and less predictable. An orchestration layer (workflow engine) sits above the rule engine and passes pre-fetched context in.

---

## 14. Package Structure

```
com.jrules.ruleengine.v2
├── api/
│   ├── PolicyV2Controller.java          POST /validate, POST /evaluate
│   └── exception/
│       └── V2ExceptionHandler.java      RFC 7807 error mapping
├── config/
│   └── JacksonConfig.java               Problem JSON module registration
├── evaluator/
│   ├── expression/
│   │   └── DefaultExpressionEvaluator   AST walker; resolves context, functions, lookups
│   ├── rule/
│   │   └── DefaultRuleChainEvaluator    Priority-ordered rule evaluation, onMissing
│   ├── table/
│   │   └── DefaultDecisionTableEvaluator FIRST/UNIQUE hit policies; TABLE() inline eval
│   └── scorecard/
│       └── DefaultScorecardEvaluator    Band matching, score summation, threshold mapping
├── exception/
│   ├── EvaluationException              Semantic evaluation errors
│   └── MissingValueException            Missing context path; caught per-rule for onMissing
├── function/
│   ├── BuiltinFunction                  Interface: name() + invoke(args)
│   ├── FunctionRegistry                 Interface: resolve built-ins by name
│   ├── DefaultFunctionRegistry          Spring singleton; registers 24 built-ins
│   └── builtin/
│       └── Builtins.java                All 24 built-in function implementations
├── lookup/
│   ├── LookupResolver                   Interface: resolve(@name, lookups map) → List
│   └── DefaultLookupResolver            INLINE direct; FILE via S3Service + @Cacheable
├── model/
│   ├── enums/                           DataType, HitPolicy, OnMissing, Operator, PolicyType, TraceLevel
│   ├── lookup/                          Lookup (abstract), InlineLookup, FileLookup
│   ├── policy/                          Policy
│   ├── request/                         EvaluationRequest, ValidationRequest
│   ├── result/                          EvaluationResult, ValidationResult, RuleResult, ...
│   ├── rule/                            Rule, RuleAction
│   ├── scorecard/                       Scorecard, ScorecardVariable, ScoreBand, ScoreThreshold
│   ├── table/                           DecisionTable, InputColumn, OutputColumn, TableRow, CellCondition
│   └── udf/                             UDF, UDFParam
├── parser/
│   ├── ExpressionParser.java            Recursive descent parser; token list → AST
│   ├── ParseException.java              Carries line/column of syntax error
│   ├── ast/
│   │   ├── ExpressionNode.java          Base class with line/column/length
│   │   └── Nodes.java                   All 14 AST node types
│   └── lexer/
│       ├── Lexer.java                   Tokenizer with position tracking
│       ├── Token.java                   type + value + line + column
│       ├── TokenType.java               All token types
│       └── LexerException.java          Carries line/column of lex error
└── validator/
    ├── RequestValidator                 Interface: 7-stage orchestration
    ├── DefaultRequestValidator          Orchestrates stages 0–6
    ├── ValidationContext.java           Error/warning accumulator
    ├── ExpressionAstValidator.java      Walks AST for semantic errors
    └── stage/
        ├── Stage0RequestValidator       Request structure
        ├── Stage1UdfValidator           UDF definitions
        ├── Stage2LookupValidator        Lookup definitions
        ├── Stage3TableValidator         Table structure
        ├── Stage4PolicyValidator        Policy-type-specific structure
        ├── Stage5RuleValidator          Rule chain structure
        └── Stage6ExpressionValidator    Parse + AST semantic check per expression
```

---

## 15. Future Work

The following were explicitly deferred from V2 phase 1:

| Item | Notes |
|---|---|
| Multi-output columns in decision tables | Currently single output only |
| `ALL` hit policy | Requires defining list-returning semantics for `TABLE()` in expressions |
| Multi-step UDFs | For computations requiring intermediate variables |
| Server-side UDF / policy storage | Engine is stateless in phase 1; orchestration layer handles storage |
| Context enrichment | Bureau pull, bank statement parsing — orchestration concern, not engine concern |
| Workflow orchestration | Chaining multiple policies in sequence |
| Champion/Challenger deployment | Route traffic between policy versions for A/B testing |
| Simulator and backtesting | Replay historical decision data against a new policy version |
| Cross-policy references | Calling another policy's result from within an expression |
