package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="wf_task_candidate", indexes={
    @Index(name="idx_wf_candidate_task", columnList="task_id"),
    @Index(name="idx_wf_candidate_subject", columnList="subject_type,subject_id")
}, uniqueConstraints=@UniqueConstraint(name="uk_wf_candidate", columnNames={"task_id","subject_type","subject_id"}))
@Getter @Setter
public class WorkflowTaskCandidate extends BaseEntity {
    @Column(name="task_id",nullable=false,length=64) private String taskId;
    @Column(name="subject_type",nullable=false,length=32) private String subjectType;
    @Column(name="subject_id",nullable=false,length=128) private String subjectId;
}
