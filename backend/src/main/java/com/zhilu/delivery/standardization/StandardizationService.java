package com.zhilu.delivery.standardization;

import com.zhilu.delivery.common.error.ConflictException;
import com.zhilu.delivery.common.error.NotFoundException;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StandardizationService {
  private static final List<String> DEBT_STATES = Arrays.asList("CANDIDATE", "PENDING", "INCLUDED", "VERIFYING", "CLOSED");
  private final JdbcTemplate jdbc;
  public StandardizationService(JdbcTemplate jdbc) { this.jdbc=jdbc; }

  public List<Map<String,Object>> baselines(Long productVersionId) {
    String sql="select b.*,p.name product_name,v.version_name,u.display_name owner_name from product_baseline b join product_version v on v.id=b.product_version_id join product p on p.id=v.product_id left join app_user u on u.id=b.owner_user_id";
    Object[] args=new Object[0]; if(productVersionId!=null){sql+=" where b.product_version_id=?";args=new Object[]{productVersionId};} sql+=" order by b.dimension,b.capability_code";
    return jdbc.query(sql,(row,index)->baseline(row),args);
  }

  @Transactional public Map<String,Object> saveBaseline(Long id,long productVersionId,String code,String name,String dimension,String scope,String options,String extensions,Long owner,long version){
    if(blank(code)||blank(name)||blank(scope))throw new IllegalArgumentException("能力编码、名称和范围不能为空");
    if(!Arrays.asList("FUNCTION","CONFIGURATION","EXTENSION").contains(dimension))throw new IllegalArgumentException("能力维度不受支持");
    if(id==null){jdbc.update("insert into product_baseline(product_version_id,capability_code,capability_name,dimension,scope_description,configuration_options,extension_points,owner_user_id) values (?,?,?,?,?,?,?,?)",productVersionId,code,name,dimension,scope,options,extensions,owner);id=jdbc.queryForObject("select id from product_baseline where product_version_id=? and capability_code=?",Long.class,productVersionId,code);}
    else {int changed=jdbc.update("update product_baseline set capability_code=?,capability_name=?,dimension=?,scope_description=?,configuration_options=?,extension_points=?,owner_user_id=?,updated_at=current_timestamp,version=version+1 where id=? and version=?",code,name,dimension,scope,options,extensions,owner,id,version);if(changed==0)throw new ConflictException("能力卡已被更新，请刷新后重试");}
    final long baselineId=id;
    return baselines(productVersionId).stream().filter(item->((Number)item.get("id")).longValue()==baselineId).findFirst().orElseThrow(()->new NotFoundException("能力卡不存在"));
  }

  @Transactional public Map<String,Object> assess(long productVersionId,long actorUserId){
    long total=count("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id join delivery_project p on p.id=r.project_id where p.product_version_id=? and r.status='CONFIRMED'",productVersionId);
    long l0=count("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id join delivery_project p on p.id=r.project_id where p.product_version_id=? and r.status='CONFIRMED' and d.confirmed_level='L0'",productVersionId);
    long l1=count("select count(*) from custom_dev_task t join delivery_project p on p.id=t.project_id where p.product_version_id=?",productVersionId);
    long reused=count("select count(*) from custom_dev_task t join delivery_project p on p.id=t.project_id where p.product_version_id=? and t.extension_point in (select t2.extension_point from custom_dev_task t2 join delivery_project p2 on p2.id=t2.project_id where p2.product_version_id=? and t2.extension_point is not null group by t2.extension_point having count(distinct t2.project_id)>=2)",productVersionId,productVersionId);
    long baseline=count("select count(*) from product_baseline where product_version_id=? and status='ACTIVE'",productVersionId);
    long documented=count("select count(*) from product_baseline where product_version_id=? and status='ACTIVE' and length(scope_description)>=10",productVersionId);
    long extended=count("select count(*) from product_baseline where product_version_id=? and status='ACTIVE' and extension_points is not null and length(extension_points)>0",productVersionId);
    long projects=count("select count(*) from delivery_project where product_version_id=?",productVersionId);
    long healthy=count("select count(*) from delivery_project where product_version_id=? and risk_level='GREEN'",productVersionId);
    int coverage=percent(l0,total),reuse=percent(reused,l1),documentation=percent(documented,baseline),readiness=percent(extended,baseline),stability=percent(healthy,projects);
    int score=(int)Math.round(coverage*.30+reuse*.40+documentation*.10+readiness*.10+stability*.10);
    String period=YearMonth.now().toString();
    Integer exists=jdbc.queryForObject("select count(*) from maturity_assessment where product_version_id=? and period_key=?",Integer.class,productVersionId,period);
    if(exists!=null&&exists>0)jdbc.update("update maturity_assessment set standard_coverage=?,reuse_rate=?,documentation_score=?,extension_readiness=?,delivery_stability=?,maturity_score=?,assessed_by=?,assessed_at=current_timestamp where product_version_id=? and period_key=?",coverage,reuse,documentation,readiness,stability,score,actorUserId,productVersionId,period);
    else jdbc.update("insert into maturity_assessment(product_version_id,period_key,standard_coverage,reuse_rate,documentation_score,extension_readiness,delivery_stability,maturity_score,assessed_by) values (?,?,?,?,?,?,?,?,?)",productVersionId,period,coverage,reuse,documentation,readiness,stability,score,actorUserId);
    Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("productVersionId",productVersionId);result.put("period",period);result.put("standardCoverage",coverage);result.put("reuseRate",reuse);result.put("documentationScore",documentation);result.put("extensionReadiness",readiness);result.put("deliveryStability",stability);result.put("maturityScore",score);return result;
  }

  public List<Map<String,Object>> deviations(long productVersionId){
    String sql="select p.id,p.code,p.name,count(d.id) total_count,sum(case when d.confirmed_level='L0' then 1 else 0 end) l0_count,sum(case when d.confirmed_level='L1' then 1 else 0 end) l1_count,sum(case when d.confirmed_level='L2' then 1 else 0 end) l2_count from delivery_project p left join requirement_item r on r.project_id=p.id and r.status='CONFIRMED' left join classification_decision d on d.requirement_id=r.id where p.product_version_id=? group by p.id,p.code,p.name order by p.code";
    return jdbc.query(sql,(row,index)->{Map<String,Object> value=new LinkedHashMap<String,Object>();long total=row.getLong("total_count"),l1=row.getLong("l1_count"),l2=row.getLong("l2_count");value.put("projectId",row.getLong("id"));value.put("projectCode",row.getString("code"));value.put("projectName",row.getString("name"));value.put("total",total);value.put("l0",row.getLong("l0_count"));value.put("l1",l1);value.put("l2",l2);value.put("deviationRate",percent(l1+l2,total));return value;},productVersionId);
  }

  @Transactional public List<Map<String,Object>> evaluateDebts(long productVersionId,long actorUserId){
    List<Map<String,Object>> patterns=jdbc.queryForList("select t.extension_point pattern_key,min(t.title) title,count(*) occurrence_count,count(distinct t.project_id) distinct_projects from custom_dev_task t join delivery_project p on p.id=t.project_id where p.product_version_id=? and t.extension_point is not null group by t.extension_point having count(distinct t.project_id)>=5",productVersionId);
    for(Map<String,Object> pattern:patterns){try{jdbc.update("insert into standardization_debt(product_version_id,pattern_key,title,occurrence_count,distinct_projects) values (?,?,?,?,?)",productVersionId,pattern.get("pattern_key"),pattern.get("title"),pattern.get("occurrence_count"),pattern.get("distinct_projects"));}catch(DuplicateKeyException duplicate){jdbc.update("update standardization_debt set title=?,occurrence_count=?,distinct_projects=?,updated_at=current_timestamp,version=version+1 where product_version_id=? and pattern_key=?",pattern.get("title"),pattern.get("occurrence_count"),pattern.get("distinct_projects"),productVersionId,pattern.get("pattern_key"));}}
    return debts(productVersionId);
  }
  public List<Map<String,Object>> debts(Long productVersionId){String sql="select d.*,p.name product_name,v.version_name,u.display_name owner_name from standardization_debt d join product_version v on v.id=d.product_version_id join product p on p.id=v.product_id left join app_user u on u.id=d.owner_user_id";Object[] args=new Object[0];if(productVersionId!=null){sql+=" where d.product_version_id=?";args=new Object[]{productVersionId};}sql+=" order by case d.status when 'CANDIDATE' then 1 when 'PENDING' then 2 when 'INCLUDED' then 3 when 'VERIFYING' then 4 else 5 end,d.distinct_projects desc";return jdbc.query(sql,(row,index)->debt(row),args);}
  @Transactional public Map<String,Object> transitionDebt(long id,String target,String note,long actorUserId){List<Map<String,Object>> values=debts(null);Map<String,Object> debt=values.stream().filter(item->((Number)item.get("id")).longValue()==id).findFirst().orElseThrow(()->new NotFoundException("标准化债务不存在"));String current=String.valueOf(debt.get("status"));int from=DEBT_STATES.indexOf(current),to=DEBT_STATES.indexOf(target);if(to!=from+1)throw new ConflictException("债务只能按候选、待评审、已纳入、验证中、已关闭顺序推进");if("CLOSED".equals(target)&&blank(note))throw new IllegalArgumentException("关闭债务必须填写验证结论");jdbc.update("update standardization_debt set status=?,verification_note=?,updated_at=current_timestamp,version=version+1 where id=?",target,note,id);return debts(null).stream().filter(item->((Number)item.get("id")).longValue()==id).findFirst().get();}

  public Map<String,Object> costs(long productVersionId){Map<String,Object> totals=jdbc.queryForMap("select coalesce(sum(t.estimated_person_days),0) estimated_days,coalesce(sum(t.actual_person_days),0) actual_days,coalesce(sum(t.estimated_cost),0) estimated_cost,coalesce(sum(t.actual_cost),0) actual_cost from custom_dev_task t join delivery_project p on p.id=t.project_id where p.product_version_id=?",productVersionId);Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("estimatedPersonDays",totals.get("estimated_days"));result.put("actualPersonDays",totals.get("actual_days"));result.put("estimatedCost",totals.get("estimated_cost"));result.put("actualCost",totals.get("actual_cost"));result.put("byExtensionPoint",jdbc.queryForList("select coalesce(t.extension_point,'未归类') extension_point,count(*) task_count,coalesce(sum(t.actual_person_days),0) person_days,coalesce(sum(t.actual_cost),0) amount from custom_dev_task t join delivery_project p on p.id=t.project_id where p.product_version_id=? group by t.extension_point order by amount desc",productVersionId));return result;}
  @Transactional public Map<String,Object> flywheel(long productVersionId){long confirmed=count("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id join delivery_project p on p.id=r.project_id where p.product_version_id=?",productVersionId),l0=count("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id join delivery_project p on p.id=r.project_id where p.product_version_id=? and d.confirmed_level='L0'",productVersionId),l1=count("select count(*) from classification_decision d join requirement_item r on r.id=d.requirement_id join delivery_project p on p.id=r.project_id where p.product_version_id=? and d.confirmed_level='L1'",productVersionId),closed=count("select count(*) from standardization_debt where product_version_id=? and status='CLOSED'",productVersionId);int reuse=((Number)assess(productVersionId,actorForVersion(productVersionId)).get("reuseRate")).intValue();BigDecimal cost=number(costs(productVersionId).get("actualCost"));String period=YearMonth.now().toString();try{jdbc.update("insert into flywheel_metric(product_version_id,period_key,confirmed_requirements,l0_count,l1_count,reuse_rate,debt_closed_count,custom_cost) values (?,?,?,?,?,?,?,?)",productVersionId,period,confirmed,l0,l1,reuse,closed,cost);}catch(DuplicateKeyException duplicate){jdbc.update("update flywheel_metric set confirmed_requirements=?,l0_count=?,l1_count=?,reuse_rate=?,debt_closed_count=?,custom_cost=? where product_version_id=? and period_key=?",confirmed,l0,l1,reuse,closed,cost,productVersionId,period);}Map<String,Object> result=new LinkedHashMap<String,Object>();result.put("period",period);result.put("confirmedRequirements",confirmed);result.put("l0Count",l0);result.put("l1Count",l1);result.put("reuseRate",reuse);result.put("debtClosedCount",closed);result.put("customCost",cost);result.put("standardCoverage",percent(l0,confirmed));return result;}

  private long actorForVersion(long version){Long actor=jdbc.queryForObject("select p.manager_user_id from delivery_project p where p.product_version_id=? order by p.id limit 1",Long.class,version);if(actor==null)throw new IllegalStateException("产品版本尚无交付项目");return actor;}
  private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
  private int percent(long value,long total){return total==0?0:(int)Math.round(value*100d/total);}
  private BigDecimal number(Object value){return value instanceof BigDecimal?(BigDecimal)value:new BigDecimal(String.valueOf(value));}
  private boolean blank(String value){return value==null||value.trim().isEmpty();}
  private Map<String,Object> baseline(java.sql.ResultSet row)throws java.sql.SQLException{Map<String,Object> v=new LinkedHashMap<String,Object>();v.put("id",row.getLong("id"));v.put("productVersionId",row.getLong("product_version_id"));v.put("productName",row.getString("product_name"));v.put("versionName",row.getString("version_name"));v.put("capabilityCode",row.getString("capability_code"));v.put("capabilityName",row.getString("capability_name"));v.put("dimension",row.getString("dimension"));v.put("scopeDescription",row.getString("scope_description"));v.put("configurationOptions",row.getString("configuration_options"));v.put("extensionPoints",row.getString("extension_points"));v.put("status",row.getString("status"));v.put("ownerName",row.getString("owner_name"));v.put("version",row.getLong("version"));return v;}
  private Map<String,Object> debt(java.sql.ResultSet row)throws java.sql.SQLException{Map<String,Object> v=new LinkedHashMap<String,Object>();v.put("id",row.getLong("id"));v.put("productVersionId",row.getLong("product_version_id"));v.put("productName",row.getString("product_name"));v.put("versionName",row.getString("version_name"));v.put("patternKey",row.getString("pattern_key"));v.put("title",row.getString("title"));v.put("occurrenceCount",row.getInt("occurrence_count"));v.put("distinctProjects",row.getInt("distinct_projects"));v.put("status",row.getString("status"));v.put("ownerName",row.getString("owner_name"));v.put("targetVersion",row.getString("target_version"));v.put("verificationNote",row.getString("verification_note"));v.put("version",row.getLong("version"));return v;}
}
