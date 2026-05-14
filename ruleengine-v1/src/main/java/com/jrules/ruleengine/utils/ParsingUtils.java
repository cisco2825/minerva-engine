package com.jrules.ruleengine.utils;

import com.jrules.ruleengine.enums.DataType;

import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.LocalDate;

/**
 * Utility class providing helper methods to validate whether a given
 * input object can be parsed into a specific {@link DataType}.
 */
public final class ParsingUtils {

  /**
   * Checks whether the given raw object can be parsed as the specified {@link DataType}.
   *
   * <p>
   * The method performs a safe parse attempt based on the data type:
   * <ul>
   *     <li><b>NUMBER</b>: Attempts to parse the value using {@link Double#parseDouble(String)}</li>
   *     <li><b>DATE</b>: Attempts to parse the value using {@link DateTimeFormatter#ISO_LOCAL_DATE}</li>
   *     <li><b>TEXT</b>: Always returns {@code true}</li>
   * </ul>
   * </p>
   *
   * <p>
   * Leading and trailing whitespace is trimmed before parsing.
   * </p>
   *
   * @param rawObject the input object to validate; may be {@code null}
   * @param dt        the expected {@link DataType} to parse the object as
   * @return {@code true} if the object can be successfully parsed as the given data type,
   *         {@code false} otherwise
   */
  public static boolean isParsableAs(Object rawObject, DataType dt) {
    if (rawObject == null) return false;

    String raw = rawObject.toString();
    raw = raw.trim();
    try {
      switch (dt) {
        case NUMBER:
          Double.parseDouble(raw);
          return true;

        case DATE:
          try {
            LocalDate.parse(raw, DateTimeFormatter.ISO_LOCAL_DATE);
            return true;
          } catch (DateTimeParseException e) {
            return false;
          }

        case TEXT:
          return true;

        default:
          return false;
      }
    } catch (Exception e) {
      return false;
    }
  }
}