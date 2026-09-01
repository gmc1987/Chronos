package com.chronos.workflow;

import com.chronos.Idao.*;
import com.chronos.model.pojo.*;
import com.chronos.model.workflow.*;
import com.fasterxml.jackson.databind.*;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowAssigneeResolver {
    private final IAdminUserRepository users;private final IRoleRepository roles;private final IEmployeeAssignmentRepository assignments;private final ObjectMapper json=new ObjectMapper();
    public WorkflowAssigneeResolver(IAdminUserRepository users,IRoleRepository roles,IEmployeeAssignmentRepository assignments){this.users=users;this.roles=roles;this.assignments=assignments;}
    public List<String> resolve(WorkflowNode node,WorkflowInstance instance,String fallback){try{JsonNode p=json.readTree(node.getPropertiesJson()==null?"{}":node.getPropertiesJson());String mode=p.path("assigneeMode").asText("USER"),value=p.path("assigneeValue").asText(p.path("assignee").asText());List<String> result=switch(mode){case "ROLE"->byRole(value);case "INITIATOR_MANAGER"->manager(instance.getInitiator());case "FORM_FIELD"->byVariable(instance.getVariablesJson(),value);default->value.isBlank()?List.of():List.of(value);};return result.stream().filter(x->x!=null&&!x.isBlank()).distinct().sorted().toList();}catch(Exception e){return List.of(fallback);}}
    private List<String> byRole(String code){Role role=roles.findByRoleCode(code);return role==null?List.of():users.findByRoles_Id(role.getId()).stream().filter(u->Integer.valueOf(1).equals(u.getStatus())).map(AdminUser::getUsername).toList();}
    private List<String> manager(String initiator){AdminUser user=users.findByUsername(initiator);if(user==null||user.getEmployeeId()==null)return List.of();return assignments.findCurrentPrimaryAssignment(user.getEmployeeId(),LocalDate.now()).stream().flatMap(a->assignments.findByOrganizationUnitIdAndDepartmentLeaderTrueAndStatus(a.getOrganizationUnitId(),1).stream()).map(a->users.findByEmployeeId(a.getEmployeeId()).orElse(null)).filter(Objects::nonNull).map(AdminUser::getUsername).toList();}
    private List<String> byVariable(String variables,String key){try{JsonNode value=json.readTree(variables==null?"{}":variables).path(key);if(value.isArray()){List<String> result=new ArrayList<>();value.forEach(x->result.add(x.asText()));return result;}return value.isMissingNode()?List.of():List.of(value.asText());}catch(Exception e){return List.of();}}
}
