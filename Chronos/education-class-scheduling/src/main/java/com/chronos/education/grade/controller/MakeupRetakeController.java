package com.chronos.education.grade.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import com.chronos.commons.model.ResultData;
import com.chronos.education.grade.dto.MakeupRetakeDtos.CreateCommand;
import com.chronos.education.grade.dto.MakeupRetakeDtos.View;
import com.chronos.education.grade.service.MakeupRetakeService;
import com.chronos.education.scheduling.dao.EducationUserBindingRepository;
import com.chronos.education.scheduling.dao.StudentGuardianRepository;
import org.springframework.security.access.AccessDeniedException;

@RestController
public class MakeupRetakeController {
    private final MakeupRetakeService service;
    private final EducationUserBindingRepository bindings;
    private final StudentGuardianRepository guardians;

    public MakeupRetakeController(MakeupRetakeService service, EducationUserBindingRepository bindings,
            StudentGuardianRepository guardians) {
        this.service = service;
        this.bindings = bindings;
        this.guardians = guardians;
    }

    @PostMapping("/admin/education/grades/makeup-retakes")
    @PreAuthorize("hasAuthority('education:score:makeup-retake:manage')")
    public ResultData<View> create(@RequestBody CreateCommand command, Authentication authentication) {
        return ok(View.of(service.create(command, authentication.getName())));
    }

    @PostMapping("/admin/education/grades/makeup-retakes/{id}/submit")
    @PreAuthorize("hasAuthority('education:score:makeup-retake:submit')")
    public ResultData<View> submit(@PathVariable String id, Authentication authentication) {
        return ok(View.of(service.submit(id, authentication.getName())));
    }

    @PostMapping("/admin/education/grades/makeup-retakes/{id}/decision")
    @PreAuthorize("hasAuthority('education:score:makeup-retake:approve')")
    public ResultData<View> decision(@PathVariable String id, @RequestParam boolean approved,
            Authentication authentication) {
        return ok(View.of(service.decide(id, approved, authentication.getName())));
    }

    @PostMapping("/admin/education/grades/makeup-retakes/{id}/publish")
    @PreAuthorize("hasAuthority('education:score:makeup-retake:publish')")
    public ResultData<View> publish(@PathVariable String id, Authentication authentication) {
        return ok(View.of(service.publish(id, authentication.getName())));
    }

    @GetMapping("/portal/education/grades/makeup-retakes")
    public ResultData<List<View>> student(Authentication authentication) {
        String studentId = bindings.findByUsernameAndProfileType(authentication.getName(), "STUDENT")
                .filter(x -> "ACTIVE".equals(x.getStatus())).map(x -> x.getProfileId())
                .orElseThrow(() -> new AccessDeniedException("当前账号不是有效学生账号"));
        return ok(service.visibleToStudent(studentId).stream().map(View::of).toList());
    }

    @GetMapping("/portal/education/family/grades/makeup-retakes")
    public ResultData<List<View>> parent(Authentication authentication) {
        String parentId = bindings.findByUsernameAndProfileType(authentication.getName(), "PARENT")
                .filter(x -> "ACTIVE".equals(x.getStatus())).map(x -> x.getProfileId())
                .orElseThrow(() -> new AccessDeniedException("当前账号不是有效家长账号"));
        return ok(service.visibleToParent(parentId, guardians).stream().map(View::of).toList());
    }

    private <T> ResultData<T> ok(T data) {
        return ResultData.<T>builder().code("200").msg("ok").data(data).build();
    }
}
