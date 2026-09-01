package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_ai_setting") @Getter @Setter
public class WorkflowAiSetting extends BaseEntity {
    @Column(nullable=false) private Boolean enabled=false;
    @Column(name="provider_mode",nullable=false,length=30) private String providerMode="LOCAL_PRIVATE";
    @Column(name="allow_external",nullable=false) private Boolean allowExternal=false;
    @Column(name="mask_sensitive_data",nullable=false) private Boolean maskSensitiveData=true;
}
