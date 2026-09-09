package com.chronos.industry.controller;

import com.chronos.commons.model.ResultData;
import com.chronos.industry.api.IndustryMetadata;
import com.chronos.industry.service.IndustryTemplateRegistry;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class IndustryContextController {

    private final IndustryTemplateRegistry registry;

    public IndustryContextController(IndustryTemplateRegistry registry) {
        this.registry = registry;
    }

    @GetMapping({
            "/public/industry/context",
            "/api/public/industry/context"
    })
    public ResultData<IndustryMetadata> activeContext() {
        return ResultData.<IndustryMetadata>builder()
                .code("200")
                .msg("success")
                .data(registry.active())
                .build();
    }

    @GetMapping("/admin/industry/templates")
    public ResultData<List<IndustryMetadata>> templates() {
        return ResultData.<List<IndustryMetadata>>builder()
                .code("200")
                .msg("success")
                .data(registry.all())
                .build();
    }
}
