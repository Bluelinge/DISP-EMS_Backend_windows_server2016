package com.bluelinge.dispems.projector;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.bluelinge.dispems.file.FileService;
import com.bluelinge.dispems.file.StoredFileEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/projector")
public class ProjectorController {
  private final JdbcTemplate jdbc;
  private final FileService files;
  private final ObjectMapper json;
  public ProjectorController(JdbcTemplate jdbc, FileService files, ObjectMapper json) {
    this.jdbc = jdbc; this.files = files; this.json = json;
  }
  @GetMapping("/icons")
  public ApiResponse<List<Map<String, Object>>> icons() {
    AuthPrincipal.required();
    return ApiResponse.ok(jdbc.query("SELECT id,name,file_id,source_code,is_built_in,row_version FROM projection_icon WHERE is_deleted=0 ORDER BY is_built_in DESC,name,id",
        (rs, index) -> {
          StoredFileEntity file = files.required(rs.getLong("file_id"));
          Map<String, Object> item = new LinkedHashMap<String, Object>();
          item.put("id", String.valueOf(rs.getLong("id"))); item.put("name", rs.getString("name"));
          item.put("fileId", String.valueOf(file.getId())); item.put("url", files.signedUrl(file));
          item.put("sourceCode", rs.getInt("source_code")); item.put("isBuiltIn", rs.getInt("is_built_in")); item.put("rowVersion", rs.getInt("row_version"));
          return item;
        }));
  }
  @PostMapping("/icons")
  public ApiResponse<Map<String, Object>> createIcon(@Valid @RequestBody IconRequest request) {
    AuthPrincipal.requireAdmin();
    StoredFileEntity file = files.required(request.fileId);
    if (file.getFileKind().intValue() != FileService.KIND_ICON) throw AppException.badRequest("图标必须关联 PNG 文件");
    String name = request.name.trim();
    List<Map<String, Object>> matches = jdbc.query("SELECT id,is_deleted FROM projection_icon WHERE name=?", new Object[] { name },
        (rs, index) -> { Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("id", rs.getLong("id")); item.put("isDeleted", rs.getInt("is_deleted")); return item; });
    if (!matches.isEmpty()) {
      Map<String, Object> existing = matches.get(0);
      if (((Number) existing.get("isDeleted")).intValue() == 0) throw AppException.conflict("投影图标名称已存在");
      Long id = ((Number) existing.get("id")).longValue();
      int restored = jdbc.update("UPDATE projection_icon SET file_id=?,source_code=1,is_built_in=0,is_deleted=0,row_version=row_version+1,updated_at=? WHERE id=? AND is_deleted=1",
          file.getId(), TimeProvider.now(), id);
      if (restored != 1) throw AppException.conflict("投影图标已更新，请刷新后重试");
      return ApiResponse.ok(icon(id));
    }
    LocalDateTime now = TimeProvider.now();
    jdbc.update("INSERT INTO projection_icon(name,file_id,source_code,is_built_in,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
        name, file.getId(), 1, 0, 0, 1, now, now);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    return ApiResponse.ok(icon(id));
  }
  @PutMapping("/icons/{id}")
  public ApiResponse<Map<String, Object>> replaceIcon(@PathVariable Long id, @Valid @RequestBody IconReplaceRequest request) {
    AuthPrincipal.requireAdmin();
    if (request.expectedVersion == null) throw AppException.badRequest("缺少 expectedVersion");
    StoredFileEntity file = files.required(request.fileId);
    if (file.getFileKind().intValue() != FileService.KIND_ICON) throw AppException.badRequest("图标必须关联 PNG 文件");
    List<Map<String, Object>> values = jdbc.query("SELECT is_built_in,row_version FROM projection_icon WHERE id=? AND is_deleted=0", new Object[] { id },
        (rs, index) -> { Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("isBuiltIn", rs.getInt("is_built_in")); item.put("rowVersion", rs.getInt("row_version")); return item; });
    if (values.isEmpty()) throw AppException.notFound("投影图标不存在");
    Map<String, Object> current = values.get(0);
    if (((Number) current.get("isBuiltIn")).intValue() == 1) throw AppException.invalid("内置图标不能替换");
    if (((Number) current.get("rowVersion")).intValue() != request.expectedVersion) throw AppException.conflict("图标已更新，请刷新后重试");
    int affected = jdbc.update("UPDATE projection_icon SET file_id=?,source_code=1,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",
        file.getId(), TimeProvider.now(), id, request.expectedVersion);
    if (affected != 1) throw AppException.conflict("图标已更新，请刷新后重试");
    return ApiResponse.ok(icon(id));
  }
  @DeleteMapping("/icons/{id}")
  public ApiResponse<Void> deleteIcon(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) {
    AuthPrincipal.requireAdmin();
    Map<String, Object> icon = icon(id);
    if (((Number) icon.get("isBuiltIn")).intValue() == 1) throw AppException.invalid("内置图标不能删除");
    if (((Number) icon.get("rowVersion")).intValue() != expectedVersion) throw AppException.conflict("图标已更新，请刷新后重试");
    int affected = jdbc.update("UPDATE projection_icon SET is_deleted=1,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=?",
        TimeProvider.now(), id, expectedVersion);
    if (affected != 1) throw AppException.conflict("图标已更新，请刷新后重试");
    return ApiResponse.ok(null);
  }
  @GetMapping("/templates")
  public ApiResponse<List<Map<String, Object>>> templates() {
    AuthPrincipal.required();
    return ApiResponse.ok(jdbc.query("SELECT id,name,description,is_default,width_px,height_px,elements_json,row_version FROM projection_template WHERE is_deleted=0 ORDER BY is_default DESC,name,id",
        (rs, index) -> templateMap(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getInt("is_default"),
            rs.getInt("width_px"), rs.getInt("height_px"), rs.getString("elements_json"), rs.getInt("row_version"))));
  }
  @GetMapping("/templates/{id}")
  public ApiResponse<Map<String, Object>> template(@PathVariable Long id) { AuthPrincipal.required(); return ApiResponse.ok(templateById(id)); }
  @PostMapping("/templates")
  public ApiResponse<Map<String, Object>> createTemplate(@Valid @RequestBody TemplateRequest request) {
    AuthPrincipal.requireAdmin();
    String name = request.name.trim();
    LocalDateTime now = TimeProvider.now();
    String elements = writeElements(request.elements);
    List<Map<String, Object>> matches = jdbc.query("SELECT id,is_deleted FROM projection_template WHERE name=?", new Object[] { name },
        (rs, index) -> { Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("id", rs.getLong("id")); item.put("isDeleted", rs.getInt("is_deleted")); return item; });
    if (!matches.isEmpty()) {
      Map<String, Object> existing = matches.get(0);
      if (((Number) existing.get("isDeleted")).intValue() == 0) throw AppException.conflict("投影模板名称已存在");
      Long id = ((Number) existing.get("id")).longValue();
      int restored = jdbc.update("UPDATE projection_template SET description=?,is_default=0,width_px=1920,height_px=1080,elements_json=?,is_deleted=0,row_version=row_version+1,updated_at=? WHERE id=? AND is_deleted=1",
          request.description == null ? "" : request.description.trim(), elements, now, id);
      if (restored != 1) throw AppException.conflict("投影模板已更新，请刷新后重试");
      return ApiResponse.ok(templateById(id));
    }
    jdbc.update("INSERT INTO projection_template(name,description,is_default,width_px,height_px,elements_json,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?,?)",
        name, request.description == null ? "" : request.description.trim(), 0, 1920, 1080, elements, 0, 1, now, now);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class); return ApiResponse.ok(templateById(id));
  }
  @PutMapping("/templates/{id}")
  public ApiResponse<Map<String, Object>> updateTemplate(@PathVariable Long id, @Valid @RequestBody TemplateRequest request) {
    AuthPrincipal.requireAdmin();
    if (request.expectedVersion == null) throw AppException.badRequest("缺少 expectedVersion");
    Map<String, Object> current = templateById(id);
    if (((Number) current.get("rowVersion")).intValue() != request.expectedVersion) throw AppException.conflict("模板已更新，请刷新后重试");
    int affected = jdbc.update("UPDATE projection_template SET name=?,description=?,elements_json=?,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",
        request.name.trim(), request.description == null ? "" : request.description.trim(), writeElements(request.elements), TimeProvider.now(), id, request.expectedVersion);
    if (affected != 1) throw AppException.conflict("模板已更新，请刷新后重试");
    return ApiResponse.ok(templateById(id));
  }
  @DeleteMapping("/templates/{id}")
  public ApiResponse<Void> deleteTemplate(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) {
    AuthPrincipal.requireAdmin();
    Map<String, Object> current = templateById(id);
    if (((Number) current.get("isDefault")).intValue() == 1) throw AppException.invalid("默认模板不可删除");
    if (((Number) current.get("rowVersion")).intValue() != expectedVersion) throw AppException.conflict("模板已更新，请刷新后重试");
    int affected = jdbc.update("UPDATE projection_template SET is_deleted=1,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=?",
        TimeProvider.now(), id, expectedVersion);
    if (affected != 1) throw AppException.conflict("模板已更新，请刷新后重试");
    return ApiResponse.ok(null);
  }
  private Map<String, Object> icon(Long id) {
    List<Map<String, Object>> values = jdbc.query("SELECT id,name,file_id,source_code,is_built_in,row_version FROM projection_icon WHERE id=? AND is_deleted=0", new Object[] { id },
        (rs, index) -> {
          StoredFileEntity file = files.required(rs.getLong("file_id"));
          Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("id", String.valueOf(rs.getLong("id")));
          item.put("name", rs.getString("name")); item.put("fileId", String.valueOf(file.getId())); item.put("url", files.signedUrl(file));
          item.put("sourceCode", rs.getInt("source_code")); item.put("isBuiltIn", rs.getInt("is_built_in")); item.put("rowVersion", rs.getInt("row_version")); return item;
        });
    if (values.isEmpty()) throw AppException.notFound("投影图标不存在"); return values.get(0);
  }
  private Map<String, Object> templateById(Long id) {
    List<Map<String, Object>> values = jdbc.query("SELECT id,name,description,is_default,width_px,height_px,elements_json,row_version FROM projection_template WHERE id=? AND is_deleted=0",
        new Object[] { id }, (rs, index) -> templateMap(rs.getLong("id"), rs.getString("name"), rs.getString("description"), rs.getInt("is_default"),
            rs.getInt("width_px"), rs.getInt("height_px"), rs.getString("elements_json"), rs.getInt("row_version")));
    if (values.isEmpty()) throw AppException.notFound("投影模板不存在"); return values.get(0);
  }
  private Map<String, Object> templateMap(Long id, String name, String description, int isDefault, int width, int height, String elements, int version) {
    Map<String, Object> item = new LinkedHashMap<String, Object>(); item.put("id", String.valueOf(id)); item.put("name", name);
    item.put("description", description); item.put("isDefault", isDefault); item.put("width", width); item.put("height", height); item.put("rowVersion", version);
    try { item.put("elements", json.readValue(elements, new TypeReference<List<Map<String, Object>>>() { })); }
    catch (Exception e) { throw new IllegalStateException("模板数据损坏", e); } return item;
  }
  private String writeElements(List<Map<String, Object>> elements) {
    try { return json.writeValueAsString(elements == null ? java.util.Collections.emptyList() : elements); }
    catch (Exception e) { throw AppException.badRequest("投影元素格式不正确"); }
  }
  public static class IconRequest { @NotBlank public String name; @NotNull public Long fileId; }
  public static class IconReplaceRequest { @NotNull public Long fileId; @NotNull public Integer expectedVersion; }
  public static class TemplateRequest { @NotBlank public String name; public String description; public List<Map<String, Object>> elements; public Integer expectedVersion; }
}
