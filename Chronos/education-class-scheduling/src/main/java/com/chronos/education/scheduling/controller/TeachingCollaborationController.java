package com.chronos.education.scheduling.controller;
import com.chronos.commons.model.ResultData;
import com.chronos.education.scheduling.model.dto.CollaborationDtos.*;
import com.chronos.education.scheduling.service.TeachingCollaborationService;
import jakarta.validation.Valid; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/education/teaching-center")
public class TeachingCollaborationController {
 private final TeachingCollaborationService service; public TeachingCollaborationController(TeachingCollaborationService s){service=s;}
 @PostMapping("/preparations") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> create(@Valid @RequestBody PreparationCreateRequest r,Authentication a){return ok(service.createPreparation(r,a));}
 @GetMapping("/preparations/{id}") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:view','education:teaching:manage')") public ResultData<?> get(@PathVariable String id,Authentication a){return ok(service.getPreparation(id,a));}
 @PostMapping("/preparations/{id}/members") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> invite(@PathVariable String id,@Valid @RequestBody MemberInviteRequest r,Authentication a){return ok(service.invite(id,r,a));}
 @PostMapping("/preparation-members/{id}/response") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> response(@PathVariable String id,@Valid @RequestBody MemberResponseRequest r,Authentication a){return ok(service.respond(id,r,a));}
 @PostMapping("/preparations/{id}/materials") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> material(@PathVariable String id,@Valid @RequestBody MaterialRequest r,Authentication a){return ok(service.addMaterial(id,r,a));}
 @PostMapping("/preparations/{id}/comments") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> comment(@PathVariable String id,@Valid @RequestBody CommentRequest r,Authentication a){return ok(service.comment(id,r,a));}
 @PostMapping("/preparations/{id}/conclusion") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:preparation:manage','education:teaching:manage')") public ResultData<?> conclusion(@PathVariable String id,@Valid @RequestBody ConclusionRequest r,Authentication a){return ok(service.conclude(id,r,a));}
 @PostMapping("/coursewares") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:material:manage','education:teaching:manage')") public ResultData<?> courseware(@Valid @RequestBody ResourceCreateRequest r,Authentication a){return ok(service.createResource(true,r,a));}
 @PostMapping("/materials") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:material:manage','education:teaching:manage')") public ResultData<?> materials(@Valid @RequestBody ResourceCreateRequest r,Authentication a){return ok(service.createResource(false,r,a));}
 @PostMapping("/{kind:coursewares|materials}/{id}/versions") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:material:manage','education:teaching:manage')") public ResultData<?> version(@PathVariable String kind,@PathVariable String id,@Valid @RequestBody VersionRequest r,Authentication a){return ok(service.addVersion("coursewares".equals(kind),id,r,a));}
 @GetMapping("/{kind:coursewares|materials}/{id}/versions") public ResultData<?> versions(@PathVariable String kind,@PathVariable String id,Authentication a){return ok(service.versions("coursewares".equals(kind),id,a));}
 @GetMapping("/{kind:coursewares|materials}/{id}/published") public ResultData<?> published(@PathVariable String kind,@PathVariable String id,Authentication a){return ok(service.readPublished("coursewares".equals(kind),id,a));}
 @PostMapping("/{kind:coursewares|materials}/versions/{id}/{action:bind|submit|revise|publish|archive}") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:material:manage','education:teaching:manage')") public ResultData<?> transition(@PathVariable String kind,@PathVariable String id,@PathVariable String action,Authentication a){return ok(service.transition("coursewares".equals(kind),id,action,a));}
 private <T> ResultData<T> ok(T d){return ResultData.<T>builder().code("200").msg("success").data(d).build();}
}
