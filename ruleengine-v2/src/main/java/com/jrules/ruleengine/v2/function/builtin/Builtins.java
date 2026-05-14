package com.jrules.ruleengine.v2.function.builtin;

import com.jrules.ruleengine.v2.exception.EvaluationException;
import com.jrules.ruleengine.v2.function.BuiltinFunction;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * All built-in functions for the V2 expression language.
 * Each is a stateless singleton registered in DefaultFunctionRegistry.
 */
public final class Builtins {

    private Builtins() {}

    // ── Math ─────────────────────────────────────────────────────────────────

    public static class AbsFunction implements BuiltinFunction {
        public String name() { return "ABS"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return Math.abs(toNum(args.get(0)));
        }
    }

    public static class RoundFunction implements BuiltinFunction {
        public String name() { return "ROUND"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 1);
            double val = toNum(args.get(0));
            int scale = args.size() > 1 ? (int) toNum(args.get(1)) : 0;
            double factor = Math.pow(10, scale);
            return Math.round(val * factor) / factor;
        }
    }

    public static class FloorFunction implements BuiltinFunction {
        public String name() { return "FLOOR"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return Math.floor(toNum(args.get(0)));
        }
    }

    public static class CeilFunction implements BuiltinFunction {
        public String name() { return "CEIL"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return Math.ceil(toNum(args.get(0)));
        }
    }

    public static class MinFunction implements BuiltinFunction {
        public String name() { return "MIN"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 1);
            return args.stream().mapToDouble(Builtins::toNum).min().getAsDouble();
        }
    }

    public static class MaxFunction implements BuiltinFunction {
        public String name() { return "MAX"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 1);
            return args.stream().mapToDouble(Builtins::toNum).max().getAsDouble();
        }
    }

    public static class PowFunction implements BuiltinFunction {
        public String name() { return "POW"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 2);
            return Math.pow(toNum(args.get(0)), toNum(args.get(1)));
        }
    }

    public static class SqrtFunction implements BuiltinFunction {
        public String name() { return "SQRT"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return Math.sqrt(toNum(args.get(0)));
        }
    }

    // ── String ────────────────────────────────────────────────────────────────

    public static class UpperFunction implements BuiltinFunction {
        public String name() { return "UPPER"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return toStr(args.get(0)).toUpperCase();
        }
    }

    public static class LowerFunction implements BuiltinFunction {
        public String name() { return "LOWER"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return toStr(args.get(0)).toLowerCase();
        }
    }

    public static class TrimFunction implements BuiltinFunction {
        public String name() { return "TRIM"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return toStr(args.get(0)).trim();
        }
    }

    public static class LengthFunction implements BuiltinFunction {
        public String name() { return "LENGTH"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return (double) toStr(args.get(0)).length();
        }
    }

    public static class ConcatFunction implements BuiltinFunction {
        public String name() { return "CONCAT"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 1);
            StringBuilder sb = new StringBuilder();
            for (Object arg : args) sb.append(arg == null ? "" : arg.toString());
            return sb.toString();
        }
    }

    public static class SubstrFunction implements BuiltinFunction {
        public String name() { return "SUBSTR"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 2);
            String s = toStr(args.get(0));
            int start = (int) toNum(args.get(1));
            if (args.size() > 2) {
                int length = (int) toNum(args.get(2));
                return s.substring(start, Math.min(start + length, s.length()));
            }
            return s.substring(start);
        }
    }

    public static class ReplaceFunction implements BuiltinFunction {
        public String name() { return "REPLACE"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 3);
            return toStr(args.get(0)).replace(toStr(args.get(1)), toStr(args.get(2)));
        }
    }

    // ── Date ──────────────────────────────────────────────────────────────────

    public static class TodayFunction implements BuiltinFunction {
        public String name() { return "TODAY"; }
        public Object invoke(List<Object> args) {
            return LocalDate.now().toString();
        }
    }

    public static class DateFunction implements BuiltinFunction {
        public String name() { return "DATE"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return parseDate(toStr(args.get(0))).toString();
        }
    }

    public static class DatediffFunction implements BuiltinFunction {
        private static final String DEFAULT_UNIT = "DAYS";
        public String name() { return "DATEDIFF"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 2);
            LocalDate d1 = parseDate(toStr(args.get(0)));
            LocalDate d2 = parseDate(toStr(args.get(1)));
            String unit = args.size() > 2 ? toStr(args.get(2)).toUpperCase() : DEFAULT_UNIT;
            return switch (unit) {
                case "DAYS"   -> (double) ChronoUnit.DAYS.between(d1, d2);
                case "MONTHS" -> (double) ChronoUnit.MONTHS.between(d1, d2);
                case "YEARS"  -> (double) ChronoUnit.YEARS.between(d1, d2);
                default -> throw new EvaluationException("DATEDIFF: unknown unit '" + unit + "'. Use DAYS, MONTHS, or YEARS");
            };
        }
    }

    public static class DateAddFunction implements BuiltinFunction {
        public String name() { return "DATEADD"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 3);
            LocalDate date = parseDate(toStr(args.get(0)));
            long amount = (long) toNum(args.get(1));
            String unit = toStr(args.get(2)).toUpperCase();
            return switch (unit) {
                case "DAYS"   -> date.plusDays(amount).toString();
                case "MONTHS" -> date.plusMonths(amount).toString();
                case "YEARS"  -> date.plusYears(amount).toString();
                default -> throw new EvaluationException("DATEADD: unknown unit '" + unit + "'");
            };
        }
    }

    // ── Logic ─────────────────────────────────────────────────────────────────

    public static class IfFunction implements BuiltinFunction {
        public String name() { return "IF"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 3);
            return toBool(args.get(0)) ? args.get(1) : args.get(2);
        }
    }

    public static class CoalesceFunction implements BuiltinFunction {
        public String name() { return "COALESCE"; }
        public Object invoke(List<Object> args) {
            requireMin(name(), args, 1);
            for (Object arg : args) {
                if (arg != null) return arg;
            }
            return null;
        }
    }

    public static class NullIfFunction implements BuiltinFunction {
        public String name() { return "NULLIF"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 2);
            Object a = args.get(0), b = args.get(1);
            return (a != null && a.equals(b)) ? null : a;
        }
    }

    // ── Type conversion ───────────────────────────────────────────────────────

    public static class ToNumberFunction implements BuiltinFunction {
        public String name() { return "TO_NUMBER"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return toNum(args.get(0));
        }
    }

    public static class ToStringFunction implements BuiltinFunction {
        public String name() { return "TO_STRING"; }
        public Object invoke(List<Object> args) {
            requireArgs(name(), args, 1);
            return args.get(0) == null ? null : args.get(0).toString();
        }
    }

    // ── Shared helpers ────────────────────────────────────────────────────────

    static double toNum(Object v) {
        if (v instanceof Number n) return n.doubleValue();
        if (v instanceof String s) {
            try { return Double.parseDouble(s); }
            catch (NumberFormatException e) {
                throw new EvaluationException("Cannot convert '" + s + "' to a number");
            }
        }
        throw new EvaluationException("Expected a number but got: " + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    static String toStr(Object v) {
        if (v == null) throw new EvaluationException("Expected a string but got null");
        return v.toString();
    }

    static boolean toBool(Object v) {
        if (v instanceof Boolean b) return b;
        throw new EvaluationException("Expected a boolean but got: " + (v == null ? "null" : v.getClass().getSimpleName()));
    }

    static LocalDate parseDate(String s) {
        try {
            return LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (Exception e) {
            throw new EvaluationException("Cannot parse date '" + s + "'. Expected format: yyyy-MM-dd");
        }
    }

    private static void requireArgs(String name, List<Object> args, int count) {
        if (args.size() != count)
            throw new EvaluationException(name + "() requires exactly " + count + " argument(s), got " + args.size());
    }

    private static void requireMin(String name, List<Object> args, int min) {
        if (args.size() < min)
            throw new EvaluationException(name + "() requires at least " + min + " argument(s), got " + args.size());
    }
}
