package com.zhilu.delivery.common.security;

import java.security.SecureRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.codec.Hex;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.stereotype.Service;

@Service
public class SettingSecretCipher {
  private static final SecureRandom RANDOM = new SecureRandom();
  private final String masterKey;

  public SettingSecretCipher(
      @Value("${delivery.settings.encryption-key}") String masterKey) {
    if (masterKey == null || masterKey.trim().isEmpty()) {
      throw new IllegalArgumentException("系统设置加密主密钥不能为空");
    }
    this.masterKey = masterKey;
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.isEmpty()) {
      throw new IllegalArgumentException("待加密内容不能为空");
    }
    byte[] salt = new byte[16];
    RANDOM.nextBytes(salt);
    String saltHex = new String(Hex.encode(salt));
    String encrypted = Encryptors.delux(masterKey, saltHex).encrypt(plaintext);
    return "v1:" + saltHex + ":" + encrypted;
  }

  public String decrypt(String storedValue) {
    try {
      String[] parts = storedValue == null
          ? new String[0] : storedValue.split(":", 3);
      if (parts.length != 3 || !"v1".equals(parts[0]) || parts[1].length() != 32) {
        throw new IllegalArgumentException("invalid encrypted setting");
      }
      return Encryptors.delux(masterKey, parts[1]).decrypt(parts[2]);
    } catch (RuntimeException failure) {
      throw new IllegalStateException(
          "系统设置密文无法解密，请检查 SETTINGS_ENCRYPTION_KEY", failure);
    }
  }
}
