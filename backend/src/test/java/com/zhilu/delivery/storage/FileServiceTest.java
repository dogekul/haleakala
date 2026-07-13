package com.zhilu.delivery.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:files;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.session.store-type=none",
    "delivery.storage.max-file-size=16"
})
@AutoConfigureMockMvc
class FileServiceTest {
  @Autowired private JdbcTemplate jdbc;
  @Autowired private FileService files;
  @Autowired private MockMvc mvc;
  @MockBean private ObjectStorage objects;

  @BeforeEach
  void seedActor() throws Exception {
    jdbc.execute("SET REFERENTIAL_INTEGRITY FALSE");
    for (String table : new String[] {"audit_log", "training_material", "knowledge_item",
        "project_artifact", "project_member", "delivery_project", "product_version", "product",
        "file_version", "file_object", "app_user", "organization"}) {
      jdbc.update("delete from " + table);
    }
    jdbc.execute("SET REFERENTIAL_INTEGRITY TRUE");
    jdbc.update("insert into organization(id,name,code) values "
        + "(500,'智鹿科技','ZHILU-FILE'),(501,'其他组织','OTHER-FILE')");
    jdbc.update("insert into app_user(id,organization_id,username,display_name,status) values "
        + "(500,500,'file-user','文件用户','ACTIVE'),"
        + "(502,500,'outsider','非项目成员','ACTIVE'),"
        + "(503,500,'pmo','PMO','ACTIVE'),"
        + "(501,501,'other','其他组织用户','ACTIVE')");
    jdbc.update("insert into product(id,code,name,status) values (500,'FILE-PRODUCT','文件产品','ACTIVE')");
    jdbc.update("insert into product_version(id,product_id,version_name,status) "
        + "values (500,500,'V1','ACTIVE')");
    jdbc.update("insert into delivery_project(id,organization_id,code,name,customer_name,product_id,"
        + "product_version_id,manager_user_id,created_by) values "
        + "(500,500,'FILE-PROJECT','文件项目','客户',500,500,500,500)");
    jdbc.update("insert into project_member(project_id,user_id,project_role) "
        + "values (500,500,'DELIVERY_MANAGER')");
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

  @Test
  void downloadAndVersionAreDeniedAcrossOrganizationBoundary() throws Exception {
    FileObjectView stored = store("tenant.txt");

    assertThrows(NotFoundException.class, () ->
        files.signedDownload(stored.getId(), Duration.ofMinutes(10), otherOrganization()));
    assertThrows(NotFoundException.class, () -> files.addVersion(stored.getId(),
        bytes("changed"), "text/plain", 7, otherOrganization()));
    verify(objects, never()).signedDownload(anyString(), org.mockito.ArgumentMatchers.any(Duration.class));

    mvc.perform(get("/api/v1/files/{id}/download", stored.getId())
            .with(actor(otherOrganization())))
        .andExpect(status().isNotFound());
  }

  @Test
  void projectArtifactRequiresMembershipForReadAndWrite() throws Exception {
    FileObjectView stored = linkedArtifact();

    assertThrows(NotFoundException.class, () ->
        files.signedDownload(stored.getId(), Duration.ofMinutes(10), outsider()));
    assertThrows(NotFoundException.class, () -> files.addVersion(stored.getId(),
        bytes("changed"), "text/plain", 7, outsider()));

    mvc.perform(get("/api/v1/files/{id}/download", stored.getId())
            .with(actor(outsider())))
        .andExpect(status().isNotFound());
    MockMultipartFile version = new MockMultipartFile(
        "file", "artifact.txt", "text/plain", "changed".getBytes("UTF-8"));
    mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .multipart("/api/v1/files/{id}/versions", stored.getId()).file(version)
            .with(actor(outsider())).with(csrf()))
        .andExpect(status().isNotFound());

    mvc.perform(get("/api/v1/files/{id}/download", stored.getId())
            .with(actor(member())))
        .andExpect(status().isFound())
        .andExpect(header().string("Location", "http://minio.local/signed"));
  }

  @Test
  void discoverableKnowledgeFileRemainsReadableOutsideProjectMembership() {
    FileObjectView stored = linkedArtifact();
    jdbc.update("insert into knowledge_item(id,organization_id,type,title,summary,content_text,"
        + "visibility,status,owner_user_id) values "
        + "(500,500,'TRAINING','培训','摘要','内容','ORGANIZATION','PUBLISHED',500)");
    jdbc.update("insert into training_material(knowledge_item_id,audience,duration_minutes,file_object_id) "
        + "values (500,'全员',30,?)", stored.getId());

    assertEquals(URI.create("http://minio.local/signed"),
        files.signedDownload(stored.getId(), Duration.ofMinutes(10), outsider()));
  }

  @Test
  void pmoCanReadProjectArtifactAcrossProjectsInsideOrganization() {
    FileObjectView stored = linkedArtifact();

    assertEquals(URI.create("http://minio.local/signed"),
        files.signedDownload(stored.getId(), Duration.ofMinutes(10), pmo()));
  }

  @Test
  void versionAndDownloadRecordAuditEvents() {
    FileObjectView stored = store("audited.txt");

    files.addVersion(stored.getId(), bytes("changed"), "text/plain", 7, member());
    files.signedDownload(stored.getId(), Duration.ofMinutes(10), member());

    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='FILE_VERSION_ADDED' and resource_id=?",
        Integer.class, String.valueOf(stored.getId())));
    assertEquals(Integer.valueOf(1), jdbc.queryForObject(
        "select count(*) from audit_log where action='FILE_DOWNLOADED' and resource_id=?",
        Integer.class, String.valueOf(stored.getId())));
  }

  @Test
  void serviceWriteMethodsAlsoRequireFileWritePermission() {
    FileObjectView stored = store("permission.txt");
    CurrentUser noWrite = new CurrentUser(500L, 500L, "file-user", "文件用户",
        Collections.singletonList("DELIVERY_MANAGER"), Collections.<String>emptyList());

    assertThrows(AccessDeniedException.class, () -> files.store(bytes("new"), "new.txt",
        "text/plain", 3, noWrite));
    assertThrows(AccessDeniedException.class, () -> files.addVersion(stored.getId(),
        bytes("changed"), "text/plain", 7, noWrite));
  }

  @Test
  void trustedStoreStillRejectsActorFromAnotherOrganization() {
    assertThrows(NotFoundException.class, () -> files.store(bytes("cross-tenant"),
        "cross-tenant.txt", "text/plain", 12, 500, 501));

    verify(objects, never()).put(org.mockito.ArgumentMatchers.anyString(),
        org.mockito.ArgumentMatchers.any(InputStream.class),
        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void failedStoreDeletesObjectWrittenBeforeDatabaseRollback() {
    String oversizedMimeType = String.join("", Collections.nCopies(20, "text/plain;"));
    assertThrows(DataIntegrityViolationException.class, () -> files.store(bytes("orphan"),
        "orphan.txt", oversizedMimeType, 6, 500, 500));

    verify(objects).delete(org.mockito.ArgumentMatchers.startsWith("organization/500/"));
  }

  @Test
  void failedVersionDeletesObjectWrittenBeforeDatabaseRollback() {
    FileObjectView stored = store("version-orphan.txt");
    clearInvocations(objects);
    CurrentUser missingActor = new CurrentUser(999L, 500L, "missing", "Missing",
        Collections.singletonList("DELIVERY_MANAGER"), Collections.singletonList("file:write"));

    assertThrows(DataIntegrityViolationException.class, () -> files.addVersion(stored.getId(),
        bytes("orphan"), "text/plain", 6, missingActor));

    verify(objects).delete(org.mockito.ArgumentMatchers.startsWith("organization/500/"));
  }

  private FileObjectView linkedArtifact() {
    FileObjectView stored = store("artifact.txt");
    jdbc.update("insert into project_artifact(project_id,file_id,artifact_type,name) "
        + "values (500,?,'DELIVERABLE','artifact')", stored.getId());
    return stored;
  }

  private FileObjectView store(String name) {
    byte[] content = "content".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    return files.store(new ByteArrayInputStream(content), name, "text/plain", content.length,
        500, 500);
  }

  private ByteArrayInputStream bytes(String value) {
    return new ByteArrayInputStream(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private CurrentUser member() {
    return user(500, 500, "DELIVERY_MANAGER", "file:write");
  }

  private CurrentUser outsider() {
    return user(502, 500, "DELIVERY_ENGINEER", "file:write");
  }

  private CurrentUser pmo() {
    return user(503, 500, "PMO", "file:write");
  }

  private CurrentUser otherOrganization() {
    return user(501, 501, "DELIVERY_ENGINEER", "file:write");
  }

  private CurrentUser user(long id, long organizationId, String role, String permission) {
    return new CurrentUser(id, organizationId, "user-" + id, "User " + id,
        Arrays.asList(role), Arrays.asList(permission));
  }

  private RequestPostProcessor actor(CurrentUser user) {
    return authentication(new UsernamePasswordAuthenticationToken(user, null,
        Collections.singletonList(new SimpleGrantedAuthority("file:write"))));
  }
}
