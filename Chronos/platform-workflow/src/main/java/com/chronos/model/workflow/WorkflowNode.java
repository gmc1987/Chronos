package com.chronos.model.workflow;

import com.chronos.model.pojo.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity @Table(name="wf_node",uniqueConstraints=@UniqueConstraint(columnNames={"flow_id","node_key"}))
@Getter @Setter
public class WorkflowNode extends BaseEntity {
    @Column(name="flow_id",nullable=false,length=64) private String flowId;
    @Column(name="node_key",nullable=false,length=100) private String nodeKey;
    @Column(name="node_name",nullable=false,length=160) private String nodeName;
    @Column(name="node_type",nullable=false,length=40) private String nodeType;
    @Column(length=160) private String executor;
    @Column(name="timeout_sec") private Integer timeoutSec=0;
    @Column(name="retry_max") private Integer retryMax=0;
    @Column(name="retry_interval_sec") private Integer retryIntervalSec=0;
    @Lob @Column(name="input_schema") private String inputSchema;
    @Lob @Column(name="output_schema") private String outputSchema;
    @Lob @Column(name="properties_json") private String propertiesJson;
    @Lob @Column(name="additional_form_ids") private String additionalFormIds;
    @Lob @Column(name="field_permissions_json") private String fieldPermissionsJson;
}
