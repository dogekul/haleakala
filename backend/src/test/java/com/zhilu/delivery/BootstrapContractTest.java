package com.zhilu.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;

class BootstrapContractTest {

  @Test
  void applicationClassIsSpringBootEntryPoint() throws Exception {
    Class<?> application = load("com.zhilu.delivery.DeliveryApplication");

    assertTrue(application.isAnnotationPresent(SpringBootApplication.class));
    assertNotNull(application.getMethod("main", String[].class));
  }

  @Test
  void apiErrorExposesStableShape() throws Exception {
    Class<?> type = load("com.zhilu.delivery.common.api.ApiError");
    Constructor<?> constructor = type.getConstructor(
        String.class, String.class, String.class, java.util.Map.class);
    Object error = constructor.newInstance(
        "VALIDATION_ERROR", "请求参数不正确", "trace-1", Collections.emptyMap());

    Method getCode = type.getMethod("getCode");
    Method getTraceId = type.getMethod("getTraceId");
    assertEquals("VALIDATION_ERROR", getCode.invoke(error));
    assertEquals("trace-1", getTraceId.invoke(error));
  }

  private Class<?> load(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException error) {
      fail("Expected production type to exist: " + className);
      return null;
    }
  }
}
