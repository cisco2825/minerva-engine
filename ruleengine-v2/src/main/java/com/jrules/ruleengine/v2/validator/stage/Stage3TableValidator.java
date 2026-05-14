package com.jrules.ruleengine.v2.validator.stage;

import com.jrules.ruleengine.v2.model.table.CellCondition;
import com.jrules.ruleengine.v2.model.table.DecisionTable;
import com.jrules.ruleengine.v2.model.table.TableRow;
import com.jrules.ruleengine.v2.validator.ValidationContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class Stage3TableValidator {

    private static final String STAGE = "STAGE_3_TABLE";

    public void validate(ValidationContext ctx) {
        Map<String, DecisionTable> tables = ctx.getRequest().getTables();
        if (tables == null || tables.isEmpty()) return;

        for (Map.Entry<String, DecisionTable> entry : tables.entrySet()) {
            String key = entry.getKey();
            DecisionTable table = entry.getValue();
            String loc = "tables[" + key + "]";

            if (table == null) {
                ctx.error("TABLE.NULL", STAGE, loc, "Table '" + key + "' is null");
                continue;
            }
            if (isBlank(table.getName())) {
                ctx.error("TABLE.MISSING_NAME", STAGE, loc, "Table must have a name");
            }
            if (table.getHitPolicy() == null) {
                ctx.error("TABLE.MISSING_HIT_POLICY", STAGE, loc,
                        "Table '" + key + "' must specify a hitPolicy (FIRST or UNIQUE)");
            }
            if (table.getInputs() == null || table.getInputs().isEmpty()) {
                ctx.error("TABLE.NO_INPUTS", STAGE, loc,
                        "Table '" + key + "' must have at least one input column");
            }
            if (table.getOutput() == null) {
                ctx.error("TABLE.NO_OUTPUT", STAGE, loc,
                        "Table '" + key + "' must define an output column");
            }
            if (table.getRows() == null || table.getRows().isEmpty()) {
                ctx.error("TABLE.NO_ROWS", STAGE, loc,
                        "Table '" + key + "' must have at least one row");
                continue;
            }

            int inputCount = table.getInputs() == null ? 0 : table.getInputs().size();
            for (int r = 0; r < table.getRows().size(); r++) {
                validateRow(table.getRows().get(r), r, inputCount, loc, ctx);
            }
        }
    }

    private void validateRow(TableRow row, int rowIdx, int inputCount, String tableLoc, ValidationContext ctx) {
        String rowLoc = tableLoc + ".rows[" + rowIdx + "]";
        List<CellCondition> conditions = row.getConditions();

        if (conditions == null || conditions.size() != inputCount) {
            ctx.error("TABLE.ROW_CONDITION_COUNT_MISMATCH", STAGE, rowLoc,
                    "Row " + rowIdx + " has " + (conditions == null ? 0 : conditions.size())
                            + " condition(s) but table has " + inputCount + " input column(s)");
            return;
        }

        for (int c = 0; c < conditions.size(); c++) {
            CellCondition cell = conditions.get(c);
            String cellLoc = rowLoc + ".conditions[" + c + "]";
            if (cell == null) {
                ctx.error("TABLE.CELL_NULL", STAGE, cellLoc, "Cell condition must not be null");
                continue;
            }
            if (cell.getType() == CellCondition.CellType.ANY) continue;

            if (cell.getOperator() == null) {
                ctx.error("TABLE.CELL_MISSING_OPERATOR", STAGE, cellLoc,
                        "CONDITION cell must specify an operator");
                continue;
            }
            // Multi-value operators need the values list
            if (cell.getOperator().isVariableCount() || cell.getOperator().expectedCount > 1) {
                if (cell.getValues() == null || cell.getValues().isEmpty()) {
                    ctx.error("TABLE.CELL_MISSING_VALUES", STAGE, cellLoc,
                            "Operator '" + cell.getOperator().token + "' requires a values list");
                }
            } else {
                // Single-value operators
                if (cell.getValue() == null) {
                    ctx.error("TABLE.CELL_MISSING_VALUE", STAGE, cellLoc,
                            "Operator '" + cell.getOperator().token + "' requires a single value");
                }
            }
        }
    }

    private boolean isBlank(String s) { return s == null || s.isBlank(); }
}
