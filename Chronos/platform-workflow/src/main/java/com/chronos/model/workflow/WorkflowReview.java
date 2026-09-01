package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_review") @Getter @Setter
public class WorkflowReview extends BaseEntity {
    @Column(name="flow_id",nullable=false,length=64) private String flowId;
    @Column(name="node_id",length=64) private String nodeId;
    @Column(nullable=false,length=20) private String source;
    @Column(nullable=false,length=20) private String severity;
    @Column(nullable=false,length=80) private String category;
    @Column(nullable=false,length=300) private String title;
    @Column(length=2000) private String description;
    @Column(length=2000) private String suggestion;
    @Column(name="blocking",nullable=false) private Boolean blocking=false;
    @Column(name="config_hash",length=64) private String configHash;
}
