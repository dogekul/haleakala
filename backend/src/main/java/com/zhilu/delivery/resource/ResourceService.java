package com.zhilu.delivery.resource;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import com.zhilu.delivery.iam.service.CurrentUser;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {
  private final JdbcTemplate jdbc;
  private final SimpleJdbcInsert assignmentInsert;
  public ResourceService(JdbcTemplate jdbc) {
    this.jdbc=jdbc;
    this.assignmentInsert=new SimpleJdbcInsert(jdbc).withTableName("resource_assignment")
        .usingColumns("organization_id","user_id","project_id","assignment_role","start_date","end_date","allocation_percent","created_by")
        .usingGeneratedKeyColumns("id");
  }

  public List<Map<String,Object>> team(CurrentUser user,String keyword){
    String sql="select u.id,u.username,u.display_name,u.email,coalesce(p.job_title,'\u5f85\u5b8c\u5584') job_title,coalesce(p.location,'\u672a\u8bbe\u7f6e') location,coalesce(p.weekly_capacity_hours,40) weekly_capacity_hours,coalesce(p.resource_status,'ACTIVE') resource_status from app_user u left join engineer_profile p on p.user_id=u.id where u.organization_id=? and u.status='ACTIVE'";
    List<Object> args=new ArrayList<Object>();args.add(user.getOrganizationId());
    if(!blank(keyword)){sql+=" and (lower(u.display_name) like ? or lower(u.username) like ? or lower(p.job_title) like ?)";String pattern="%"+keyword.trim().toLowerCase()+"%";args.add(pattern);args.add(pattern);args.add(pattern);}sql+=" order by u.display_name";
    List<Map<String,Object>> values=jdbc.query(sql,(row,index)->{Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("userId",row.getLong("id"));value.put("username",row.getString("username"));value.put("displayName",row.getString("display_name"));value.put("email",row.getString("email"));value.put("jobTitle",row.getString("job_title"));value.put("location",row.getString("location"));value.put("weeklyCapacityHours",row.getInt("weekly_capacity_hours"));value.put("resourceStatus",row.getString("resource_status"));return value;},args.toArray());
    for(Map<String,Object> value:values)value.put("skills",skills(((Number)value.get("userId")).longValue(),user));
    return values;
  }

  @Transactional public Map<String,Object> saveProfile(long userId,CurrentUser actor,String title,String location,int capacity,String status){verifyUser(userId,actor);if(capacity<1||capacity>80)throw new IllegalArgumentException("\u6bcf\u5468\u4ea7\u80fd\u5fc5\u987b\u5728 1-80 \u5c0f\u65f6\u4e4b\u95f4");Integer count=jdbc.queryForObject("select count(*) from engineer_profile where user_id=?",Integer.class,userId);if(count!=null&&count>0)jdbc.update("update engineer_profile set job_title=?,location=?,weekly_capacity_hours=?,resource_status=?,updated_at=current_timestamp where user_id=?",title,location,capacity,status,userId);else jdbc.update("insert into engineer_profile(user_id,organization_id,job_title,location,weekly_capacity_hours,resource_status) values (?,?,?,?,?,?)",userId,actor.getOrganizationId(),title,location,capacity,status);return team(actor,null).stream().filter(v->((Number)v.get("userId")).longValue()==userId).findFirst().get();}

  public List<Map<String,Object>> skillCatalog(CurrentUser user){return jdbc.queryForList("select id,code,name,category,status from skill_catalog where organization_id=? order by category,name",user.getOrganizationId());}
  @Transactional public Map<String,Object> createSkill(CurrentUser user,String code,String name,String category){if(blank(code)||blank(name)||blank(category))throw new IllegalArgumentException("\u6280\u80fd\u7f16\u7801\u3001\u540d\u79f0\u548c\u5206\u7c7b\u4e0d\u80fd\u4e3a\u7a7a");try{jdbc.update("insert into skill_catalog(organization_id,code,name,category) values (?,?,?,?)",user.getOrganizationId(),code,name,category);}catch(DuplicateKeyException duplicate){throw new ConflictException("\u6280\u80fd\u7f16\u7801\u5df2\u5b58\u5728");}return jdbc.queryForMap("select id,code,name,category,status from skill_catalog where organization_id=? and code=?",user.getOrganizationId(),code);}
  @Transactional public Map<String,Object> saveSkill(long userId,long skillId,int proficiency,boolean certified,int experienceMonths,CurrentUser actor){verifyUser(userId,actor);if(proficiency<1||proficiency>5)throw new IllegalArgumentException("\u719f\u7ec3\u5ea6\u5fc5\u987b\u5728 1-5 \u4e4b\u95f4");Integer exists=jdbc.queryForObject("select count(*) from skill_catalog where id=? and organization_id=?",Integer.class,skillId,actor.getOrganizationId());if(exists==null||exists==0)throw new NotFoundException("\u6280\u80fd\u4e0d\u5b58\u5728");Integer count=jdbc.queryForObject("select count(*) from engineer_skill where user_id=? and skill_id=?",Integer.class,userId,skillId);if(count!=null&&count>0)jdbc.update("update engineer_skill set proficiency=?,certified=?,experience_months=?,updated_at=current_timestamp where user_id=? and skill_id=?",proficiency,certified,experienceMonths,userId,skillId);else jdbc.update("insert into engineer_skill(user_id,skill_id,proficiency,certified,experience_months) values (?,?,?,?,?)",userId,skillId,proficiency,certified,experienceMonths);return skills(userId,actor).stream().filter(v->((Number)v.get("id")).longValue()==skillId).findFirst().get();}

  @Transactional public Map<String,Object> assign(CurrentUser actor,long userId,long projectId,String role,LocalDate start,LocalDate end,int allocation){verifyUser(userId,actor);verifyProject(projectId,actor);validateAssignment(role,start,end,allocation);Map<String,Object> values=new LinkedHashMap<String,Object>();values.put("organization_id",actor.getOrganizationId());values.put("user_id",userId);values.put("project_id",projectId);values.put("assignment_role",role);values.put("start_date",start);values.put("end_date",end);values.put("allocation_percent",allocation);values.put("created_by",actor.getId());long id=assignmentInsert.executeAndReturnKey(values).longValue();return assignment(id,actor);}
  @Transactional public Map<String,Object> updateAssignment(long id,CurrentUser actor,long userId,long projectId,String role,LocalDate start,LocalDate end,int allocation,String status,long version){verifyUser(userId,actor);verifyProject(projectId,actor);validateAssignment(role,start,end,allocation);int changed=jdbc.update("update resource_assignment set user_id=?,project_id=?,assignment_role=?,start_date=?,end_date=?,allocation_percent=?,status=?,updated_at=current_timestamp,version=version+1 where id=? and organization_id=? and version=?",userId,projectId,role,start,end,allocation,status,id,actor.getOrganizationId(),version);if(changed==0)throw new ConflictException("\u8d44\u6e90\u5206\u914d\u5df2\u66f4\u65b0\uff0c\u8bf7\u5237\u65b0\u540e\u91cd\u8bd5");return assignment(id,actor);}
  public List<Map<String,Object>> assignments(CurrentUser user,Long projectId,Long userId){String sql=assignmentSql()+" where a.organization_id=?";List<Object> args=new ArrayList<Object>();args.add(user.getOrganizationId());if(projectId!=null){sql+=" and a.project_id=?";args.add(projectId);}if(userId!=null){sql+=" and a.user_id=?";args.add(userId);}sql+=" order by a.start_date,a.id";return jdbc.query(sql,(row,index)->assignmentRow(row),args.toArray());}

  public List<Map<String,Object>> load(CurrentUser user,LocalDate from,LocalDate to){
    validateRange(from,to);
    Map<Long,List<Map<String,Object>>> byUser=assignmentsByUser(user,from,to);
    String sql="select u.id,u.display_name,coalesce(p.job_title,'\u5f85\u5b8c\u5584') job_title,coalesce(p.weekly_capacity_hours,40) capacity from app_user u left join engineer_profile p on p.user_id=u.id where u.organization_id=? and u.status='ACTIVE'";
    List<Map<String,Object>> values=jdbc.query(sql,(row,index)->{Map<String,Object> value=new LinkedHashMap<String,Object>();long userId=row.getLong("id");int allocation=peakAllocation(byUser.get(userId),from,to);value.put("userId",userId);value.put("displayName",row.getString("display_name"));value.put("jobTitle",row.getString("job_title"));value.put("weeklyCapacityHours",row.getInt("capacity"));value.put("allocationPercent",allocation);value.put("availablePercent",Math.max(0,100-allocation));value.put("loadStatus",allocation>100?"OVERLOAD":allocation>=80?"HIGH":allocation>=40?"BALANCED":"AVAILABLE");return value;},user.getOrganizationId());
    values.sort(Comparator.<Map<String,Object>,Integer>comparing(value->((Number)value.get("allocationPercent")).intValue()).reversed().thenComparing(value->String.valueOf(value.get("displayName"))));
    return values;
  }

  public List<Map<String,Object>> conflicts(CurrentUser user,LocalDate from,LocalDate to){
    validateRange(from,to);
    List<Map<String,Object>> conflicts=new ArrayList<Map<String,Object>>();
    for(Map.Entry<Long,List<Map<String,Object>>> entry:assignmentsByUser(user,from,to).entrySet()){
      List<Map<String,Object>> assignments=entry.getValue();
      TreeSet<LocalDate> boundaries=new TreeSet<LocalDate>();
      boundaries.add(from);
      for(Map<String,Object> assignment:assignments){LocalDate start=(LocalDate)assignment.get("startDate");LocalDate end=(LocalDate)assignment.get("endDate");boundaries.add(start.isBefore(from)?from:start);if(end.isBefore(to))boundaries.add(end.plusDays(1));}
      List<LocalDate> dates=new ArrayList<LocalDate>(boundaries);
      Map<String,Object> conflict=null;Map<Long,Map<String,Object>> involved=null;
      for(int i=0;i<dates.size();i++){
        LocalDate start=dates.get(i);LocalDate end=i+1<dates.size()?dates.get(i+1).minusDays(1):to;
        List<Map<String,Object>> active=new ArrayList<Map<String,Object>>();int allocation=0;
        for(Map<String,Object> assignment:assignments){if(!((LocalDate)assignment.get("startDate")).isAfter(start)&&!((LocalDate)assignment.get("endDate")).isBefore(start)){active.add(assignment);allocation+=((Number)assignment.get("allocationPercent")).intValue();}}
        if(allocation>100){if(conflict==null){conflict=new LinkedHashMap<String,Object>();involved=new LinkedHashMap<Long,Map<String,Object>>();conflict.put("userId",entry.getKey());conflict.put("displayName",assignments.get(0).get("displayName"));conflict.put("startDate",start);conflict.put("peakAllocationPercent",allocation);}conflict.put("endDate",end);conflict.put("peakAllocationPercent",Math.max(((Number)conflict.get("peakAllocationPercent")).intValue(),allocation));for(Map<String,Object> assignment:active)involved.put(((Number)assignment.get("id")).longValue(),assignment);}
        else if(conflict!=null){conflict.put("assignments",new ArrayList<Map<String,Object>>(involved.values()));conflicts.add(conflict);conflict=null;involved=null;}
      }
      if(conflict!=null){conflict.put("assignments",new ArrayList<Map<String,Object>>(involved.values()));conflicts.add(conflict);}
    }
    return conflicts;
  }

  private List<Map<String,Object>> skills(long userId,CurrentUser actor){return jdbc.query("select s.id,s.code,s.name,s.category,e.proficiency,e.certified,e.experience_months from engineer_skill e join skill_catalog s on s.id=e.skill_id where e.user_id=? and s.organization_id=? order by e.proficiency desc,s.name",(row,index)->{Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("id",row.getLong("id"));value.put("code",row.getString("code"));value.put("name",row.getString("name"));value.put("category",row.getString("category"));value.put("proficiency",row.getInt("proficiency"));value.put("certified",row.getBoolean("certified"));value.put("experienceMonths",row.getInt("experience_months"));return value;},userId,actor.getOrganizationId());}
  private Map<String,Object> assignment(long id,CurrentUser actor){List<Map<String,Object>> values=jdbc.query(assignmentSql()+" where a.id=? and a.organization_id=?",(row,index)->assignmentRow(row),id,actor.getOrganizationId());if(values.isEmpty())throw new NotFoundException("\u8d44\u6e90\u5206\u914d\u4e0d\u5b58\u5728");return values.get(0);}
  private Map<Long,List<Map<String,Object>>> assignmentsByUser(CurrentUser user,LocalDate from,LocalDate to){List<Map<String,Object>> values=jdbc.query(assignmentSql()+" where a.organization_id=? and a.status='ACTIVE' and a.start_date<=? and a.end_date>=? order by u.display_name,a.user_id,a.start_date,a.id",(row,index)->assignmentRow(row),user.getOrganizationId(),to,from);Map<Long,List<Map<String,Object>>> grouped=new LinkedHashMap<Long,List<Map<String,Object>>>();for(Map<String,Object> value:values){long userId=((Number)value.get("userId")).longValue();if(!grouped.containsKey(userId))grouped.put(userId,new ArrayList<Map<String,Object>>());grouped.get(userId).add(value);}return grouped;}
  private int peakAllocation(List<Map<String,Object>> assignments,LocalDate from,LocalDate to){if(assignments==null)return 0;TreeMap<LocalDate,Integer> changes=new TreeMap<LocalDate,Integer>();for(Map<String,Object> assignment:assignments){LocalDate start=(LocalDate)assignment.get("startDate");LocalDate end=(LocalDate)assignment.get("endDate");start=start.isBefore(from)?from:start;end=end.isAfter(to)?to:end;int allocation=((Number)assignment.get("allocationPercent")).intValue();changes.put(start,changes.containsKey(start)?changes.get(start)+allocation:allocation);if(end.isBefore(to)){LocalDate after=end.plusDays(1);changes.put(after,changes.containsKey(after)?changes.get(after)-allocation:-allocation);}}int current=0,peak=0;for(Integer change:changes.values()){current+=change;peak=Math.max(peak,current);}return peak;}
  private String assignmentSql(){return "select a.*,u.display_name,p.code project_code,p.name project_name from resource_assignment a join app_user u on u.id=a.user_id join delivery_project p on p.id=a.project_id";}
  private Map<String,Object> assignmentRow(java.sql.ResultSet row)throws java.sql.SQLException{Map<String,Object> value=new LinkedHashMap<String,Object>();value.put("id",row.getLong("id"));value.put("userId",row.getLong("user_id"));value.put("displayName",row.getString("display_name"));value.put("projectId",row.getLong("project_id"));value.put("projectCode",row.getString("project_code"));value.put("projectName",row.getString("project_name"));value.put("role",row.getString("assignment_role"));value.put("startDate",row.getDate("start_date").toLocalDate());value.put("endDate",row.getDate("end_date").toLocalDate());value.put("allocationPercent",row.getInt("allocation_percent"));value.put("status",row.getString("status"));value.put("version",row.getLong("version"));return value;}
  private void verifyUser(long id,CurrentUser actor){Integer count=jdbc.queryForObject("select count(*) from app_user where id=? and organization_id=? and status='ACTIVE'",Integer.class,id,actor.getOrganizationId());if(count==null||count==0)throw new NotFoundException("\u4eba\u5458\u4e0d\u5b58\u5728");}
  private void verifyProject(long id,CurrentUser actor){Integer count=jdbc.queryForObject("select count(*) from delivery_project where id=? and organization_id=?",Integer.class,id,actor.getOrganizationId());if(count==null||count==0)throw new NotFoundException("\u9879\u76ee\u4e0d\u5b58\u5728");}
  private void validateAssignment(String role,LocalDate start,LocalDate end,int allocation){if(blank(role))throw new IllegalArgumentException("\u9879\u76ee\u89d2\u8272\u4e0d\u80fd\u4e3a\u7a7a");validateRange(start,end);if(allocation<1||allocation>100)throw new IllegalArgumentException("\u6295\u5165\u6bd4\u4f8b\u5fc5\u987b\u5728 1-100% \u4e4b\u95f4");}
  private void validateRange(LocalDate from,LocalDate to){if(from==null||to==null||to.isBefore(from))throw new IllegalArgumentException("\u65e5\u671f\u8303\u56f4\u65e0\u6548");}
  private boolean blank(String value){return value==null||value.trim().isEmpty();}
}
