package com.chronos.workflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.chronos.Idao.workflow.IWorkflowDefinitionRepository;
import com.chronos.Idao.workflow.IWorkflowEdgeRepository;
import com.chronos.Idao.workflow.IWorkflowNodeRepository;
import com.chronos.model.workflow.WorkflowDefinition;
import com.chronos.model.workflow.WorkflowEdge;
import com.chronos.model.workflow.WorkflowNode;

@ExtendWith(MockitoExtension.class)
class WorkflowVersionComparisonTest {
	@Mock private IWorkflowDefinitionRepository definitions;
	@Mock private IWorkflowNodeRepository nodes;
	@Mock private IWorkflowEdgeRepository edges;
	@InjectMocks private WorkflowService service;

	@Test
	void highlightsBehaviorAndEdgeChanges() {
		when(definitions.findById("old"))
				.thenReturn(Optional.of(definition("old", "LEAVE", "v1")));
		when(definitions.findById("new"))
				.thenReturn(Optional.of(definition("new", "LEAVE", "v2")));
		when(nodes.findByFlowIdOrderByCreateTimeAsc("old"))
				.thenReturn(List.of(node("approval", "APPROVAL")));
		when(nodes.findByFlowIdOrderByCreateTimeAsc("new"))
				.thenReturn(List.of(node("approval", "SERVICE_TASK")));
		when(edges.findByFlowIdOrderByCreateTimeAsc("old"))
				.thenReturn(List.of());
		when(edges.findByFlowIdOrderByCreateTimeAsc("new"))
				.thenReturn(List.of(edge("approval", "end")));

		var comparison = service.compareVersions("old", "new");

		assertThat(comparison.requiresReview()).isTrue();
		assertThat(comparison.changes())
				.extracting(WorkflowService.WorkflowVersionChange::change)
				.contains("NODE_BEHAVIOR_CHANGED", "EDGE_ADDED");
	}

	@Test
	void rejectsDifferentWorkflowCodes() {
		when(definitions.findById("old"))
				.thenReturn(Optional.of(definition("old", "LEAVE", "v1")));
		when(definitions.findById("new"))
				.thenReturn(Optional.of(definition("new", "PURCHASE", "v2")));

		assertThatThrownBy(() -> service.compareVersions("old", "new"))
				.isInstanceOf(IllegalArgumentException.class);
	}

	private WorkflowDefinition definition(String id, String code, String version) {
		WorkflowDefinition definition = new WorkflowDefinition();
		definition.setId(id);
		definition.setFlowCode(code);
		definition.setVersion(version);
		return definition;
	}

	private WorkflowNode node(String key, String type) {
		WorkflowNode node = new WorkflowNode();
		node.setNodeKey(key);
		node.setNodeType(type);
		return node;
	}

	private WorkflowEdge edge(String from, String to) {
		WorkflowEdge edge = new WorkflowEdge();
		edge.setFromNodeKey(from);
		edge.setToNodeKey(to);
		edge.setIsDefault(true);
		return edge;
	}
}
