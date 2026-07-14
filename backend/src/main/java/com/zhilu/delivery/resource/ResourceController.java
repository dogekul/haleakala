package com.zhilu.delivery.resource;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/v1/resources")
public class ResourceController {
  private final ResourceService resources;private final AuditService audit;
  public ResourceController(ResourceService resources,AuditService audit){this.resources=resources;this.audit=audit;}
  @GetMapping("/team") public List<Map<String,Object>> team(@RequestParam(required=false)String keyword,@AuthenticationPrincipal CurrentUser user){return resources.team(user,keyword);}
  @PutMapping("/team/{userId}/profile") public Map<String,Object> profile(@PathVariable long userId,@Valid @RequestBody ProfileRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=resources.saveProfile(userId,user,request.jobTitle,request.location,request.weeklyCapacityHours,request.resourceStatus);record(user,"UPDATE_PROFILE",String.valueOf(userId));return value;}
  @PostMapping("/team/{userId}/skills") public Map<String,Object> skill(@PathVariable long userId,@Valid @RequestBody EngineerSkillRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=resources.saveSkill(userId,request.skillId,request.proficiency,request.certified,request.experienceMonths,user);record(user,"UPDATE_SKILL",userId+":"+request.skillId);return value;}
  @GetMapping("/skills") public List<Map<String,Object>> skills(@AuthenticationPrincipal CurrentUser user){return resources.skillCatalog(user);}
  @PostMapping("/skills") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> createSkill(@Valid @RequestBody SkillRequest request,@AuthenticationPrincipal CurrentUser user){return resources.createSkill(user,request.code,request.name,request.category);}
  @GetMapping("/assignments") public List<Map<String,Object>> assignments(@RequestParam(required=false)Long projectId,@RequestParam(required=false)Long userId,@AuthenticationPrincipal CurrentUser user){return resources.assignments(user,projectId,userId);}
  @PostMapping("/assignments") @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> assign(@Valid @RequestBody AssignmentRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=resources.assign(user,request.userId,request.projectId,request.role,request.startDate,request.endDate,request.allocationPercent);record(user,"ASSIGN",String.valueOf(value.get("id")));return value;}
  @PutMapping("/assignments/{id}") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody AssignmentRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=resources.updateAssignment(id,user,request.userId,request.projectId,request.role,request.startDate,request.endDate,request.allocationPercent,request.status,request.version);record(user,"UPDATE_ASSIGNMENT",String.valueOf(id));return value;}
  @GetMapping("/load") public List<Map<String,Object>> load(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@AuthenticationPrincipal CurrentUser user){return resources.load(user,from,to);}
  @GetMapping("/conflicts") public List<Map<String,Object>> conflicts(@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate from,@RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate to,@AuthenticationPrincipal CurrentUser user){return resources.conflicts(user,from,to);}
  private void record(CurrentUser user,String action,String id){audit.record(user.getOrganizationId(),user.getId(),action,"RESOURCE",id,null);}
  public static final class ProfileRequest{@NotBlank public String jobTitle;public String location;public int weeklyCapacityHours=40;public String resourceStatus="ACTIVE";}
  public static final class EngineerSkillRequest{@NotNull public Long skillId;public int proficiency;public boolean certified;public int experienceMonths;}
  public static final class SkillRequest{@NotBlank public String code;@NotBlank public String name;@NotBlank public String category;}
  public static final class AssignmentRequest{@NotNull public Long userId;@NotNull public Long projectId;@NotBlank public String role;@NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)public LocalDate startDate;@NotNull @DateTimeFormat(iso=DateTimeFormat.ISO.DATE)public LocalDate endDate;public int allocationPercent;public String status="ACTIVE";public long version;}
}
