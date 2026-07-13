package com.zhilu.delivery.iam.api;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.IamAdminService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminIamController {
  private final IamAdminService admin;
  private final AuditService audit;

  public AdminIamController(IamAdminService admin, AuditService audit) {
    this.admin = admin;
    this.audit = audit;
  }

  @GetMapping("/users")
  public List<Map<String, Object>> users(@AuthenticationPrincipal CurrentUser user) {
    return admin.users(user.getOrganizationId());
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createUser(
      @Valid @RequestBody CreateUserRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> created = admin.createLocalUser(
        user.getOrganizationId(),
        request.primaryTeamId,
        request.username,
        request.password,
        request.displayName,
        request.email,
        request.roleCodes == null ? Collections.emptyList() : request.roleCodes);
    record(user, "USER_CREATED", "USER", created.get("id"), request.username);
    return created;
  }

  @PutMapping("/users/{id}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateUserStatus(
      @PathVariable long id,
      @Valid @RequestBody StatusRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    admin.updateUserStatus(user, id, request.status);
    record(user, "USER_STATUS_UPDATED", "USER", id, request.status);
  }

  @PutMapping("/users/{id}")
  public Map<String, Object> updateUser(
      @PathVariable long id,
      @Valid @RequestBody UpdateUserRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> updated = admin.updateUser(user, id, request.displayName, request.email,
        request.primaryTeamId, request.roleCodes, request.status);
    record(user, "USER_UPDATED", "USER", id, request.displayName);
    return updated;
  }

  @GetMapping("/teams")
  public List<Map<String, Object>> teams(@AuthenticationPrincipal CurrentUser user) {
    return admin.teams(user.getOrganizationId());
  }

  @PostMapping("/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createTeam(
      @Valid @RequestBody CreateTeamRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> created =
        admin.createTeam(user.getOrganizationId(), request.parentId, request.name, request.code);
    record(user, "TEAM_CREATED", "TEAM", created.get("id"), request.name);
    return created;
  }

  @PutMapping("/teams/{id}")
  public Map<String, Object> updateTeam(
      @PathVariable long id,
      @Valid @RequestBody UpdateTeamRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> updated = admin.updateTeam(user.getOrganizationId(), id, request.parentId,
        request.name, request.code, request.enabled);
    record(user, "TEAM_UPDATED", "TEAM", id, request.name);
    return updated;
  }

  @GetMapping("/roles")
  public List<Map<String, Object>> roles() {
    return admin.roles();
  }

  @GetMapping("/permissions")
  public List<Map<String, Object>> permissions() {
    return admin.permissions();
  }

  @PutMapping("/roles/{id}/permissions")
  public Map<String, Object> replacePermissions(
      @PathVariable long id, @Valid @RequestBody RolePermissionsRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> updated = admin.replacePermissions(id, request.permissionCodes);
    record(user, "ROLE_PERMISSIONS_UPDATED", "ROLE", id,
        String.join(",", request.permissionCodes));
    return updated;
  }

  private void record(CurrentUser user, String action, String type, Object id, String details) {
    audit.record(user.getOrganizationId(), user.getId(), action, type,
        String.valueOf(id), details);
  }

  public static final class CreateUserRequest {
    public Long primaryTeamId;
    @NotBlank public String username;
    @NotBlank @Size(min = 8, max = 72) public String password;
    @NotBlank public String displayName;
    public String email;
    public List<String> roleCodes;
  }

  public static final class UpdateUserRequest {
    public Long primaryTeamId;
    @NotBlank public String displayName;
    public String email;
    @NotBlank public String status;
    @NotNull public List<String> roleCodes;
  }

  public static final class CreateTeamRequest {
    public Long parentId;
    @NotBlank public String name;
    @NotBlank public String code;
  }

  public static final class UpdateTeamRequest {
    public Long parentId;
    @NotBlank public String name;
    @NotBlank public String code;
    @NotNull public Boolean enabled;
  }

  public static final class RolePermissionsRequest {
    @NotNull public List<String> permissionCodes;
  }

  public static final class StatusRequest {
    @NotBlank public String status;
  }
}
