package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="wf_instance_participant", indexes={
    @Index(name="idx_wf_participant_instance", columnList="instance_id"),
    @Index(name="idx_wf_participant_user", columnList="username,participant_type")
}, uniqueConstraints=@UniqueConstraint(name="uk_wf_participant", columnNames={"instance_id","username","participant_type"}))
@Getter @Setter
public class WorkflowInstanceParticipant extends BaseEntity {
    @Column(name="instance_id",nullable=false,length=64) private String instanceId;
    @Column(nullable=false,length=128) private String username;
    @Column(name="participant_type",nullable=false,length=32) private String participantType;
    @Column(name="source_task_id",length=64) private String sourceTaskId;
    @Column(nullable=false) private Boolean active=true;
}
