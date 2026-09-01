package com.chronos.service.impl;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.multipart.MultipartFile;
import com.chronos.Idao.IEmployeeAssignmentRepository;
import com.chronos.Idao.IEmployeeRepository;
import com.chronos.Idao.IJobLevelRepository;
import com.chronos.Idao.IOrganizationRepository;
import com.chronos.Idao.IOrganizationUnitRepository;
import com.chronos.Idao.IPositionRepository;
import com.chronos.model.pojo.Employee;
import com.chronos.model.pojo.EmployeeAssignment;
import com.chronos.model.pojo.JobLevel;
import com.chronos.model.pojo.Organization;
import com.chronos.model.pojo.OrganizationUnit;
import com.chronos.model.pojo.Position;
import com.chronos.service.iService.IAuditLogService;

@Service
public class DirectoryImportService {
    private static final int MAX_ROWS=5000;
    private static final Set<String> TYPES=Set.of("departments","positions","job-levels","employees");
    private final IOrganizationRepository organizations;private final IOrganizationUnitRepository units;private final IPositionRepository positions;
    private final IJobLevelRepository levels;private final IEmployeeRepository employees;private final IEmployeeAssignmentRepository assignments;
    private final IAuditLogService audit;
    private final DataFormatter formatter=new DataFormatter();
    public DirectoryImportService(IOrganizationRepository organizations,IOrganizationUnitRepository units,IPositionRepository positions,
            IJobLevelRepository levels,IEmployeeRepository employees,IEmployeeAssignmentRepository assignments,IAuditLogService audit){this.organizations=organizations;this.units=units;this.positions=positions;this.levels=levels;this.employees=employees;this.assignments=assignments;this.audit=audit;}

    public byte[] template(String type){checkType(type);try(Workbook wb=new XSSFWorkbook();ByteArrayOutputStream out=new ByteArrayOutputStream()){
        Sheet guide=wb.createSheet("填写说明");String[][] notes={{"导入说明","请勿修改数据页表头；业务编码用于新增或更新；删除示例行后填写正式数据。"},{"事务规则","系统先校验整批数据，任一行失败则整批不入库，单次最多5000行。"},{"日期格式","统一使用 yyyy-MM-dd。"},{"状态编码","状态：1=启用，0=停用；布尔值：是/否。"}};writeRows(guide,notes);guide.setColumnWidth(0,5200);guide.setColumnWidth(1,22000);
        String[] headers;String[] sample;
        switch(type){
            case "departments"->{headers=new String[]{"机构编码*","部门编码*","部门名称*","上级部门编码","部门类型","负责人工作号","排序","状态","说明"};sample=new String[]{"HOSPITAL_001","DEPT_001","心内科","","CLINICAL","E0001","10","1","示例行，请删除"};}
            case "positions"->{headers=new String[]{"岗位编码*","岗位名称*","岗位类别","管理岗位","岗位等级","排序","状态","说明"};sample=new String[]{"DOCTOR","医师","MEDICAL","否","1","10","1","示例行，请删除"};}
            case "job-levels"->{headers=new String[]{"职级编码*","职级名称*","职级序列","等级值","排序","状态","说明"};sample=new String[]{"JUNIOR","初级","CLINICAL","1","10","1","示例行，请删除"};}
            default->{headers=new String[]{"工号*","姓名*","性别","手机号","邮箱","员工类型","在职状态","入职日期","离职日期","机构编码","部门编码","岗位编码","职级编码","主任职","部门负责人","任职生效日期","任职失效日期","任职状态","说明"};sample=new String[]{"E0001","张医生","MALE","13800000000","doctor@example.com","STAFF","ACTIVE","2026-01-01","","HOSPITAL_001","DEPT_001","DOCTOR","JUNIOR","是","否","2026-01-01","","1","示例行，请删除"};}
        }
        Sheet data=wb.createSheet("导入数据");Row header=data.createRow(0);CellStyle hs=headerStyle(wb);for(int i=0;i<headers.length;i++){Cell c=header.createCell(i);c.setCellValue(headers[i]);c.setCellStyle(hs);data.setColumnWidth(i,Math.min(7200,Math.max(3600,headers[i].length()*900)));}Row ex=data.createRow(1);for(int i=0;i<sample.length;i++)ex.createCell(i).setCellValue(sample[i]);data.createFreezePane(0,1);data.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0,1,0,headers.length-1));wb.setActiveSheet(1);wb.write(out);return out.toByteArray();
    }catch(Exception e){throw new IllegalStateException("template generation failed",e);}}

    @Transactional
    public Map<String,Object> importFile(String type,MultipartFile file,boolean dryRun){checkType(type);if(file==null||file.isEmpty())throw new IllegalArgumentException("请选择Excel文件");if(file.getSize()>10*1024*1024)throw new IllegalArgumentException("文件不能超过10MB");try(Workbook wb=new XSSFWorkbook(new ByteArrayInputStream(file.getBytes()))){for(int i=0;i<wb.getNumberOfSheets();i++)if(wb.isSheetHidden(i)||wb.isSheetVeryHidden(i))throw new IllegalArgumentException("模板不允许包含隐藏工作表");Sheet sheet=wb.getSheet("导入数据");if(sheet==null)throw new IllegalArgumentException("缺少“导入数据”工作表");if(sheet.getLastRowNum()>MAX_ROWS)throw new IllegalArgumentException("单次导入不能超过5000行");rejectFormulas(sheet);List<Map<String,String>> rows=read(sheet);if(rows.isEmpty())throw new IllegalArgumentException("没有可导入的数据");int count=switch(type){case "departments"->importDepartments(rows);case "positions"->importPositions(rows);case "job-levels"->importLevels(rows);default->importEmployees(rows);};String actor=SecurityContextHolder.getContext().getAuthentication()==null?"unknown":SecurityContextHolder.getContext().getAuthentication().getName();if(dryRun)TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();else audit.log(actor,"DIRECTORY_IMPORT","type="+type+", count="+count);return Map.of("type",type,"successCount",count,"dryRun",dryRun,"valid",true,"message",dryRun?"校验通过":"导入成功");}catch(IllegalArgumentException e){if(dryRun){TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();return Map.of("type",type,"successCount",0,"dryRun",true,"valid",false,"errors",java.util.Arrays.asList(e.getMessage().split("；")),"message","校验失败");}throw e;}catch(Exception e){throw new IllegalArgumentException("Excel解析失败，请使用系统模板并检查文件格式："+e.getMessage(),e);}}

    private int importDepartments(List<Map<String,String>> rows){List<String> errors=new ArrayList<>();List<DepartmentRow> values=new ArrayList<>();int n=1;for(Map<String,String> r:rows){n++;String oc=v(r,"机构编码*"),code=v(r,"部门编码*"),name=v(r,"部门名称*");Organization org=organizations.findByOrgCode(oc);if(org==null)errors.add(err(n,"机构编码不存在："+oc));if(blank(code))errors.add(err(n,"部门编码不能为空"));if(blank(name))errors.add(err(n,"部门名称不能为空"));values.add(new DepartmentRow(n,org,code,name,v(r,"上级部门编码"),def(v(r,"部门类型"),"DEPARTMENT"),v(r,"负责人工作号"),integer(r,"排序",0,n,errors),integer(r,"状态",1,n,errors),v(r,"说明")));}fail(errors);List<DepartmentRow> pending=new ArrayList<>(values);int saved=0;while(!pending.isEmpty()){int before=pending.size();var it=pending.iterator();while(it.hasNext()){DepartmentRow x=it.next();OrganizationUnit parent=blank(x.parentCode)?null:units.findByOrgIdAndOrganizationUnitCode(x.org.getId(),x.parentCode).orElse(null);if(!blank(x.parentCode)&&parent==null)continue;OrganizationUnit target=units.findByOrgIdAndOrganizationUnitCode(x.org.getId(),x.code).orElseGet(OrganizationUnit::new);target.setOrgId(x.org.getId());target.setOrganizationUnitCode(x.code);target.setOrganizationUnitName(x.name);target.setParentOrganizationUnit(parent);target.setLevel(parent==null?1:(parent.getLevel()==null?1:parent.getLevel())+1);target.setTreePath(parent==null?null:parent.getTreePath());target.setUnitType(x.type);target.setSortOrder(x.sort);target.setStatus(x.status);target.setDescription(x.description);if(!blank(x.leaderCode)){Employee leader=employees.findByEmployeeCode(x.leaderCode).orElseThrow(()->new IllegalArgumentException(err(x.row,"负责人工作号不存在："+x.leaderCode)));target.setLeaderEmployeeId(leader.getId());}target=units.save(target);target.setTreePath(parent==null?"/"+target.getId()+"/":parent.getTreePath()+target.getId()+"/");units.save(target);it.remove();saved++;}if(before==pending.size())throw new IllegalArgumentException("导入失败：上级部门编码不存在或存在循环引用，涉及行："+pending.stream().map(x->String.valueOf(x.row)).toList());}return saved;}
    private int importPositions(List<Map<String,String>> rows){List<String> errors=new ArrayList<>();List<Position> values=new ArrayList<>();int n=1;for(Map<String,String> r:rows){n++;String code=v(r,"岗位编码*"),name=v(r,"岗位名称*");if(blank(code))errors.add(err(n,"岗位编码不能为空"));if(blank(name))errors.add(err(n,"岗位名称不能为空"));Position p=positions.findByPositionCode(code);if(p==null)p=new Position();p.setPositionCode(code);p.setPositionName(name);p.setPositionCategory(v(r,"岗位类别"));p.setManagement(bool(v(r,"管理岗位")));p.setPositionLevel(v(r,"岗位等级"));p.setSortOrder(integer(r,"排序",0,n,errors));p.setStatus(integer(r,"状态",1,n,errors));p.setDescription(v(r,"说明"));values.add(p);}fail(errors);positions.saveAll(values);return values.size();}
    private int importLevels(List<Map<String,String>> rows){List<String> errors=new ArrayList<>();List<JobLevel> values=new ArrayList<>();int n=1;for(Map<String,String> r:rows){n++;String code=v(r,"职级编码*"),name=v(r,"职级名称*");if(blank(code))errors.add(err(n,"职级编码不能为空"));if(blank(name))errors.add(err(n,"职级名称不能为空"));JobLevel l=levels.findByLevelCode(code).orElseGet(JobLevel::new);l.setLevelCode(code);l.setLevelName(name);l.setLevelCategory(v(r,"职级序列"));l.setLevelSequence(integer(r,"等级值",0,n,errors));l.setSortOrder(integer(r,"排序",0,n,errors));l.setStatus(integer(r,"状态",1,n,errors));l.setDescription(v(r,"说明"));values.add(l);}fail(errors);levels.saveAll(values);return values.size();}
    private int importEmployees(List<Map<String,String>> rows){List<String> errors=new ArrayList<>();List<EmployeeRow> values=new ArrayList<>();int n=1;for(Map<String,String> r:rows){n++;String code=v(r,"工号*"),name=v(r,"姓名*");if(blank(code))errors.add(err(n,"工号不能为空"));if(blank(name))errors.add(err(n,"姓名不能为空"));LocalDate hire=date(r,"入职日期",n,errors),leave=date(r,"离职日期",n,errors),from=date(r,"任职生效日期",n,errors),to=date(r,"任职失效日期",n,errors);if(from!=null&&to!=null&&to.isBefore(from))errors.add(err(n,"任职失效日期不能早于生效日期"));values.add(new EmployeeRow(n,r,code,name,hire,leave,from,to));}fail(errors);int count=0;for(EmployeeRow x:values){Map<String,String> r=x.data;Employee e=employees.findByEmployeeCode(x.code).orElseGet(Employee::new);e.setEmployeeCode(x.code);e.setEmployeeName(x.name);e.setGender(def(v(r,"性别"),"UNSPECIFIED"));e.setPhone(v(r,"手机号"));e.setEmail(v(r,"邮箱"));e.setEmployeeType(def(v(r,"员工类型"),"STAFF"));e.setEmploymentStatus(def(v(r,"在职状态"),"ACTIVE"));e.setHireDate(x.hire);e.setLeaveDate(x.leave);e=employees.save(e);String orgCode=v(r,"机构编码"),deptCode=v(r,"部门编码"),positionCode=v(r,"岗位编码");if(!blank(orgCode)||!blank(deptCode)||!blank(positionCode)){if(blank(orgCode)||blank(deptCode)||blank(positionCode))throw new IllegalArgumentException(err(x.row,"任职信息中的机构、部门、岗位编码必须同时填写"));Organization org=organizations.findByOrgCode(orgCode);if(org==null)throw new IllegalArgumentException(err(x.row,"机构编码不存在："+orgCode));OrganizationUnit unit=units.findByOrgIdAndOrganizationUnitCode(org.getId(),deptCode).orElseThrow(()->new IllegalArgumentException(err(x.row,"部门编码不存在："+deptCode)));Position pos=positions.findByPositionCode(positionCode);if(pos==null)throw new IllegalArgumentException(err(x.row,"岗位编码不存在："+positionCode));EmployeeAssignment a=assignments.findFirstByEmployeeIdAndOrganizationIdAndOrganizationUnitIdAndPositionId(e.getId(),org.getId(),unit.getId(),pos.getId()).orElseGet(EmployeeAssignment::new);a.setEmployeeId(e.getId());a.setOrganizationId(org.getId());a.setOrganizationUnitId(unit.getId());a.setPositionId(pos.getId());String levelCode=v(r,"职级编码");a.setJobLevelId(blank(levelCode)?null:levels.findByLevelCode(levelCode).orElseThrow(()->new IllegalArgumentException(err(x.row,"职级编码不存在："+levelCode))).getId());a.setPrimaryAssignment(bool(v(r,"主任职")));a.setDepartmentLeader(bool(v(r,"部门负责人")));a.setEffectiveFrom(x.from);a.setEffectiveTo(x.to);a.setStatus(integer(r,"任职状态",1,x.row,new ArrayList<>()));if(Boolean.TRUE.equals(a.getPrimaryAssignment()))assignments.findFirstByEmployeeIdAndPrimaryAssignmentTrueAndStatus(e.getId(),1).filter(old->!old.getId().equals(a.getId())).ifPresent(old->{old.setPrimaryAssignment(false);assignments.save(old);});assignments.save(a);}count++;}return count;}

    private List<Map<String,String>> read(Sheet s){Row h=s.getRow(0);if(h==null)throw new IllegalArgumentException("模板表头缺失");List<String> headers=new ArrayList<>();for(Cell c:h)headers.add(formatter.formatCellValue(c).trim());List<Map<String,String>> result=new ArrayList<>();for(int i=1;i<=s.getLastRowNum();i++){Row row=s.getRow(i);if(row==null)continue;Map<String,String> value=new LinkedHashMap<>();boolean any=false;for(int c=0;c<headers.size();c++){String text=formatter.formatCellValue(row.getCell(c,Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)).trim();value.put(headers.get(c),text);any|=!text.isBlank();}if(any&&!"示例行，请删除".equals(value.get("说明")))result.add(value);}return result;}
    private void rejectFormulas(Sheet sheet){for(Row row:sheet)for(Cell cell:row)if(cell.getCellType()==org.apache.poi.ss.usermodel.CellType.FORMULA)throw new IllegalArgumentException("导入数据不允许包含公式，位置："+cell.getAddress());}
    private void checkType(String type){if(!TYPES.contains(type))throw new IllegalArgumentException("unsupported import type");}
    private String v(Map<String,String> r,String k){return r.getOrDefault(k,"").trim();}private boolean blank(String v){return v==null||v.isBlank();}private String def(String v,String d){return blank(v)?d:v;}
    private int integer(Map<String,String> r,String key,int d,int row,List<String> errors){String v=v(r,key);if(blank(v))return d;try{return Integer.parseInt(v.replace(".0",""));}catch(Exception e){errors.add(err(row,key+"必须是整数"));return d;}}
    private LocalDate date(Map<String,String> r,String key,int row,List<String> errors){String v=v(r,key);if(blank(v))return null;try{return LocalDate.parse(v);}catch(Exception e){errors.add(err(row,key+"格式必须为yyyy-MM-dd"));return null;}}
    private boolean bool(String v){return "是".equals(v)||"Y".equalsIgnoreCase(v)||"TRUE".equalsIgnoreCase(v)||"1".equals(v);}
    private String err(int row,String msg){return "第"+row+"行："+msg;}private void fail(List<String> errors){if(!errors.isEmpty())throw new IllegalArgumentException("导入校验失败："+String.join("；",errors));}
    private void writeRows(Sheet sheet,String[][] rows){for(int r=0;r<rows.length;r++){Row row=sheet.createRow(r);for(int c=0;c<rows[r].length;c++)row.createCell(c).setCellValue(rows[r][c]);}}
    private CellStyle headerStyle(Workbook wb){CellStyle s=wb.createCellStyle();s.setFillForegroundColor(IndexedColors.TEAL.getIndex());s.setFillPattern(FillPatternType.SOLID_FOREGROUND);Font f=wb.createFont();f.setBold(true);f.setColor(IndexedColors.WHITE.getIndex());s.setFont(f);return s;}
    private record DepartmentRow(int row,Organization org,String code,String name,String parentCode,String type,String leaderCode,int sort,int status,String description){}
    private record EmployeeRow(int row,Map<String,String> data,String code,String name,LocalDate hire,LocalDate leave,LocalDate from,LocalDate to){}
}
