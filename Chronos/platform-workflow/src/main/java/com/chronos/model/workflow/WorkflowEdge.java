package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_edge") @Getter @Setter
public class WorkflowEdge extends BaseEntity {
    @Column(name="flow_id",nullable=false,length=64) private String flowId;
    @Column(name="from_node_key",nullable=false,length=100) private String fromNodeKey;
    @Column(name="to_node_key",nullable=false,length=100) private String toNodeKey;
    @Column(name="condition_expr",length=1000) private String conditionExpr;
    @Column(name="is_default",nullable=false) private Boolean isDefault=false;
}
