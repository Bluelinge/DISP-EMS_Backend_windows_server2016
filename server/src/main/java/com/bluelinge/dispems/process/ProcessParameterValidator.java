package com.bluelinge.dispems.process;

import com.bluelinge.dispems.common.AppException;
import java.util.List;
import java.util.Map;

final class ProcessParameterValidator {
  private ProcessParameterValidator() { }

  static void validateAndNormalize(List<Map<String, Object>> steps) {
    for (Map<String, Object> step : steps) {
      Object rawParameters = step.get("parameters");
      if (!(rawParameters instanceof List)) continue;
      int parameterIndex = 0;
      for (Object rawParameter : (List<?>) rawParameters) {
        parameterIndex++;
        if (!(rawParameter instanceof Map)) continue;
        Map<String, Object> parameter = (Map<String, Object>) rawParameter;
        if (number(parameter.get("type")) != 2) continue;
        String reference = parameter.get("referenceValue") == null ? "" : String.valueOf(parameter.get("referenceValue"));
        try {
          parameter.put("referenceValue", ParameterRange.parse(reference).normalized);
        } catch (IllegalArgumentException exception) {
          throw AppException.invalid("第 " + step.get("stepNo") + " 步参数 " + parameterIndex + "：" + exception.getMessage());
        }
      }
    }
  }

  private static int number(Object value) {
    if (value instanceof Number) return ((Number) value).intValue();
    try { return Integer.parseInt(String.valueOf(value)); } catch (Exception e) { return -1; }
  }
}
