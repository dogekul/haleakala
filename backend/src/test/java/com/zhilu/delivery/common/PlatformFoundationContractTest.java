package com.zhilu.delivery.common;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Modifier;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class PlatformFoundationContractTest {

  @Test
  void validationErrorsUseStableJsonShape() throws Exception {
    Object advice = load("com.zhilu.delivery.common.error.GlobalExceptionHandler")
        .getConstructor()
        .newInstance();
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new ValidationController())
        .setControllerAdvice(advice)
        .build();

    mvc.perform(post("/test-validation")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.message").value("请求参数不正确"))
        .andExpect(jsonPath("$.traceId").isNotEmpty())
        .andExpect(jsonPath("$.fieldErrors.name").value("名称不能为空"));
  }

  @Test
  void baseEntityDefinesAuditAndOptimisticLockFields() throws Exception {
    Class<?> type = load("com.zhilu.delivery.common.model.BaseEntity");

    assertTrue(Modifier.isAbstract(type.getModifiers()));
    assertNotNull(type.getDeclaredField("id"));
    assertNotNull(type.getDeclaredField("createdAt"));
    assertNotNull(type.getDeclaredField("updatedAt"));
    assertNotNull(type.getDeclaredField("version"));
  }

  private Class<?> load(String className) {
    try {
      return Class.forName(className);
    } catch (ClassNotFoundException error) {
      fail("Expected production type to exist: " + className);
      return null;
    }
  }

  @RestController
  static class ValidationController {
    @PostMapping("/test-validation")
    String validate(@Valid @RequestBody ValidationPayload payload) {
      return payload.name;
    }
  }

  static class ValidationPayload {
    @NotBlank(message = "名称不能为空")
    public String name;
  }
}
