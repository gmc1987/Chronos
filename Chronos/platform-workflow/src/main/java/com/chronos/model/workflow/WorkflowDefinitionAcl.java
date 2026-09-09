package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name="wf_definition_acl", indexes={
    @Index(name="idx_wf_acl_definition_action", columnList="definition_id,action"),
    @Index(name="idx_wf_acl_subject", columnList="subject_type,subject_id")
}, uniqueConstraints=@UniqueConstraint(name="uk_wf_acl_rule", columnNames={"definition_id","subject_type","subject_id","action"}))
@Getter @Setter
public class WorkflowDefinitionAcl extends BaseEntity {
    @Column(name="definition_id",nullable=false,length=64) private String definitionId;
    @Column(name="subject_type",nullable=false,length=32) private String subjectType;
    @Column(name="subject_id",nullable=false,length=128) private String subjectId;
    @Column(nullable=false,length=32) private String action;
    @Column(nullable=false) private Boolean enabled=true;
}
