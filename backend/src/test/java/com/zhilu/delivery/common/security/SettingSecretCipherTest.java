package com.zhilu.delivery.common.security;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SettingSecretCipherTest {
  private final SettingSecretCipher cipher =
      new SettingSecretCipher("test-settings-master-key-2026");

  @Test
  void encryptsWithRandomSaltAndDecryptsTheOriginalValue() {
    String first = cipher.encrypt("ol_api_secret");
    String second = cipher.encrypt("ol_api_secret");

    assertTrue(first.startsWith("v1:"));
    assertFalse(first.contains("ol_api_secret"));
    assertNotEquals(first, second);
    org.junit.jupiter.api.Assertions.assertEquals(
        "ol_api_secret", cipher.decrypt(first));
    org.junit.jupiter.api.Assertions.assertEquals(
        "ol_api_secret", cipher.decrypt(second));
  }

  @Test
  void rejectsBlankMasterKeysAndInvalidCiphertext() {
    assertThrows(IllegalArgumentException.class, () -> new SettingSecretCipher(" "));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("plain-token"));
    assertThrows(IllegalStateException.class, () -> cipher.decrypt("v1:00:broken"));
  }
}
