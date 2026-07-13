package com.zhilu.delivery.storage;

import com.zhilu.delivery.iam.service.CurrentUser;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
  private final FileService files;

  public FileController(FileService files) {
    this.files = files;
  }

  @PostMapping
  public ResponseEntity<FileObjectView> upload(
      @RequestParam("file") MultipartFile upload,
      @AuthenticationPrincipal CurrentUser user) throws IOException {
    FileObjectView stored = files.store(upload.getInputStream(), upload.getOriginalFilename(),
        upload.getContentType(), upload.getSize(), user.getOrganizationId(), user.getId());
    return ResponseEntity.status(HttpStatus.CREATED).body(stored);
  }

  @PostMapping("/{id}/versions")
  public FileObjectView addVersion(
      @PathVariable long id,
      @RequestParam("file") MultipartFile upload,
      @AuthenticationPrincipal CurrentUser user) throws IOException {
    return files.addVersion(id, upload.getInputStream(), upload.getContentType(),
        upload.getSize(), user.getId());
  }

  @GetMapping("/{id}/download")
  public ResponseEntity<Void> download(@PathVariable long id) {
    URI location = files.signedDownload(id, Duration.ofMinutes(10));
    return ResponseEntity.status(HttpStatus.FOUND)
        .header(HttpHeaders.LOCATION, location.toString()).build();
  }
}
