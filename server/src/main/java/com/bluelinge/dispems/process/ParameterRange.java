package com.bluelinge.dispems.process;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class ParameterRange {
  private static final String DECIMAL = "[+-]?(?:\\d+(?:\\.\\d+)?|\\.\\d+)";
  private static final Pattern DECIMAL_PATTERN = Pattern.compile("^" + DECIMAL + "$");
  private static final String FORMAT_ERROR = "请输入四段逗号格式，例如 (>30),(=30),(=70),(<70)，空边界填写 ()";

  final String normalized;

  private ParameterRange(Bound lower, Bound upper) {
    if (lower == null && upper == null) throw invalid("至少填写一个下限 (>x) 或上限 (<y)");
    if (lower != null && upper != null) {
      int comparison = lower.value.compareTo(upper.value);
      if (comparison > 0) throw invalid("下限不能大于上限");
      if (comparison == 0 && (!lower.inclusive || !upper.inclusive)) {
        throw invalid("上下限相等时必须同时包含两端，否则区间为空");
      }
    }
    normalized = (lower == null ? "()" : "(>" + canonical(lower.value) + ")") + ","
        + (lower != null && lower.inclusive ? "(=" + canonical(lower.value) + ")" : "()") + ","
        + (upper != null && upper.inclusive ? "(=" + canonical(upper.value) + ")" : "()") + ","
        + (upper == null ? "()" : "(<" + canonical(upper.value) + ")");
  }

  static ParameterRange parse(String raw) {
    String value = raw == null ? "" : raw.trim();
    if (value.isEmpty()) throw invalid("范围值不能为空；" + FORMAT_ERROR);
    if (value.contains("\n") || value.contains("\r")) {
      String[] lines = value.split("\\r?\\n", -1);
      if (lines.length != 2 || !DECIMAL_PATTERN.matcher(lines[0].trim()).matches()
          || !DECIMAL_PATTERN.matcher(lines[1].trim()).matches()) {
        throw invalid("旧格式须为两行数值（第一行下限、第二行上限）；" + FORMAT_ERROR);
      }
      return new ParameterRange(new Bound(decimal(lines[0].trim()), true), new Bound(decimal(lines[1].trim()), true));
    }
    String[] parts = value.replaceAll("[\\s\\p{Z}]", "").split(",", -1);
    if (parts.length != 4) throw invalid(FORMAT_ERROR);
    BigDecimal lowerExclusive = clause(parts[0], ">", 1), lowerInclusive = clause(parts[1], "=", 2);
    BigDecimal upperInclusive = clause(parts[2], "=", 3), upperExclusive = clause(parts[3], "<", 4);
    if (lowerInclusive != null && lowerExclusive == null) throw invalid("下限包含段 (=x) 必须配合同值的下限排除段 (>x)");
    if (lowerInclusive != null && lowerExclusive.compareTo(lowerInclusive) != 0) throw invalid("下限两段数值必须相同");
    if (upperInclusive != null && upperExclusive == null) throw invalid("上限包含段 (=y) 必须配合同值的上限排除段 (<y)");
    if (upperInclusive != null && upperExclusive.compareTo(upperInclusive) != 0) throw invalid("上限两段数值必须相同");
    return new ParameterRange(
        lowerExclusive == null ? null : new Bound(lowerExclusive, lowerInclusive != null),
        upperExclusive == null ? null : new Bound(upperExclusive, upperInclusive != null));
  }

  private static BigDecimal clause(String text, String operator, int index) {
    if (text.equals("()")) return null;
    Matcher matcher = Pattern.compile("^\\(" + Pattern.quote(operator) + "(" + DECIMAL + ")\\)$").matcher(text);
    if (!matcher.matches()) throw invalid("第 " + index + " 段应填写 (" + operator + "数值) 或 ()，数值仅支持整数、负数和小数");
    return decimal(matcher.group(1));
  }

  private static BigDecimal decimal(String value) {
    try { return new BigDecimal(value); }
    catch (NumberFormatException exception) { throw invalid("数值仅支持整数、负数和小数"); }
  }

  private static String canonical(BigDecimal value) {
    BigDecimal normalized = value.stripTrailingZeros();
    return normalized.signum() == 0 ? "0" : normalized.toPlainString();
  }

  private static IllegalArgumentException invalid(String message) {
    return new IllegalArgumentException(message);
  }

  private static final class Bound {
    final BigDecimal value;
    final boolean inclusive;
    Bound(BigDecimal value, boolean inclusive) { this.value = value; this.inclusive = inclusive; }
  }
}
