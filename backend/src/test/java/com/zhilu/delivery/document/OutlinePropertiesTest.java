package com.zhilu.delivery.document;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:outline-properties;MODE=MySQL;"
        + "DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.outline.base-url=http://outline.test",
    "delivery.outline.api-token=ol_api_test",
    "delivery.outline.collection-id=a4296a54-2044-4529-ba86-d598a5322e06",
    "delivery.outline.connect-timeout=2s",
    "delivery.outline.read-timeout=7s"
})
class OutlinePropertiesTest {
  @Autowired private OutlineProperties properties;

  @Test
  void bindsOutlineConnectionAndRetrySettings() {
    assertEquals("http://outline.test", properties.getBaseUrl());
    assertEquals("ol_api_test", properties.getApiToken());
    assertEquals("a4296a54-2044-4529-ba86-d598a5322e06", properties.getCollectionId());
    assertEquals(Duration.ofSeconds(2), properties.getConnectTimeout());
    assertEquals(Duration.ofSeconds(7), properties.getReadTimeout());
    assertEquals(5, properties.getMaxAttempts());
    assertEquals(Duration.ofSeconds(30), properties.getInitialBackoff());
  }
}
