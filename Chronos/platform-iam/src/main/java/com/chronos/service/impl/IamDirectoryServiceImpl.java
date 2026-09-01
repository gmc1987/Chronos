package com.chronos.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IJobTitleRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.Idao.IAdminUserRepository;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.JobTitle;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.Position;
import com.chronos.service.iService.IIamDirectoryService;
import com.chronos.service.iService.IRefreshTokenService;
import com.chronos.model.dto.OrganizationUnitDTO;
import com.chronos.model.dto.EmployeeAssignmentDTO;
import com.chronos.model.vo.OrganizationUnitVO;
import com.chronos.model.vo.EmployeeAssignmentVO;

@Service
@Transactional(readOnly = true)
public class IamDirectoryServiceImpl implements IIamDirectoryService {
    private final IOrganizationRepository organizations; private final IOrganizationUnitRepository units;
    private final IPositionRepository positions; private final IJobTitleRepository jobTitles; private final IJobLevelRepository jobLevels;
    private final IEmployeeRepository employees; private final IEmployeeAssignmentRepository assignments;
    private final IAdminUserRepository users; private final IRefreshTokenService refreshTokens;
    public IamDirectoryServiceImpl(IOrganizationRepository organizations,IOrganizationUnitRepository units,
            IPositionRepository positions,IJobTitleRepository jobTitles,IJobLevelRepository jobLevels,IEmployeeRepository employees,
            IEmployeeAssignmentRepository assignments,IAdminUserRepository users,IRefreshTokenService refreshTokens) {
        this.organizations=organizations;this.units=units;this.positions=positions;this.jobTitles=jobTitles;this.jobLevels=jobLevels;
        this.employees=employees;this.assignments=assignments;this.users=users;this.refreshTokens=refreshTokens;
    }
    public List<OrganizationUnitVO> organizationUnits(String organizationId){
        organizations.findById(organizationId).orElseThrow(()->new IllegalArgumentException("organization not found"));
        List<OrganizationUnitVO> all=units.findByOrgIdOrderBySortOrderAsc(organizationId).stream().map(this::unitVO).toList();
        Map<String,OrganizationUnitVO> map=new HashMap<>();all.forEach(v->map.put(v.getId(),v));List<OrganizationUnitVO> roots=new ArrayList<>();
        all.forEach(v->{if(v.getParentId()!=null&&map.containsKey(v.getParentId()))map.get(v.getParentId()).getChildren().add(v);else roots.add(v);});
        Comparator<OrganizationUnitVO> order=Comparator.comparing(v->v.getSortOrder()==null?0:v.getSortOrder());sortTree(roots,order);return roots;
    }
    @Transactional public OrganizationUnitVO saveOrganizationUnit(OrganizationUnitDTO value){
        var organization=organizations.findById(value.getOrganizationId()).orElseThrow(()->new IllegalArgumentException("organization not found"));if(!Integer.valueOf(1).equals(organization.getStatus()))throw new IllegalStateException("已停用机构不能新增或修改部门");
        boolean duplicate=value.getId()==null?units.existsByOrgIdAndOrganizationUnitCode(value.getOrganizationId(),value.getDepartmentCode()):units.existsByOrgIdAndOrganizationUnitCodeAndIdNot(value.getOrganizationId(),value.getDepartmentCode(),value.getId());
        if(duplicate)throw new IllegalArgumentException("department code already exists in organization");
        OrganizationUnit target=value.getId()==null?new OrganizationUnit():units.findById(value.getId()).orElseThrow(()->new IllegalArgumentException("department not found"));
        if(target.getId()!=null&&!target.getOrganizationUnitCode().equals(value.getDepartmentCode()))throw new IllegalArgumentException("部门编码创建后不允许修改");
        OrganizationUnit parent=null;if(value.getParentId()!=null&&!value.getParentId().isBlank()){
            if(value.getParentId().equals(value.getId()))throw new IllegalArgumentException("department parent cannot be self");
            parent=units.findById(value.getParentId()).orElseThrow(()->new IllegalArgumentException("parent department not found"));
            if(!value.getOrganizationId().equals(parent.getOrgId()))throw new IllegalArgumentException("parent department must belong to same organization");
            if(parent.getTreePath()!=null&&value.getId()!=null&&parent.getTreePath().contains("/"+value.getId()+"/"))throw new IllegalArgumentException("department hierarchy cycle detected");
        }
        target.setOrganizationUnitName(value.getDepartmentName());target.setOrganizationUnitCode(value.getDepartmentCode());target.setOrgId(value.getOrganizationId());
        target.setParentOrganizationUnit(parent);target.setLevel(parent==null?1:(parent.getLevel()==null?1:parent.getLevel())+1);
        target.setSortOrder(value.getSortOrder()==null?0:value.getSortOrder());target.setUnitType(value.getDepartmentType()==null?"DEPARTMENT":value.getDepartmentType());String targetId=target.getId();if(value.getLeaderEmployeeId()!=null&&!value.getLeaderEmployeeId().isBlank()){if(targetId==null||assignments.findCurrentAssignments(value.getLeaderEmployeeId(),java.time.LocalDate.now()).stream().noneMatch(a->targetId.equals(a.getOrganizationUnitId())))throw new IllegalArgumentException("部门负责人必须在该部门有当前有效任职");}target.setLeaderEmployeeId(value.getLeaderEmployeeId());
        target.setStatus(value.getStatus()==null?1:value.getStatus());target.setDescription(value.getDescription());target=units.save(target);
        target.setTreePath(parent==null?"/"+target.getId()+"/":parent.getTreePath()+target.getId()+"/");target=units.save(target);refreshChildPaths(target);return unitVO(target);
    }
    @Transactional public void deleteOrganizationUnit(String id){if(units.existsByParentOrganizationUnit_Id(id))throw new IllegalStateException("部门存在下级部门，不能删除");if(!assignments.findByOrganizationUnitId(id).isEmpty())throw new IllegalStateException("部门存在员工任职，不能删除");units.deleteById(id);}
    public List<Position> positions(){return positions.findAll();}
    @Transactional public Position savePosition(Position v){require(v.getPositionCode(),"position code");require(v.getPositionName(),"position name");Position same=positions.findByPositionCode(v.getPositionCode());if(same!=null&&!same.getId().equals(v.getId()))throw new IllegalArgumentException("position code already exists");Position t=v.getId()==null?new Position():positions.findById(v.getId()).orElseThrow();copyPosition(v,t);return positions.save(t);}
    @Transactional public void deletePosition(String id){if(assignments.existsByPositionIdAndStatus(id,1))throw new IllegalStateException("岗位存在有效任职，不能删除");positions.deleteById(id);}
    public List<JobTitle> jobTitles(){return jobTitles.findAll();}
    @Transactional public JobTitle saveJobTitle(JobTitle v){JobTitle t=v.getId()==null?new JobTitle():jobTitles.findById(v.getId()).orElseThrow();copyJobTitle(v,t);return jobTitles.save(t);}
    @Transactional public void deleteJobTitle(String id){jobTitles.deleteById(id);}
    public List<JobLevel> jobLevels(){return jobLevels.findAll().stream().sorted(Comparator.comparing(v->v.getSortOrder()==null?0:v.getSortOrder())).toList();}
    @Transactional public JobLevel saveJobLevel(JobLevel v){require(v.getLevelCode(),"job level code");require(v.getLevelName(),"job level name");jobLevels.findByLevelCode(v.getLevelCode()).filter(x->!x.getId().equals(v.getId())).ifPresent(x->{throw new IllegalArgumentException("job level code already exists");});JobLevel t=v.getId()==null?new JobLevel():jobLevels.findById(v.getId()).orElseThrow();if(t.getId()!=null&&!java.util.Objects.equals(t.getLevelCode(),v.getLevelCode()))throw new IllegalArgumentException("职级编码创建后不允许修改");t.setLevelCode(v.getLevelCode());t.setLevelName(v.getLevelName());t.setLevelSequence(v.getLevelSequence()==null?0:v.getLevelSequence());t.setLevelCategory(v.getLevelCategory());t.setStatus(v.getStatus()==null?1:v.getStatus());t.setSortOrder(v.getSortOrder()==null?0:v.getSortOrder());t.setDescription(v.getDescription());return jobLevels.save(t);}
    @Transactional public void deleteJobLevel(String id){if(assignments.existsByJobLevelIdAndStatus(id,1))throw new IllegalStateException("职级存在有效任职，不能删除");jobLevels.deleteById(id);}
    public List<Employee> employees(){return employees.findAll();}
    @Transactional public Employee saveEmployee(Employee v){require(v.getEmployeeCode(),"employee code");require(v.getEmployeeName(),"employee name");employees.findByEmployeeCode(v.getEmployeeCode()).filter(x->!x.getId().equals(v.getId())).ifPresent(x->{throw new IllegalArgumentException("employee code already exists");});Employee t=v.getId()==null?new Employee():employees.findById(v.getId()).orElseThrow();copyEmployee(v,t);t=employees.save(t);if(!"ACTIVE".equals(t.getEmploymentStatus()))disableEmployeeAccount(t.getId());return t;}
    @Transactional public void deleteEmployee(String id){if(!assignments.findByEmployeeIdAndStatus(id,1).isEmpty())throw new IllegalStateException("员工存在有效任职，不能删除");if(users.existsByEmployeeId(id))throw new IllegalStateException("员工已开通登录账号，请先停用账号，员工档案不允许直接删除");employees.deleteById(id);}
    public List<EmployeeAssignmentVO> assignments(String employeeId){return assignments.findCurrentAssignments(employeeId,java.time.LocalDate.now()).stream().map(this::assignmentVO).toList();}
    @Transactional public EmployeeAssignmentVO saveAssignment(EmployeeAssignmentDTO v){
        Employee employee=employees.findById(v.getEmployeeId()).orElseThrow(()->new IllegalArgumentException("employee not found"));if(!"ACTIVE".equals(employee.getEmploymentStatus()))throw new IllegalStateException("非在职员工不能新增有效任职");
        var organization=organizations.findById(v.getOrganizationId()).orElseThrow(()->new IllegalArgumentException("organization not found"));if(!Integer.valueOf(1).equals(organization.getStatus()))throw new IllegalStateException("已停用机构不能新增任职");
        OrganizationUnit unit=units.findById(v.getDepartmentId()).orElseThrow(()->new IllegalArgumentException("department not found"));
        if(!v.getOrganizationId().equals(unit.getOrgId()))throw new IllegalArgumentException("department does not belong to organization");
        if(!Integer.valueOf(1).equals(unit.getStatus()))throw new IllegalStateException("已停用部门不能新增任职");
        Position position=positions.findById(v.getPositionId()).orElseThrow(()->new IllegalArgumentException("position not found"));if(!Integer.valueOf(1).equals(position.getStatus()))throw new IllegalStateException("已停用岗位不能新增任职");
        if(v.getJobLevelId()!=null&&!v.getJobLevelId().isBlank()){JobLevel level=jobLevels.findById(v.getJobLevelId()).orElseThrow(()->new IllegalArgumentException("job level not found"));if(!Integer.valueOf(1).equals(level.getStatus()))throw new IllegalStateException("已停用职级不能新增任职");}
        if(v.getEffectiveFrom()!=null&&v.getEffectiveTo()!=null&&v.getEffectiveTo().isBefore(v.getEffectiveFrom()))throw new IllegalArgumentException("effective end date cannot precede start date");
        for(EmployeeAssignment old:assignments.findByEmployeeIdAndStatus(v.getEmployeeId(),1)){if(old.getId().equals(v.getId()))continue;java.time.LocalDate newFrom=v.getEffectiveFrom()==null?java.time.LocalDate.MIN:v.getEffectiveFrom(),newTo=v.getEffectiveTo()==null?java.time.LocalDate.MAX:v.getEffectiveTo(),oldFrom=old.getEffectiveFrom()==null?java.time.LocalDate.MIN:old.getEffectiveFrom(),oldTo=old.getEffectiveTo()==null?java.time.LocalDate.MAX:old.getEffectiveTo();if(!newTo.isBefore(oldFrom)&&!oldTo.isBefore(newFrom)&&java.util.Objects.equals(old.getOrganizationUnitId(),v.getDepartmentId())&&java.util.Objects.equals(old.getPositionId(),v.getPositionId()))throw new IllegalArgumentException("员工在相同部门岗位存在重叠任职期间");}
        if(Boolean.TRUE.equals(v.getPrimaryAssignment())) assignments.findFirstByEmployeeIdAndPrimaryAssignmentTrueAndStatus(v.getEmployeeId(),1)
                .filter(old->!old.getId().equals(v.getId())).ifPresent(old->{old.setPrimaryAssignment(false);assignments.save(old);});
        EmployeeAssignment t=v.getId()==null?new EmployeeAssignment():assignments.findById(v.getId()).orElseThrow();
        t.setEmployeeId(v.getEmployeeId());t.setOrganizationId(v.getOrganizationId());t.setOrganizationUnitId(v.getDepartmentId());
        t.setPositionId(v.getPositionId());t.setJobLevelId(v.getJobLevelId());t.setJobTitleId(v.getJobTitleId());t.setPrimaryAssignment(Boolean.TRUE.equals(v.getPrimaryAssignment()));
        t.setDepartmentLeader(Boolean.TRUE.equals(v.getDepartmentLeader()));t.setEffectiveFrom(v.getEffectiveFrom());t.setEffectiveTo(v.getEffectiveTo());t.setStatus(v.getStatus()==null?1:v.getStatus());return assignmentVO(assignments.save(t));
    }
    @Transactional public void deleteAssignment(String id){assignments.deleteById(id);}
    private OrganizationUnitVO unitVO(OrganizationUnit u){OrganizationUnitVO v=new OrganizationUnitVO();v.setId(u.getId());v.setOrganizationId(u.getOrgId());v.setDepartmentCode(u.getOrganizationUnitCode());v.setDepartmentName(u.getOrganizationUnitName());v.setParentId(u.getParentOrganizationUnit()==null?null:u.getParentOrganizationUnit().getId());v.setDepartmentType(u.getUnitType());v.setLeaderEmployeeId(u.getLeaderEmployeeId());if(u.getLeaderEmployeeId()!=null)employees.findById(u.getLeaderEmployeeId()).ifPresent(e->v.setLeaderEmployeeName(e.getEmployeeName()));v.setLevel(u.getLevel());v.setTreePath(u.getTreePath());v.setSortOrder(u.getSortOrder());v.setStatus(u.getStatus());v.setDescription(u.getDescription());return v;}
    private EmployeeAssignmentVO assignmentVO(EmployeeAssignment a){EmployeeAssignmentVO v=new EmployeeAssignmentVO();v.setId(a.getId());v.setEmployeeId(a.getEmployeeId());employees.findById(a.getEmployeeId()).ifPresent(x->v.setEmployeeName(x.getEmployeeName()));v.setOrganizationId(a.getOrganizationId());organizations.findById(a.getOrganizationId()).ifPresent(x->v.setOrganizationName(x.getOrganizationName()));v.setDepartmentId(a.getOrganizationUnitId());units.findById(a.getOrganizationUnitId()).ifPresent(x->v.setDepartmentName(x.getOrganizationUnitName()));v.setPositionId(a.getPositionId());if(a.getPositionId()!=null)positions.findById(a.getPositionId()).ifPresent(x->v.setPositionName(x.getPositionName()));v.setJobLevelId(a.getJobLevelId());if(a.getJobLevelId()!=null)jobLevels.findById(a.getJobLevelId()).ifPresent(x->v.setJobLevelName(x.getLevelName()));v.setJobTitleId(a.getJobTitleId());if(a.getJobTitleId()!=null)jobTitles.findById(a.getJobTitleId()).ifPresent(x->v.setJobTitleName(x.getTitleName()));v.setPrimaryAssignment(a.getPrimaryAssignment());v.setDepartmentLeader(a.getDepartmentLeader());v.setEffectiveFrom(a.getEffectiveFrom());v.setEffectiveTo(a.getEffectiveTo());v.setStatus(a.getStatus());return v;}
    private void sortTree(List<OrganizationUnitVO> values,Comparator<OrganizationUnitVO> order){values.sort(order);values.forEach(v->sortTree(v.getChildren(),order));}
    private void refreshChildPaths(OrganizationUnit parent){for(OrganizationUnit child:units.findByParentOrganizationUnit_IdOrderBySortOrderAsc(parent.getId())){child.setLevel((parent.getLevel()==null?1:parent.getLevel())+1);child.setTreePath(parent.getTreePath()+child.getId()+"/");units.save(child);refreshChildPaths(child);}}
    private void require(String value,String field){if(value==null||value.isBlank())throw new IllegalArgumentException(field+" required");}
    private void copyPosition(Position v,Position t){if(t.getId()!=null&&!java.util.Objects.equals(t.getPositionCode(),v.getPositionCode()))throw new IllegalArgumentException("岗位编码创建后不允许修改");t.setPositionCode(v.getPositionCode());t.setPositionName(v.getPositionName());t.setPositionCategory(v.getPositionCategory());t.setPositionLevel(v.getPositionLevel());t.setManagement(Boolean.TRUE.equals(v.getManagement()));t.setStatus(v.getStatus()==null?1:v.getStatus());t.setSortOrder(v.getSortOrder()==null?0:v.getSortOrder());t.setDescription(v.getDescription());}
    private void copyJobTitle(JobTitle v,JobTitle t){t.setTitleCode(v.getTitleCode());t.setTitleName(v.getTitleName());t.setTitleType(v.getTitleType());t.setTitleLevel(v.getTitleLevel());t.setStatus(v.getStatus());t.setSortOrder(v.getSortOrder());}
    private void copyEmployee(Employee v,Employee t){if(t.getId()!=null&&!java.util.Objects.equals(t.getEmployeeCode(),v.getEmployeeCode()))throw new IllegalArgumentException("工号创建后不允许修改");t.setEmployeeCode(v.getEmployeeCode());t.setEmployeeName(v.getEmployeeName());t.setGender(v.getGender());t.setPhone(v.getPhone());t.setEmail(v.getEmail());t.setIdNumberCipher(v.getIdNumberCipher());t.setEmploymentStatus(v.getEmploymentStatus());t.setEmployeeType(v.getEmployeeType());t.setHireDate(v.getHireDate());t.setLeaveDate(v.getLeaveDate());}
    private void disableEmployeeAccount(String employeeId){users.findByEmployeeId(employeeId).ifPresent(user->{if(!Integer.valueOf(0).equals(user.getStatus())){user.setStatus(0);user.setAccountLocked(true);user.setTokenVersion((user.getTokenVersion()==null?0:user.getTokenVersion())+1);users.save(user);refreshTokens.revokeAll(user.getUsername());}});}
}
