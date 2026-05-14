package com.jrules.ruleengine.utils;

import lombok.extern.slf4j.Slf4j;
import org.zalando.problem.Problem;
import org.zalando.problem.Status;

import java.util.Map;

@Slf4j
public class ContextResolver {

  public static Object resolve(Map<String, ? extends Object> data, String parameter) {
    if (data == null) {
      log.error("Context data is null for parameter: {}", parameter);
      throw Problem.valueOf(Status.BAD_REQUEST, "Context data is null for parameter: " + parameter);
    }

    if (parameter == null || parameter.trim().isEmpty()) {
      log.error("Parameter is null or empty");
      throw Problem.valueOf(
          Status.BAD_REQUEST, "Not a valid parameter in condition. Parameter is null or empty");
    }

    if(!data.containsKey(parameter)) {
      log.error("Parameter value not found in the context provided. Param: {}", parameter);
      throw Problem.valueOf(
          Status.BAD_REQUEST, "Parameter value not found in the context provided. Param: " + parameter);
    }
    return data.get(parameter);
  }
}
