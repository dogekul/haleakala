package com.zhilu.delivery.knowledge;

import com.zhilu.delivery.audit.AuditService;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
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

@RestController
@RequestMapping("/api/v1/knowledge")
public class KnowledgeController {
  private final KnowledgeService knowledge;
  private final AuditService audit;
  public KnowledgeController(KnowledgeService knowledge, AuditService audit) { this.knowledge=knowledge; this.audit=audit; }

  @GetMapping public List<Map<String,Object>> search(
      @RequestParam(required=false) String keyword,@RequestParam(required=false) String type,
      @RequestParam(required=false) String tag,@RequestParam(defaultValue="false") boolean publishedOnly,
      @AuthenticationPrincipal CurrentUser user){return knowledge.search(user,keyword,type,tag,publishedOnly);}
  @GetMapping("/{id}") public Map<String,Object> get(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){return knowledge.get(id,user);}
  @PostMapping @ResponseStatus(HttpStatus.CREATED) public Map<String,Object> create(@Valid @RequestBody KnowledgeRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=knowledge.create(user,request.type,request.title,request.summary,request.content,request.tags,request.productId,request.productVersionId,request.visibility,request.language,request.codeText,request.detailText(),request.durationMinutes,request.fileObjectId,request.stageCode,request.requirement,request.enabled);audit(user,"CREATE",value);return value;}
  @PutMapping("/{id}") public Map<String,Object> update(@PathVariable long id,@Valid @RequestBody KnowledgeRequest request,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=knowledge.update(id,user,request.type,request.title,request.summary,request.content,request.tags,request.productId,request.productVersionId,request.visibility,request.language,request.codeText,request.detailText(),request.durationMinutes,request.fileObjectId,request.version,request.documentRevision,request.stageCode,request.requirement,request.enabled);audit(user,"UPDATE",value);return value;}
  @PostMapping("/{id}/publish") public Map<String,Object> publish(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=knowledge.publish(id,user);audit(user,"PUBLISH",value);return value;}
  @PostMapping("/{id}/document/retry") public Map<String,Object> retryDocument(@PathVariable long id,@AuthenticationPrincipal CurrentUser user){Map<String,Object> value=knowledge.retryDocument(id,user);audit(user,"RETRY_DOCUMENT",value);return value;}
  private void audit(CurrentUser user,String action,Map<String,Object> value){audit.record(user.getOrganizationId(),user.getId(),action,"KNOWLEDGE_ITEM",String.valueOf(value.get("id")),String.valueOf(value.get("title")));}

  public static final class KnowledgeRequest {
    @NotBlank public String type; @NotBlank public String title; @NotBlank public String summary;
    public String content; public String tags; public Long productId; public Long productVersionId;
    public String visibility="ORGANIZATION"; public String language; public String codeText;
    public String usageNotes; public String audience; public Integer durationMinutes; public Long fileObjectId; public long version;
    public Long documentRevision; public String stageCode; public String requirement; public Boolean enabled;
    String detailText(){return "CODE".equals(type)?usageNotes:audience;}
  }
}
