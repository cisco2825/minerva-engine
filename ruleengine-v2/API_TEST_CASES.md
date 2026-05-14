# V2 Rule Engine — API Test Cases

Base URL: `http://localhost:8080`

Run the application with the `local` profile (no Redis or S3 required):
```bash
java -jar target/ruleengine-v2-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

---

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v2/policy/validate` | Validate a policy definition — returns all errors and warnings |
| POST | `/api/v2/policy/evaluate` | Evaluate a policy against a context — returns outcome and trace |

---

## Request Shape

### Validate
```json
{
  "policy":  { ... },
  "udfs":    [ ... ],       // optional
  "tables":  { "name": { ... } },  // optional — standalone tables for TABLE() references
  "lookups": { "name": { ... } }   // optional — INLINE or FILE lookup definitions
}
```

### Evaluate
```json
{
  "policy":     { ... },
  "udfs":       [ ... ],
  "tables":     { ... },
  "lookups":    { ... },
  "context":    { "key": "value", "nested": { "key": "value" } },
  "traceLevel": "NONE | STANDARD | FULL"
}
```

### Policy skeleton
```json
{
  "id":      "string",
  "name":    "string",
  "version": "string",
  "type":    "RULE_CHAIN | DECISION_TABLE | SCORECARD",

  "rules":         [ ... ],   // RULE_CHAIN only
  "defaultAction": { ... },   // RULE_CHAIN only

  "table":    { ... },        // DECISION_TABLE only

  "scorecard": { ... }        // SCORECARD only
}
```

---

## Data Type Reference

| JSON value | `DataType` enum |
|------------|-----------------|
| `"NUMBER"` | numeric |
| `"TEXT"`   | string |
| `"DATE"`   | date string |
| `"BOOLEAN"` | boolean |

## Operator Reference

| Operator | Token | Arity | Description |
|----------|-------|-------|-------------|
| `EQ`  | `eq`  | 1 | Equal |
| `NEQ` | `neq` | 1 | Not equal |
| `GT`  | `gt`  | 1 | Greater than |
| `GTE` | `gte` | 1 | Greater than or equal |
| `LT`  | `lt`  | 1 | Less than |
| `LTE` | `lte` | 1 | Less than or equal |
| `BT`  | `bt`  | 2 | Between (inclusive) — use `values: [min, max]` |
| `IN`  | `in`  | n | In list — use `values: [...]` |
| `NOT_IN` | `notIn` | n | Not in list — use `values: [...]` |
| `CONTAINS` | `contains` | 1 | String contains |
| `STARTS_WITH` | `startsWith` | 1 | String starts with |
| `ENDS_WITH` | `endsWith` | 1 | String ends with |
| `MATCHES` | `matches` | 1 | Regex match |
| `BEFORE` | `before` | 1 | Date before |
| `AFTER`  | `after`  | 1 | Date after |

---

## Test Cases

---

### TC-01 — Validate: Valid RULE_CHAIN

Validates a two-rule loan approval policy. Expects `valid: true` with no errors.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/validate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "loan-approval",
      "name": "Loan Approval Policy",
      "version": "1.0",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Age Check",
          "expression": "applicant.age >= 18",
          "priority": 1,
          "onPass": {"type": "CONTINUE"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Applicant under 18"},
          "onMissing": "FAIL"
        },
        {
          "name": "Income Check",
          "expression": "applicant.income >= 30000",
          "priority": 2,
          "onPass": {"type": "STOP", "outcome": "APPROVED"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Insufficient income"},
          "onMissing": "SKIP"
        }
      ],
      "defaultAction": {"type": "STOP", "outcome": "REJECTED"}
    }
  }'
```

**Expected Response — 200 OK**
```json
{
  "valid": true,
  "errors": [],
  "warnings": []
}
```

---

### TC-02 — Validate: Expression Parse Error + Missing Name Warning

Policy with a dangling `AND` (parse error) and no `name` field. Expects errors with line/column position and a warning.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/validate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "bad-policy",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Bad Rule",
          "expression": "UNKNOWN_FN(x) > 5 AND",
          "priority": 1,
          "onPass": {"type": "CONTINUE"},
          "onFail": {"type": "STOP"}
        }
      ]
    }
  }'
```

**Expected Response — 200 OK**
```json
{
  "valid": false,
  "errors": [
    {
      "code": "RULE.ON_FAIL_STOP_MISSING_OUTCOME",
      "severity": "ERROR",
      "stage": "STAGE_5_RULE",
      "location": "policy.rules[Bad Rule].onFail",
      "message": "STOP action must specify an outcome"
    },
    {
      "code": "EXPR.PARSE_ERROR",
      "severity": "ERROR",
      "stage": "STAGE_6_EXPRESSION",
      "location": "policy.rules[Bad Rule].expression",
      "message": "Parse error: Unexpected token: [EOF '' at 1:22]",
      "suggestion": "Check operator usage, parentheses, and keyword spelling",
      "position": {"line": 1, "column": 22, "length": 1}
    }
  ],
  "warnings": [
    {
      "code": "REQ.POLICY_NAME_BLANK",
      "severity": "WARNING",
      "stage": "STAGE_0_REQUEST",
      "location": "policy.name",
      "message": "Policy name is blank — consider adding a descriptive name"
    }
  ]
}
```

---

### TC-03 — Evaluate RULE_CHAIN: Applicant Approved

Applicant age=25, income=55000. Both rules pass; outcome is `APPROVED` with full trace.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "loan-approval",
      "name": "Loan Approval Policy",
      "version": "1.0",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Age Check",
          "expression": "applicant.age >= 18",
          "priority": 1,
          "onPass": {"type": "CONTINUE"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Under 18"}
        },
        {
          "name": "Income Check",
          "expression": "applicant.income >= 30000",
          "priority": 2,
          "onPass": {"type": "STOP", "outcome": "APPROVED"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Insufficient income"}
        }
      ],
      "defaultAction": {"type": "STOP", "outcome": "REJECTED"}
    },
    "context": {
      "applicant": {"age": 25, "income": 55000}
    },
    "traceLevel": "FULL"
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "loan-approval",
  "policyVersion": "1.0",
  "policyType": "RULE_CHAIN",
  "outcome": "APPROVED",
  "triggeredBy": "Income Check",
  "ruleResults": [
    {
      "name": "Age Check",
      "expression": "applicant.age >= 18",
      "result": true,
      "action": "CONTINUE"
    },
    {
      "name": "Income Check",
      "expression": "applicant.income >= 30000",
      "result": true,
      "action": "STOP",
      "outcome": "APPROVED"
    }
  ],
  "evaluationMs": 1
}
```

---

### TC-04 — Evaluate RULE_CHAIN: Rejected at First Rule

Applicant age=16, income=55000. Fails age check and stops immediately. Income Check is not evaluated.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "loan-approval",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Age Check",
          "expression": "applicant.age >= 18",
          "priority": 1,
          "onPass": {"type": "CONTINUE"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Under 18"}
        },
        {
          "name": "Income Check",
          "expression": "applicant.income >= 30000",
          "priority": 2,
          "onPass": {"type": "STOP", "outcome": "APPROVED"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Insufficient income"}
        }
      ],
      "defaultAction": {"type": "STOP", "outcome": "REJECTED"}
    },
    "context": {
      "applicant": {"age": 16, "income": 55000}
    },
    "traceLevel": "FULL"
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "loan-approval",
  "policyType": "RULE_CHAIN",
  "outcome": "REJECTED",
  "triggeredBy": "Age Check",
  "ruleResults": [
    {
      "name": "Age Check",
      "expression": "applicant.age >= 18",
      "result": false,
      "action": "STOP",
      "outcome": "REJECTED"
    }
  ],
  "notEvaluated": ["Income Check"],
  "evaluationMs": 0
}
```

---

### TC-05 — Evaluate DECISION_TABLE: FIRST Hit Policy

Risk tier table with three rows. Applicant age=30, creditScore=720 matches the PRIME row (row 1).

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "risk-tier",
      "name": "Risk Tier Policy",
      "version": "1.0",
      "type": "DECISION_TABLE",
      "table": {
        "name": "risk-tier-table",
        "hitPolicy": "FIRST",
        "inputs": [
          {"name": "Age",   "param": "applicant.age",         "datatype": "NUMBER"},
          {"name": "Score", "param": "applicant.creditScore", "datatype": "NUMBER"}
        ],
        "output": {"name": "tier", "datatype": "TEXT"},
        "rows": [
          {
            "priority": 1,
            "conditions": [
              {"type": "CONDITION", "operator": "GTE", "value": 25},
              {"type": "CONDITION", "operator": "GTE", "value": 700}
            ],
            "output": "PRIME"
          },
          {
            "priority": 2,
            "conditions": [
              {"type": "ANY"},
              {"type": "CONDITION", "operator": "BT", "values": [600, 699]}
            ],
            "output": "STANDARD"
          },
          {
            "priority": 3,
            "conditions": [
              {"type": "ANY"},
              {"type": "CONDITION", "operator": "LT", "value": 600}
            ],
            "output": "SUBPRIME"
          }
        ]
      }
    },
    "context": {
      "applicant": {"age": 30, "creditScore": 720}
    }
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "risk-tier",
  "policyVersion": "1.0",
  "policyType": "DECISION_TABLE",
  "outcome": "PRIME",
  "tableOutput": "PRIME",
  "outputColumn": "tier",
  "tableInputs": {
    "Age": 30,
    "Score": 720
  },
  "evaluationMs": 2
}
```

---

### TC-06 — Evaluate SCORECARD: Full Breakdown

Credit scorecard with age and income variables. Applicant age=35, income=80000 scores 50 points → APPROVED.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "credit-score",
      "name": "Credit Scorecard",
      "version": "1.0",
      "type": "SCORECARD",
      "scorecard": {
        "name": "Credit Scorecard",
        "variables": [
          {
            "name": "Age Score",
            "param": "applicant.age",
            "onMissing": "SKIP",
            "bands": [
              {"operator": "LT",  "value": 25,            "points": 5,  "label": "Young"},
              {"operator": "BT",  "values": [25, 45],     "points": 20, "label": "Prime"},
              {"operator": "GTE", "value": 45,            "points": 10, "label": "Mature"}
            ]
          },
          {
            "name": "Income Score",
            "param": "applicant.income",
            "onMissing": "SKIP",
            "bands": [
              {"operator": "LT",  "value": 30000,         "points": 0,  "label": "Low"},
              {"operator": "BT",  "values": [30000, 70000], "points": 15, "label": "Medium"},
              {"operator": "GTE", "value": 70000,         "points": 30, "label": "High"}
            ]
          }
        ],
        "thresholds": [
          {"min": 40, "max": 100, "outcome": "APPROVED", "label": "Creditworthy"},
          {"min": 20, "max": 39,  "outcome": "REVIEW",   "label": "Manual Review"},
          {"min": 0,  "max": 19,  "outcome": "REJECTED", "label": "High Risk"}
        ],
        "defaultOutcome": "REJECTED"
      }
    },
    "context": {
      "applicant": {"age": 35, "income": 80000}
    },
    "traceLevel": "FULL"
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "credit-score",
  "policyVersion": "1.0",
  "policyType": "SCORECARD",
  "outcome": "APPROVED",
  "label": "Creditworthy",
  "totalScore": 50.0,
  "maxPossibleScore": 50.0,
  "breakdown": [
    {"variable": "Age Score",    "value": 35,    "pointsAwarded": 20.0, "label": "Prime"},
    {"variable": "Income Score", "value": 80000, "pointsAwarded": 30.0, "label": "High"}
  ],
  "skippedVariables": [],
  "evaluationMs": 5
}
```

---

### TC-07 — Evaluate RULE_CHAIN: Inline Lookup with IN Operator

Policy uses an `@allowedCountries` lookup defined inline. Applicant country `"IN"` is in the list → APPROVED.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "country-check",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Allowed Country",
          "expression": "applicant.country IN @allowedCountries",
          "priority": 1,
          "onPass": {"type": "STOP", "outcome": "APPROVED"},
          "onFail": {"type": "STOP", "outcome": "REJECTED", "reason": "Country not supported"},
          "onMissing": "FAIL"
        }
      ],
      "defaultAction": {"type": "STOP", "outcome": "REJECTED"}
    },
    "lookups": {
      "allowedCountries": {
        "type": "INLINE",
        "values": ["IN", "US", "GB", "SG"]
      }
    },
    "context": {
      "applicant": {"country": "IN"}
    }
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "country-check",
  "policyType": "RULE_CHAIN",
  "outcome": "APPROVED",
  "triggeredBy": "Allowed Country",
  "ruleResults": [
    {
      "name": "Allowed Country",
      "result": true,
      "action": "STOP",
      "outcome": "APPROVED"
    }
  ],
  "evaluationMs": 1
}
```

---

### TC-08 — Evaluate RULE_CHAIN: onMissing SKIP

The first rule references `applicant.employed` which is absent from the context. With `onMissing: SKIP` the rule is skipped and evaluation proceeds. Second rule passes → APPROVED.

**Request**
```bash
curl -X POST http://localhost:8080/api/v2/policy/evaluate \
  -H "Content-Type: application/json" \
  -d '{
    "policy": {
      "id": "optional-check",
      "type": "RULE_CHAIN",
      "rules": [
        {
          "name": "Optional Employment Check",
          "expression": "applicant.employed = true",
          "priority": 1,
          "onPass": {"type": "CONTINUE"},
          "onFail": {"type": "STOP", "outcome": "REJECTED"},
          "onMissing": "SKIP"
        },
        {
          "name": "Income Check",
          "expression": "applicant.income >= 25000",
          "priority": 2,
          "onPass": {"type": "STOP", "outcome": "APPROVED"},
          "onFail": {"type": "STOP", "outcome": "REJECTED"}
        }
      ],
      "defaultAction": {"type": "STOP", "outcome": "PENDING"}
    },
    "context": {
      "applicant": {"income": 30000}
    },
    "traceLevel": "FULL"
  }'
```

**Expected Response — 200 OK**
```json
{
  "policyId": "optional-check",
  "policyType": "RULE_CHAIN",
  "outcome": "APPROVED",
  "triggeredBy": "Income Check",
  "ruleResults": [
    {
      "name": "Income Check",
      "expression": "applicant.income >= 25000",
      "result": true,
      "action": "STOP",
      "outcome": "APPROVED"
    }
  ],
  "skippedRules": ["Optional Employment Check"],
  "evaluationMs": 1
}
```

---

## Error Response Shape (Zalando Problem — RFC 7807)

All 4xx / 5xx responses follow the Problem+JSON format:

```json
{
  "type":   "about:blank",
  "title":  "Bad Request",
  "status": "BAD_REQUEST",
  "detail": "JSON parse error: ...",
  "parameters": {}
}
```

Parse and evaluation errors include extension fields:

```json
{
  "type":   "about:blank",
  "title":  "Unprocessable Entity",
  "status": "UNPROCESSABLE_ENTITY",
  "detail": "Parse error at line 1, column 5",
  "line":   1,
  "column": 5
}
```

---

## Notes

- `traceLevel` controls response verbosity: `NONE` returns only `outcome`, `STANDARD` adds `triggeredBy`, `FULL` includes per-rule breakdown, skipped/not-evaluated lists.
- `onMissing` per rule: `FAIL` (throws error), `PASS` (treats as true), `SKIP` (skips the rule).
- `DECISION_TABLE` `hitPolicy`: `FIRST` returns the first matching row, `UNIQUE` throws if more than one row matches.
- FILE lookups require a real S3 connection and are not available in the `local` profile.
