package com.zhilu.delivery.iam.api;

import com.zhilu.delivery.iam.service.IamAdminService;
import com.zhilu.delivery.iam.service.IamService;
import com.zhilu.delivery.iam.service.UserSummary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.http.HttpStatus;
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
  private final IamService iam;
  private final IamAdminService admin;

  public AdminIamController(IamService iam, IamAdminService admin) {
    this.iam = iam;
    this.admin = admin;
  }

  @GetMapping("/users")
  public List<UserSummary> users() {
    return iam.listUsers();
  }

  @PostMapping("/users")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createUser(@Valid @RequestBody CreateUserRequest request) {
    return admin.createLocalUser(
        request.organizationId,
        request.primaryTeamId,
        request.username,
        request.password,
        request.displayName,
        request.email,
        request.roleCodes == null ? Collections.emptyList() : request.roleCodes);
  }

  @PutMapping("/users/{id}/status")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void updateUserStatus(@PathVariable long id, @Valid @RequestBody StatusRequest request) {
    admin.updateUserStatus(id, request.status);
  }

  @GetMapping("/teams")
  public List<Map<String, Object>> teams() {
    return admin.teams();
  }

  @PostMapping("/teams")
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> createTeam(@Valid @RequestBody CreateTeamRequest request) {
    return admin.createTeam(request.organizationId, request.parentId, request.name, request.code);
  }

  @GetMapping("/roles")
  public List<Map<String, Object>> roles() {
    return admin.roles();
  }

  @PutMapping("/roles/{id}/permissions")
  public Map<String, Object> replacePermissions(
      @PathVariable long id, @Valid @RequestBody RolePermissionsRequest request) {
    return admin.replacePermissions(id, request.permissionCodes);
  }

  public static final class CreateUserRequest {
    @NotNull public Long organizationId;
    public Long primaryTeamId;
    @NotBlank public String username;
    @NotBlank @Size(min = 8, max = 72) public String password;
    @NotBlank public String displayName;
    public String email;
    public List<String> roleCodes;
  }

  public static final class CreateTeamRequest {
    @NotNull public Long organizationId;
    public Long parentId;
    @NotBlank public String name;
    @NotBlank public String code;
  }

  public static final class RolePermissionsRequest {
    @NotNull public List<String> permissionCodes;
  }

  public static final class StatusRequest {
    @NotBlank public String status;
  }
}
