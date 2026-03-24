package com.example.gitsqlrunner.interfaces.web;

import java.util.LinkedHashMap;
import java.util.Map;

public final class ApiResponseMaps {
  private ApiResponseMaps() {
  }

  public static Map<String, Object> ok() {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("ok", true);
    return result;
  }

  public static Map<String, Object> error(String message) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("ok", false);
    result.put("message", message);
    return result;
  }

  public static Map<String, Object> single(String key, Object value) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(key, value);
    return result;
  }

  public static Map<String, Object> pair(String firstKey, Object firstValue, String secondKey, Object secondValue) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put(firstKey, firstValue);
    result.put(secondKey, secondValue);
    return result;
  }
}
