package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_instance") @Getter @Setter
public class WorkflowInstance extends BaseEntity {
    @Column(name="definition_id",nullable=false,length=64) private String definitionId;
    @Column(name="definition_version",nullable=false,length=40) private String definitionVersion;
    @Column(name="business_key",length=160) private String businessKey;
    @Column(nullable=false,length=128) private String initiator;
    @Column(nullable=false,length=30) private String status="RUNNING";
    @Column(name="current_node_key",length=100) private String currentNodeKey;
    @Lob @Column(name="variables_json") private String variablesJson;
    @Column(name="finished_at") private LocalDateTime finishedAt;
}
