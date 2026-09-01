package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_task") @Getter @Setter
public class WorkflowTask extends BaseEntity {
    @Column(name="instance_id",nullable=false,length=64) private String instanceId;
    @Column(name="node_key",nullable=false,length=100) private String nodeKey;
    @Column(name="node_name",nullable=false,length=160) private String nodeName;
    @Column(name="assignee",nullable=false,length=128) private String assignee;
    @Column(nullable=false,length=30) private String status="PENDING";
    @Column(length=1000) private String comment;
    @Column(name="completed_at") private LocalDateTime completedAt;
    @Column(name="due_at") private LocalDateTime dueAt;
    @Column(name="reminded_at") private LocalDateTime remindedAt;
}
