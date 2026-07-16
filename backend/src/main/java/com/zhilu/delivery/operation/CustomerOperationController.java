package com.zhilu.delivery.operation;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class CustomerOperationController {
  private final CustomerOperationService operations;
  private final AuditService audit;

  public CustomerOperationController(CustomerOperationService operations, AuditService audit) {
    this.operations = operations;
    this.audit = audit;
  }

  @GetMapping
  public List<Map<String, Object>> list(@RequestParam(required = false) String keyword,
      @RequestParam(required = false) Long customerId,
      @RequestParam(required = false) Long ownerUserId,
      @RequestParam(required = false) String stage,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal CurrentUser user) {
    return operations.list(user.getOrganizationId(), keyword, customerId, ownerUserId, stage, status);
  }

  @GetMapping("/{id}")
  public Map<String, Object> get(@PathVariable long id,
      @AuthenticationPrincipal CurrentUser user) {
    return operations.get(user.getOrganizationId(), id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody CustomerOperationService.Input input,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> result = operations.create(user.getOrganizationId(), user.getId(), input);
    record(user, "CREATE", result.get("id"), input.title);
    return result;
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable long id, @Valid @RequestBody UpdateRequest input,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> result = operations.update(
        user.getOrganizationId(), id, input.version.longValue(), input);
    record(user, "UPDATE", id, input.title);
    return result;
  }

  @PostMapping("/{id}/advance")
  @Transactional
  public Map<String, Object> advance(@PathVariable long id,
      @Valid @RequestBody AdvanceRequest input, @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> result = operations.advance(
        user.getOrganizationId(), id, input.version.longValue(), user.getId());
    record(user, "ADVANCE", id, String.valueOf(result.get("stage")));
    return result;
  }

  private void record(CurrentUser user, String action, Object id, String details) {
    audit.record(user.getOrganizationId(), user.getId(), action, "CUSTOMER_OPERATION",
        String.valueOf(id), details);
  }

  public static final class UpdateRequest extends CustomerOperationService.UpdateInput {
    @NotNull public Long version;
  }

  public static final class AdvanceRequest {
    @NotNull public Long version;
  }
}
