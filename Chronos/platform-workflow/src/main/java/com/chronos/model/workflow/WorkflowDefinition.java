package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_definition", uniqueConstraints=@UniqueConstraint(columnNames={"flow_code","version"}))
@Getter @Setter
public class WorkflowDefinition extends BaseEntity {
    @Column(name="flow_code",length=100) private String flowCode;
    @Column(name="flow_name",nullable=false,length=160) private String flowName;
    @Column(length=100) private String category;
    @Column(nullable=false,length=40) private String version="v1";
    @Column(length=1000) private String description;
    @Column(name="entry_node_key",length=100) private String entryNodeKey;
    @Column(nullable=false,length=30) private String status="DRAFT";
    @Column(length=500) private String tags;
    @Lob @Column(name="config_json") private String configJson;
    @Column(name="main_form_id",length=64) private String mainFormId;
    @Column(name="manager_user",length=128) private String managerUser;
    @Lob @Column(name="starter_scope_json") private String starterScopeJson;
    @Column(name="ai_assist_enabled",nullable=false) private Boolean aiAssistEnabled=false;
    @Column(name="published_at") private LocalDateTime publishedAt;
    @Column(name="flowable_deployment_id",length=64) private String flowableDeploymentId;
    @Column(name="flowable_process_key",length=160) private String flowableProcessKey;
}
