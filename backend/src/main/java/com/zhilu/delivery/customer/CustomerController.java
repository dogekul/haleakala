package com.zhilu.delivery.customer;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;
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
@RequestMapping("/api/v1/customers")
public class CustomerController {
  private final CustomerService customers;
  private final AuditService audit;

  public CustomerController(CustomerService customers, AuditService audit) {
    this.customers = customers;
    this.audit = audit;
  }

  @GetMapping
  public List<Map<String, Object>> customers(
      @RequestParam(required = false) String keyword,
      @RequestParam(required = false) String status,
      @AuthenticationPrincipal CurrentUser user) {
    return customers.list(user.getOrganizationId(), keyword, status);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @Transactional
  public Map<String, Object> create(@Valid @RequestBody CustomerRequest request,
      @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = customers.create(user.getOrganizationId(), request.name,
        request.shortName, request.contactName, request.phone, request.email, request.address,
        request.status, request.remark);
    record(user, "CREATE", value.get("id"), request.name);
    return value;
  }

  @PutMapping("/{id}")
  @Transactional
  public Map<String, Object> update(@PathVariable long id,
      @Valid @RequestBody CustomerRequest request, @AuthenticationPrincipal CurrentUser user) {
    Map<String, Object> value = customers.update(user.getOrganizationId(), id, request.name,
        request.shortName, request.contactName, request.phone, request.email, request.address,
        request.status, request.remark, request.version);
    record(user, "UPDATE", id, request.name + " · " + request.status);
    return value;
  }

  private void record(CurrentUser user, String action, Object id, String details) {
    audit.record(user.getOrganizationId(), user.getId(), action, "CUSTOMER",
        String.valueOf(id), details);
  }

  public static final class CustomerRequest {
    @NotBlank @Size(max = 180) public String name;
    @Size(max = 100) public String shortName;
    @Size(max = 100) public String contactName;
    @Size(max = 40) public String phone;
    @Email @Size(max = 160) public String email;
    @Size(max = 500) public String address;
    @NotBlank public String status = "ACTIVE";
    public String remark;
    public long version;
  }
}
