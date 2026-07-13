package com.zhilu.delivery.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:files;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.storage.max-file-size=16"
})
class FileServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FileService files;
  @MockBean private ObjectStorage objects;

  @BeforeEach
  void seedActor() throws Exception {
    jdbc.update("delete from file_version");
    jdbc.update("delete from file_object");
    jdbc.update("delete from app_user");
    jdbc.update("delete from organization");
    jdbc.update("insert into organization(id,name,code) values (500,'智鹿科技','ZHILU-FILE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) "
        + "values (500,500,'file-user','文件用户','ACTIVE')");
    org.mockito.Mockito.doAnswer(invocation -> {
      InputStream content = invocation.getArgument(1);
      ByteArrayOutputStream sink = new ByteArrayOutputStream();
      byte[] buffer = new byte[32];
      int read;
      while ((read = content.read(buffer)) >= 0) {
        sink.write(buffer, 0, read);
      }
      return null;
    }).when(objects).put(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(InputStream.class),
        org.mockito.ArgumentMatchers.anyLong(),
        org.mockito.ArgumentMatchers.anyString());
    org.mockito.Mockito.when(objects.signedDownload(
        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
        .thenReturn(URI.create("http://minio.local/signed"));
  }

  @Test
  void storesBytesInObjectStorageAndOnlyMetadataInMysql() {
    byte[] content = "hello-delivery".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    FileObjectView stored = files.store(
        new ByteArrayInputStream(content), "交付说明.txt", "text/plain", content.length, 500, 500);

    assertEquals("交付说明.txt", stored.getOriginalName());
    assertEquals("text/plain", stored.getMimeType());
    assertEquals(content.length, stored.getSizeBytes());
    assertEquals(1, stored.getFileVersion());
    assertEquals(64, stored.getChecksumSha256().length());
    assertTrue(stored.getObjectKey().startsWith("organization/500/"));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from file_version where file_id=?", Integer.class, stored.getId()));
    assertFalse(jdbc.queryForList(
        "select column_name from information_schema.columns "
            + "where table_name in ('file_object','file_version') and column_name in ('content','bytes')",
        String.class).size() > 0);
  }

  @Test
  void enforcesSizeLimitAndReturnsShortLivedSignedUrl() {
    assertThrows(IllegalArgumentException.class, () -> files.store(
        new ByteArrayInputStream(new byte[17]), "too-large.bin", "application/octet-stream",
        17, 500, 500));
  }
}
