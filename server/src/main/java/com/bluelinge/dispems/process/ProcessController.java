package com.bluelinge.dispems.process;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.auth.CurrentUser;
import com.bluelinge.dispems.common.ApiResponse;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/processes")
public class ProcessController {
  private final JdbcTemplate jdbc; private final ObjectMapper json;
  public ProcessController(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc=jdbc; this.json=json; }
  @GetMapping public ApiResponse<List<Map<String,Object>>> list() { AuthPrincipal.required(); return ApiResponse.ok(listRows()); }
  @PostMapping @Transactional public ApiResponse<Map<String,Object>> create(@Valid @RequestBody ProcessRequest request) {
    CurrentUser user=AuthPrincipal.required(); AuthPrincipal.requireAdmin(); LocalDateTime now=TimeProvider.now();
    List<Map<String,Object>> found=jdbc.query("SELECT * FROM process_definition WHERE model=?",(rs,n)->row(rs),request.model);
    if(found.isEmpty()) {
      List<Map<String,Object>> steps=normalizeSteps(request.steps);
      ProcessParameterValidator.validateAndNormalize(steps);
      jdbc.update("INSERT INTO process_definition(model,title,version_code,source_workbook,source_document,steps_json,projection_json,is_deleted,row_version,created_by,created_at,updated_at) VALUES(?,?,?,?,?,?,?,0,1,?,?,?)", request.model,request.title,blank(request.version,"v1"),blank(request.sourceWorkbook,""),blank(request.sourceDocument,""),write(steps),writeProjection(normalizeProjection(request.stepProjection,steps)),user.getId(),now,now);
      Long id=jdbc.queryForObject("SELECT LAST_INSERT_ID()",Long.class); return ApiResponse.ok(find(id));
    }
    Map<String,Object> existing=found.get(0); Long id=(Long)existing.get("idValue");
    List<Map<String,Object>> steps=normalizeSteps(request.steps);
    ProcessParameterValidator.validateAndNormalize(steps);
    jdbc.update("UPDATE process_definition SET title=?,version_code=?,source_workbook=?,source_document=?,steps_json=?,projection_json=?,is_deleted=0,row_version=row_version+1,updated_at=? WHERE id=?",request.title,blank(request.version,"v1"),blank(request.sourceWorkbook,""),blank(request.sourceDocument,""),write(steps),writeProjection(normalizeProjection(request.stepProjection,steps)),now,id);
    return ApiResponse.ok(find(id));
  }
  @DeleteMapping("/{id}") @Transactional public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) {
    AuthPrincipal.requireAdmin(); Map<String,Object> current=find(id);
    if (((Number)current.get("rowVersion")).intValue() != expectedVersion) throw AppException.conflict("工艺已被更新，请刷新后重试");
    int changed=jdbc.update("UPDATE process_definition SET is_deleted=1,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",TimeProvider.now(),id,expectedVersion);
    if (changed != 1) throw AppException.conflict("工艺已被更新，请刷新后重试");
    return ApiResponse.ok(null);
  }
  @PatchMapping("/{id}/steps/{stepNo}") @Transactional public ApiResponse<Map<String,Object>> updateStep(@PathVariable Long id,@PathVariable Integer stepNo,@Valid @RequestBody StepRequest request) {
    AuthPrincipal.requireAdmin(); Map<String,Object> current=find(id); if(((Number)current.get("rowVersion")).intValue()!=request.expectedVersion) throw AppException.conflict("工艺已被更新，请刷新后重试");
    List<Map<String,Object>> steps=(List<Map<String,Object>>)current.get("steps"); boolean updated=false;
    for(int i=0;i<steps.size();i++) if(((Number)steps.get(i).get("stepNo")).intValue()==stepNo.intValue()) { Map<String,Object> replacement=normalizeStep(request.step); replacement.put("stepNo",stepNo); ProcessParameterValidator.validateAndNormalize(Collections.singletonList(replacement)); steps.set(i,replacement); updated=true; break; }
    if(!updated) throw AppException.notFound("工艺步骤不存在");
    int changed=jdbc.update("UPDATE process_definition SET steps_json=?,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",write(steps),TimeProvider.now(),id,request.expectedVersion);
    if(changed!=1) throw AppException.conflict("工艺已被更新，请刷新后重试"); return ApiResponse.ok(find(id));
  }
  @PatchMapping("/{id}/projections/{stepNo}") @Transactional public ApiResponse<Map<String,Object>> updateProjection(@PathVariable Long id,@PathVariable Integer stepNo,@Valid @RequestBody ProjectionRequest request) {
    AuthPrincipal.requireAdmin(); Map<String,Object> current=find(id); if(((Number)current.get("rowVersion")).intValue()!=request.expectedVersion) throw AppException.conflict("工艺已被更新，请刷新后重试");
    List<Map<String,Object>> steps=(List<Map<String,Object>>)current.get("steps"); boolean exists=false;
    for(Map<String,Object> step:steps) if(((Number)step.get("stepNo")).intValue()==stepNo.intValue()) { exists=true; break; }
    if(!exists) throw AppException.notFound("工艺步骤不存在");
    Map<String,Object> projections=(Map<String,Object>)current.get("stepProjection");
    Map<String,Object> projection=new LinkedHashMap<String,Object>(); projection.put("templateId",request.templateId == null ? defaultTemplateId() : String.valueOf(request.templateId));
    projection.put("elements",request.elements == null ? new ArrayList<Object>() : request.elements); projection.put("inheritsTemplate",request.inheritsTemplate == null ? false : request.inheritsTemplate.booleanValue());
    projections.put(String.valueOf(stepNo),projection);
    int changed=jdbc.update("UPDATE process_definition SET projection_json=?,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",writeProjection(projections),TimeProvider.now(),id,request.expectedVersion);
    if(changed!=1) throw AppException.conflict("工艺已被更新，请刷新后重试"); return ApiResponse.ok(find(id));
  }
  private List<Map<String,Object>> listRows(){return jdbc.query("SELECT * FROM process_definition WHERE is_deleted=0 ORDER BY updated_at DESC,id DESC",(rs,n)->row(rs));}
  private Map<String,Object> find(Long id){List<Map<String,Object>> rows=jdbc.query("SELECT * FROM process_definition WHERE id=? AND is_deleted=0",(rs,n)->row(rs),id);if(rows.isEmpty())throw AppException.notFound("工艺不存在");return rows.get(0);}
  private Map<String,Object> row(java.sql.ResultSet rs)throws java.sql.SQLException {Map<String,Object> r=new LinkedHashMap<String,Object>();List<Map<String,Object>> steps=read(rs.getString("steps_json"));r.put("id",String.valueOf(rs.getLong("id")));r.put("idValue",rs.getLong("id"));r.put("model",rs.getString("model"));r.put("title",rs.getString("title"));r.put("version",rs.getString("version_code"));r.put("sourceWorkbook",rs.getString("source_workbook"));r.put("sourceDocument",rs.getString("source_document"));r.put("steps",steps);r.put("stepProjection",readProjection(rs.getString("projection_json"),steps));r.put("rowVersion",rs.getInt("row_version"));return r;}
  private String blank(String value,String fallback){return value==null||value.trim().isEmpty()?fallback:value.trim();}
  private String write(Object value){try{return json.writeValueAsString(value);}catch(Exception e){throw AppException.invalid("工艺数据格式不合法");}}
  private List<Map<String,Object>> read(String value){try{return normalizeSteps(json.readValue(value,new TypeReference<List<Map<String,Object>>>(){}));}catch(Exception e){throw AppException.invalid("工艺数据损坏");}}
  private List<Map<String,Object>> normalizeSteps(List<Map<String,Object>> steps){for(int index=0;index<steps.size();index++){Map<String,Object> step=steps.get(index);if(!(step.get("stepNo") instanceof Number))step.put("stepNo",index+1);normalizeStep(step);}return steps;}
  private Map<String,Object> normalizeStep(Map<String,Object> step){if(step.get("content")==null)step.put("content","");else step.put("content",String.valueOf(step.get("content")));if(!(step.get("parameters") instanceof List))step.put("parameters",new java.util.ArrayList<Object>());return step;}
  private Map<String,Object> readProjection(String value,List<Map<String,Object>> steps){
    if(value==null||value.trim().isEmpty()) return defaultProjection(steps);
    try { return normalizeProjection(json.readValue(value,new TypeReference<Map<String,Object>>(){}),steps); }
    catch(Exception e){ throw AppException.invalid("投影配置数据损坏"); }
  }
  private Map<String,Object> normalizeProjection(Map<String,Object> source,List<Map<String,Object>> steps){
    Map<String,Object> result=new LinkedHashMap<String,Object>(); String defaultId=defaultTemplateId();
    for(Map<String,Object> step:steps){ String key=String.valueOf(step.get("stepNo")); Object value=source==null?null:source.get(key); Map<String,Object> entry=value instanceof Map?(Map<String,Object>)value:new LinkedHashMap<String,Object>(); Map<String,Object> normalized=new LinkedHashMap<String,Object>();
      normalized.put("templateId",entry.get("templateId")==null?defaultId:String.valueOf(entry.get("templateId"))); normalized.put("elements",entry.get("elements") instanceof List?entry.get("elements"):new ArrayList<Object>()); normalized.put("inheritsTemplate",entry.get("inheritsTemplate")==null?true:Boolean.valueOf(String.valueOf(entry.get("inheritsTemplate")))); result.put(key,normalized); }
    return result;
  }
  private Map<String,Object> defaultProjection(List<Map<String,Object>> steps){ return normalizeProjection(Collections.<String,Object>emptyMap(),steps); }
  private String defaultTemplateId(){ List<Long> ids=jdbc.query("SELECT id FROM projection_template WHERE is_deleted=0 AND is_default=1 ORDER BY id LIMIT 1",(rs,n)->rs.getLong(1)); return ids.isEmpty()?"":String.valueOf(ids.get(0)); }
  private String writeProjection(Map<String,Object> value){ return write(value==null?Collections.<String,Object>emptyMap():value); }
  public static class ProcessRequest { @NotBlank public String model; @NotBlank public String title; public String version; public String sourceWorkbook; public String sourceDocument; @NotEmpty public List<Map<String,Object>> steps; public Map<String,Object> stepProjection; }
  public static class StepRequest { @NotNull public Integer expectedVersion; @NotNull public Map<String,Object> step; }
  public static class ProjectionRequest { @NotNull public Integer expectedVersion; public String templateId; public List<Map<String,Object>> elements; public Boolean inheritsTemplate; }
}
