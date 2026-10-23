package com.bluelinge.dispems.workstation;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotNull;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workstations")
public class WorkstationController {
  private final JdbcTemplate jdbc; private final ObjectMapper json;
  public WorkstationController(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }
  @GetMapping("/options")
  public ApiResponse<List<Map<String, Object>>> options() {
    return ApiResponse.ok(list());
  }
  @GetMapping
  public ApiResponse<List<Map<String, Object>>> all() {
    AuthPrincipal.required(); return ApiResponse.ok(list());
  }
  @PostMapping
  @Transactional
  public ApiResponse<Map<String, Object>> create(@Valid @RequestBody CreateRequest request) {
    AuthPrincipal.requireAdmin();
    Integer next = nextStationNo();
    LocalDateTime now = TimeProvider.now();
    jdbc.update("INSERT INTO workstation(station_no,name,capture_mode,is_default,is_deleted,row_version,created_at,updated_at) VALUES(?,?,?,?,?,?,?,?)",
        next, request.name == null || request.name.trim().isEmpty() ? "工位" + next : request.name.trim(), request.captureMode == null ? 1 : request.captureMode,
        0, 0, 1, now, now);
    Long id = jdbc.queryForObject("SELECT id FROM workstation WHERE station_no=? AND is_deleted=0", Long.class, next);
    return ApiResponse.ok(find(id));
  }
  @PatchMapping("/{id}")
  public ApiResponse<Map<String, Object>> update(@PathVariable Long id, @Valid @RequestBody UpdateRequest request) {
    AuthPrincipal.requireAdmin();
    Map<String, Object> current = find(id);
    if (((Number) current.get("rowVersion")).intValue() != request.expectedVersion) throw AppException.conflict("工位已被更新，请刷新后重试");
    String name = request.name == null || request.name.trim().isEmpty() ? (String) current.get("name") : request.name.trim();
    int affected = jdbc.update("UPDATE workstation SET name=?,capture_mode=?,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=? AND is_deleted=0",
        name, request.captureMode, TimeProvider.now(), id, request.expectedVersion);
    if (affected != 1) throw AppException.conflict("工位已被更新，请刷新后重试");
    return ApiResponse.ok(find(id));
  }
  @DeleteMapping("/{id}")
  @Transactional
  public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) {
    AuthPrincipal.requireAdmin();
    Map<String, Object> current = find(id);
    if (((Number) current.get("stationNo")).intValue() == 1) throw AppException.invalid("默认工位不能删除");
    if (((Number) current.get("rowVersion")).intValue() != expectedVersion) throw AppException.conflict("工位已被更新，请刷新后重试");
    if (hasRunningTaskReference(id)) throw AppException.conflict("有在制任务的冻结工艺引用该工位，完成任务后才能删除");
    Long defaultId = jdbc.queryForObject("SELECT id FROM workstation WHERE station_no=1 AND is_deleted=0", Long.class);
    if (defaultId == null) throw AppException.invalid("默认工位不存在，无法删除");
    reassignProcessSteps(id, defaultId);
    int affected = jdbc.update("UPDATE workstation SET station_no=?,is_deleted=1,row_version=row_version+1,updated_at=? WHERE id=? AND row_version=?",
        retiredStationNo(id), TimeProvider.now(), id, expectedVersion);
    if (affected != 1) throw AppException.conflict("工位已被更新，请刷新后重试");
    return ApiResponse.ok(null);
  }
  private Integer nextStationNo() {
    List<Integer> numbers = jdbc.query("SELECT station_no FROM workstation WHERE is_deleted=0 ORDER BY station_no FOR UPDATE", (rs, index) -> rs.getInt(1));
    int expected = 1;
    for (Integer number : numbers) {
      if (number.intValue() == expected) expected += 1;
      else if (number.intValue() > expected) break;
    }
    return expected;
  }
  private long retiredStationNo(Long id) { return 2000000000L + id.longValue(); }
  private boolean hasRunningTaskReference(Long stationId) {
    List<String> snapshots = jdbc.query("SELECT process_snapshot_json FROM assembly_task WHERE status_code=0", (rs, index) -> rs.getString(1));
    for (String snapshot : snapshots) if (containsStation(readSteps(snapshot, "任务工艺快照"), stationId)) return true;
    return false;
  }
  private void reassignProcessSteps(Long sourceStationId, Long defaultStationId) {
    List<Map<String, Object>> processes = jdbc.query("SELECT id,steps_json FROM process_definition WHERE is_deleted=0", (rs, index) -> {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", rs.getLong("id")); item.put("steps", rs.getString("steps_json")); return item;
    });
    LocalDateTime now = TimeProvider.now();
    for (Map<String, Object> process : processes) {
      List<Map<String, Object>> steps = readSteps((String) process.get("steps"), "工艺步骤");
      boolean changed = false;
      for (Map<String, Object> step : steps) if (sameStation(step.get("stationId"), sourceStationId)) {
        step.put("stationId", defaultStationId); changed = true;
      }
      if (changed) jdbc.update("UPDATE process_definition SET steps_json=?,row_version=row_version+1,updated_at=? WHERE id=? AND is_deleted=0", writeSteps(steps), now, process.get("id"));
    }
  }
  private boolean containsStation(List<Map<String, Object>> steps, Long stationId) {
    for (Map<String, Object> step : steps) if (sameStation(step.get("stationId"), stationId)) return true;
    return false;
  }
  private boolean sameStation(Object value, Long stationId) {
    if (value instanceof Number) return ((Number) value).longValue() == stationId.longValue();
    try { return Long.parseLong(String.valueOf(value)) == stationId.longValue(); } catch (Exception ignored) { return false; }
  }
  private List<Map<String, Object>> readSteps(String source, String label) {
    try { return json.readValue(source, new TypeReference<List<Map<String, Object>>>() {}); }
    catch (Exception e) { throw AppException.invalid(label + "数据损坏"); }
  }
  private String writeSteps(List<Map<String, Object>> steps) {
    try { return json.writeValueAsString(steps == null ? new ArrayList<Object>() : steps); }
    catch (Exception e) { throw AppException.invalid("工艺步骤数据格式不合法"); }
  }
  private List<Map<String, Object>> list() {
    return jdbc.query("SELECT id,station_no,name,capture_mode,row_version FROM workstation WHERE is_deleted=0 ORDER BY station_no,id", (rs, index) -> {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", String.valueOf(rs.getLong("id"))); item.put("stationNo", rs.getInt("station_no"));
      item.put("name", rs.getString("name")); item.put("captureMode", rs.getInt("capture_mode")); item.put("rowVersion", rs.getInt("row_version"));
      return item;
    });
  }
  private Map<String, Object> find(Long id) {
    List<Map<String, Object>> result = jdbc.query("SELECT id,station_no,name,capture_mode,row_version FROM workstation WHERE id=? AND is_deleted=0", new Object[] { id }, (rs, index) -> {
      Map<String, Object> item = new LinkedHashMap<String, Object>();
      item.put("id", String.valueOf(rs.getLong("id"))); item.put("stationNo", rs.getInt("station_no")); item.put("name", rs.getString("name"));
      item.put("captureMode", rs.getInt("capture_mode")); item.put("rowVersion", rs.getInt("row_version")); return item;
    });
    if (result.isEmpty()) throw AppException.notFound("工位不存在");
    return result.get(0);
  }
  public static class CreateRequest { public String name; public Integer captureMode; }
  public static class UpdateRequest { public String name; @NotNull public Integer captureMode; @NotNull public Integer expectedVersion; }
}
