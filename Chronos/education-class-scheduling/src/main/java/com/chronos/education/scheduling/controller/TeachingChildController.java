package com.chronos.education.scheduling.controller;
import com.chronos.commons.model.*; import com.chronos.education.scheduling.service.TeachingChildService; import java.util.*; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/education/teaching-center/children")
public class TeachingChildController { private final TeachingChildService service; public TeachingChildController(TeachingChildService s){service=s;}
 @GetMapping("/{type}") @PreAuthorize("@iamAuthorization.any(authentication,'education:teaching:view','education:teaching:manage')") public ResultData<?> page(@PathVariable String type,@RequestParam String parentId,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size,Authentication a){return ok(service.page(type,parentId,page,size,a));}
 private <T> ResultData<T> ok(T v){return ResultData.<T>builder().code("200").msg("success").data(v).build();}}
