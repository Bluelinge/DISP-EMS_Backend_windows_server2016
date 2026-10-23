package com.bluelinge.dispems.task;

import com.bluelinge.dispems.auth.CurrentUser;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.bluelinge.dispems.event.TaskEventPublisher;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaskService {
  private static final ZoneId STATISTICS_ZONE = ZoneId.of("Asia/Shanghai");
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final TaskEventPublisher events;
  public TaskService(JdbcTemplate jdbc, ObjectMapper json, TaskEventPublisher events) {
    this.jdbc = jdbc; this.json = json; this.events = events;
  }

  @Transactional
  public Map<String, Object> create(CurrentUser user, String productCode, String model, String steelNo, Object processSnapshot) {
    requireOperatorStation(user);
    List<Map<String, Object>> steps = steps(processSnapshot);
    if (steps.isEmpty()) throw AppException.invalid("工艺步骤不能为空");
    List<Map<String, Object>> active = jdbc.query("SELECT * FROM assembly_task WHERE steel_no=? AND status_code=0 ORDER BY started_at DESC LIMIT 1", (rs, n) -> row(rs), steelNo);
    if (!active.isEmpty()) {
      Map<String, Object> existing = active.get(0);
      if (!model.equals(existing.get("model"))) throw AppException.conflict("该钢印编号已有其他型号的进行中任务");
      if (!productCode.equals(existing.get("productCode"))) {
        // 手动录入工位使用钢印号重新识别产品时，产品编码可能会变更。
        // 保留原有在制任务和工艺快照，仅把该钢印号的当前产品编码重新绑定，
        // 避免产生两个同钢印号的并行任务；自动识别工位仍不能覆盖扫码绑定。
        if (!manualCaptureStation(user)) {
          throw AppException.conflict("该钢印编号已绑定其他产品编码的进行中任务，请在手动录入工位重新绑定");
        }
        Long taskId = (Long) existing.get("idValue");
        String previousProductCode = String.valueOf(existing.get("productCode"));
        LocalDateTime now = TimeProvider.now();
        jdbc.update("UPDATE assembly_task SET product_code=?,row_version=row_version+1 WHERE id=?", productCode, taskId);
        jdbc.update("INSERT INTO task_product_code_audit(task_id,previous_product_code,product_code,operator_id,workstation_id,changed_at) VALUES(?,?,?,?,?,?)",
            taskId, previousProductCode, productCode, user.getId(), user.getWorkstationId(), now);
        events.publish(taskId, "product-code-rebound", map("previousProductCode", previousProductCode, "productCode", productCode, "stationId", user.getWorkstationId()));
        return task(taskId);
      }
      // 同一产品在后续工位再次扫码不是重复建任务，而是恢复冻结快照继续交接。
      return detail(existing);
    }
    LocalDateTime now = TimeProvider.now();
    jdbc.update("INSERT INTO assembly_task(model,product_code,steel_no,process_snapshot_json,status_code,started_by,started_at,row_version) VALUES(?,?,?,?,0,?,?,1)",
        model, productCode, steelNo, write(steps), user.getId(), now);
    Long id = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    events.publish(id, "task-created", Collections.<String, Object>singletonMap("stationId", user.getWorkstationId()));
    return task(id);
  }

  @Transactional
  public Map<String, Object> recordStep(CurrentUser user, Long taskId, Integer stepNo, Map<String, Object> values) {
    requireOperatorStation(user); Map<String, Object> task = requiredTask(taskId);
    if (((Number) task.get("statusCode")).intValue() != 0) throw AppException.conflict("任务已经完成，不能继续录入");
    Map<String, Object> step = step(task, stepNo);
    Long stationId = number(step.get("stationId"));
    if (!stationId.equals(user.getWorkstationId())) throw AppException.forbidden("该步骤不属于当前登录工位");
    if (!previousStationsConfirmed(task, stationId)) throw AppException.conflict("请等待上一工位确认交接");
    LocalDateTime now = TimeProvider.now();
    jdbc.update("INSERT INTO task_step_log(task_id,step_no,station_id,operator_id,action_code,values_json,occurred_at,updated_at) VALUES(?,?,?,?,0,?,?,?) "
            + "ON DUPLICATE KEY UPDATE station_id=VALUES(station_id),operator_id=VALUES(operator_id),values_json=VALUES(values_json),updated_at=VALUES(updated_at)",
        taskId, stepNo, stationId, user.getId(), write(values == null ? Collections.emptyMap() : values), now, now);
    jdbc.update("UPDATE assembly_task SET row_version=row_version+1 WHERE id=?", taskId);
    events.publish(taskId, "step-saved", map("stepNo", stepNo, "stationId", stationId));
    return task(taskId);
  }

  @Transactional
  public Map<String, Object> confirmStation(CurrentUser user, Long taskId, Long stationId, Object items) {
    requireOperatorStation(user); if (!stationId.equals(user.getWorkstationId())) throw AppException.forbidden("只能确认当前登录工位");
    Map<String, Object> task = requiredTask(taskId);
    if (((Number) task.get("statusCode")).intValue() != 0) throw AppException.conflict("任务已经完成");
    if (!previousStationsConfirmed(task, stationId)) throw AppException.conflict("请等待上一工位确认交接");
    for (Map<String, Object> step : steps(task.get("processSnapshot"))) {
      if (stationId.equals(number(step.get("stationId"))) && !hasLog(taskId, ((Number) step.get("stepNo")).intValue())) {
        throw AppException.invalid("当前工位还有未保存的步骤");
      }
    }
    LocalDateTime now = TimeProvider.now();
    // 汇总页可修正本站参数；确认时必须把修正后的值回写到步骤日志，
    // 不能只保存 confirmation_json，否则跨工位最终汇总会重新读到旧值/空值。
    mergeSummaryValues(taskId, stationId, items, steps(task.get("processSnapshot")), now);
    jdbc.update("INSERT INTO task_station_confirmation(task_id,station_id,operator_id,confirmation_json,confirmed_at) VALUES(?,?,?,?,?) "
            + "ON DUPLICATE KEY UPDATE operator_id=VALUES(operator_id),confirmation_json=VALUES(confirmation_json),confirmed_at=VALUES(confirmed_at)",
        taskId, stationId, user.getId(), write(items == null ? Collections.emptyList() : items), now);
    jdbc.update("UPDATE assembly_task SET row_version=row_version+1 WHERE id=?", taskId);
    events.publish(taskId, "station-confirmed", map("stationId", stationId));
    return task(taskId);
  }

  @Transactional
  public Map<String, Object> updateSteelNo(CurrentUser user, Long taskId, String steelNo) {
    requireOperatorStation(user); Map<String, Object> task = requiredTask(taskId);
    if (((Number) task.get("statusCode")).intValue() != 0) throw AppException.conflict("任务已经完成");
    jdbc.update("UPDATE assembly_task SET steel_no=?,row_version=row_version+1 WHERE id=?", steelNo, taskId);
    events.publish(taskId, "steel-no-updated", map("steelNo", steelNo)); return task(taskId);
  }

  @Transactional
  public Map<String, Object> complete(CurrentUser user, Long taskId, Object items) {
    requireOperatorStation(user); Map<String, Object> task = requiredTask(taskId);
    if (((Number) task.get("statusCode")).intValue() != 0) throw AppException.conflict("任务已经完成");
    List<Map<String, Object>> snapshot = steps(task.get("processSnapshot"));
    for (Map<String, Object> step : snapshot) if (!hasLog(taskId, ((Number) step.get("stepNo")).intValue())) throw AppException.invalid("仍有步骤未保存");
    for (Long station : stationIds(snapshot)) if (!confirmed(taskId, station)) throw AppException.invalid("仍有工位尚未确认交接");
    LocalDateTime now = TimeProvider.now();
    // 最终汇总允许补齐任一工位的参数，提交完成前同样持久化到步骤日志。
    mergeSummaryValues(taskId, null, items, snapshot, now);
    ensureAllParametersFilled(taskId, snapshot);
    Timestamp started = (Timestamp) task.get("startedAtValue");
    long duration = Math.max(1L, (now.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() - started.toInstant().toEpochMilli()) / 1000L);
    jdbc.update("UPDATE assembly_task SET status_code=1,ended_at=?,duration_sec=?,row_version=row_version+1 WHERE id=?", now, duration, taskId);
    events.publish(taskId, "task-completed", map("durationSec", duration)); return task(taskId);
  }

  public Map<String, Object> task(Long id) { return detail(requiredTask(id)); }
  /**
   * Product-code recognition must come from the durable in-progress task, not
   * from a browser-local cache.  Operators commonly change workstations or
   * refresh the page between stations.
   */
  public Map<String, Object> bindingFor(CurrentUser user, String productCode) {
    requireOperatorStation(user);
    List<Map<String, Object>> rows = jdbc.query(
        "SELECT * FROM assembly_task WHERE product_code=? AND status_code=0 ORDER BY started_at DESC LIMIT 1",
        (rs, n) -> row(rs), productCode.trim());
    if (rows.isEmpty()) return null;
    Map<String, Object> task = rows.get(0);
    return map("taskId", String.valueOf(task.get("idValue")), "productCode", task.get("productCode"),
        "model", task.get("model"), "steelNo", task.get("steelNo"));
  }
  public int normalizePageSize(int pageSize) {
    if (pageSize == 10 || pageSize == 20 || pageSize == 50 || pageSize == 100) return pageSize;
    return 20;
  }
  public List<String> models() {
    return jdbc.query("SELECT DISTINCT model FROM assembly_task WHERE model IS NOT NULL AND model<>'' ORDER BY model", (rs, n) -> rs.getString(1));
  }
  public Map<String, Object> page(int page, int pageSize, Integer status, String startedFrom, String startedTo, String model) {
    StringBuilder where = new StringBuilder(" WHERE 1=1");
    List<Object> arguments = new ArrayList<Object>();
    if (status != null) {
      where.append(" AND status_code=?");
      arguments.add(status.intValue() == 1 ? 1 : 0);
    }
    if (model != null && !model.trim().isEmpty()) {
      where.append(" AND model=?");
      arguments.add(model.trim());
    }
    if (startedFrom != null && !startedFrom.trim().isEmpty()) {
      where.append(" AND started_at>=?");
      arguments.add(utcStart(startedFrom));
    }
    if (startedTo != null && !startedTo.trim().isEmpty()) {
      where.append(" AND started_at<?");
      arguments.add(utcEndExclusive(startedTo));
    }
    if (startedFrom != null && !startedFrom.trim().isEmpty() && startedTo != null && !startedTo.trim().isEmpty() && utcStart(startedFrom).isAfter(utcEndExclusive(startedTo))) {
      throw AppException.invalid("开始日期不能晚于结束日期");
    }
    String predicate = where.toString();
    List<Object> countArguments = new ArrayList<Object>(arguments);
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM assembly_task" + predicate, countArguments.toArray(), Integer.class);
    List<Object> rowArguments = new ArrayList<Object>(arguments);
    rowArguments.add(pageSize);
    rowArguments.add((page - 1) * pageSize);
    List<Map<String, Object>> rows = jdbc.query("SELECT * FROM assembly_task" + predicate + " ORDER BY started_at DESC, id DESC LIMIT ? OFFSET ?", rowArguments.toArray(), (rs, n) -> detail(row(rs)));
    return map("items", rows, "page", page, "pageSize", pageSize, "total", total == null ? 0 : total);
  }
  private LocalDateTime utcStart(String value) {
    try {
      return LocalDate.parse(value.trim()).atStartOfDay(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (Exception e) {
      throw AppException.invalid("开始日期格式应为 YYYY-MM-DD");
    }
  }
  private LocalDateTime utcEndExclusive(String value) {
    try {
      return LocalDate.parse(value.trim()).plusDays(1).atStartOfDay(ZoneId.of("Asia/Shanghai")).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    } catch (Exception e) {
      throw AppException.invalid("结束日期格式应为 YYYY-MM-DD");
    }
  }
  public Map<String, Object> activeFor(CurrentUser user) {
    requireOperatorStation(user);
    List<Map<String, Object>> rows = jdbc.query("SELECT * FROM assembly_task WHERE status_code=0 ORDER BY started_at DESC LIMIT 20", (rs, n) -> detail(row(rs)));
    return map("items", rows);
  }
  public Map<String, Object> statistics(String from, String to) {
    StatisticsRange range = statisticsRange(from, to);
    int rangeDays = (int) java.time.temporal.ChronoUnit.DAYS.between(range.from, range.to) + 1;
    StatisticsRange previous = new StatisticsRange(range.from.minusDays(rangeDays), range.from.minusDays(1));
    List<Map<String, Object>> completed = completedBetween(range);
    List<Map<String, Object>> previousCompleted = completedBetween(previous);
    List<Map<String, Object>> inProgress = startedBetween(range);
    List<Map<String, Object>> previousInProgress = startedBetween(previous);
    Map<Long, StationInfo> stations = stationInfo();
    Map<Long, List<Map<String, Object>>> logs = logsFor(taskIds(completed));
    LocalDate today = localDate(TimeProvider.now());
    LocalDate monthStart = today.withDayOfMonth(1);
    List<Map<String, Object>> currentMonth = completedBetween(new StatisticsRange(monthStart, today));
    LocalDate yesterday = today.minusDays(1);
    List<Map<String, Object>> previousDay = completedBetween(new StatisticsRange(yesterday, yesterday));
    LocalDate previousMonthStart = monthStart.minusMonths(1);
    LocalDate previousMonthEnd = previousMonthStart.plusDays(Math.min(today.getDayOfMonth(), previousMonthStart.lengthOfMonth()) - 1L);
    List<Map<String, Object>> previousMonth = completedBetween(new StatisticsRange(previousMonthStart, previousMonthEnd));
    List<String> models = statisticModels(completed);

    return map(
        "range", map("from", range.from.toString(), "to", range.to.toString()),
        "overview", overview(completed, previousCompleted, inProgress, previousInProgress),
        "liveStations", liveStations(stations),
        "dailyCapacity", dailyCapacity(completed, range),
        "todayCapacity", completedPieces(filterCompletedForDate(currentMonth, today)),
        "todayCapacityPrevious", completedPieces(previousDay),
        "monthCapacity", completedPieces(currentMonth),
        "monthCapacityPrevious", completedPieces(previousMonth),
        "models", models,
        "parameterDeviationsByModel", parameterDeviations(completed, logs, stations),
        "stepDurationsByModel", stepDurations(completed, logs));
  }

  private StatisticsRange statisticsRange(String from, String to) {
    LocalDate today = localDate(TimeProvider.now());
    LocalDate start = blank(from) ? today.minusDays(6) : parseStatisticsDate(from, "开始日期格式应为 YYYY-MM-DD");
    LocalDate end = blank(to) ? today : parseStatisticsDate(to, "结束日期格式应为 YYYY-MM-DD");
    if (start.isAfter(end)) throw AppException.invalid("开始日期不能晚于结束日期");
    return new StatisticsRange(start, end);
  }

  private LocalDate parseStatisticsDate(String value, String message) {
    try { return LocalDate.parse(value.trim()); }
    catch (Exception e) { throw AppException.invalid(message); }
  }

  private LocalDate localDate(LocalDateTime value) {
    return value.atZone(ZoneOffset.UTC).withZoneSameInstant(STATISTICS_ZONE).toLocalDate();
  }

  private LocalDate localDate(Timestamp value) {
    return value.toInstant().atZone(STATISTICS_ZONE).toLocalDate();
  }

  private List<Map<String, Object>> completedBetween(StatisticsRange range) {
    return jdbc.query("SELECT * FROM assembly_task WHERE status_code=1 AND ended_at>=? AND ended_at<? ORDER BY ended_at,id",
        (rs, n) -> row(rs), range.utcStart(), range.utcEndExclusive());
  }

  private List<Map<String, Object>> startedBetween(StatisticsRange range) {
    return jdbc.query("SELECT * FROM assembly_task WHERE status_code=0 AND started_at>=? AND started_at<? ORDER BY started_at,id",
        (rs, n) -> row(rs), range.utcStart(), range.utcEndExclusive());
  }

  private Map<String, Object> overview(List<Map<String, Object>> completed, List<Map<String, Object>> previousCompleted,
      List<Map<String, Object>> inProgress, List<Map<String, Object>> previousInProgress) {
    ModelDuration longest = modelDuration(completed, true);
    ModelDuration best = modelDuration(completed, false);
    return map(
        "completed", metric(completedPieces(completed), completedPieces(previousCompleted)),
        "backlog", metric((long) inProgress.size(), (long) previousInProgress.size()),
        "firstPassRate", metric(firstPassRate(completed), firstPassRate(previousCompleted)),
        "averageDurationSec", metric(averageDuration(completed), averageDuration(previousCompleted)),
        "longestModel", modelDurationMap(longest),
        "bestModel", modelDurationMap(best));
  }

  private Map<String, Object> metric(Object value, Object previous) { return map("value", value, "previous", previous); }

  private long completedPieces(List<Map<String, Object>> tasks) {
    Set<String> steelNos = new HashSet<String>();
    for (Map<String, Object> task : tasks) {
      String steelNo = string(task.get("steelNo"));
      if (!steelNo.isEmpty()) steelNos.add(steelNo);
    }
    return steelNos.size();
  }

  private List<Map<String, Object>> filterCompletedForDate(List<Map<String, Object>> tasks, LocalDate date) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (Map<String, Object> task : tasks) {
      Timestamp endedAt = (Timestamp) task.get("endedAtValue");
      if (endedAt != null && date.equals(localDate(endedAt))) result.add(task);
    }
    return result;
  }

  private Double firstPassRate(List<Map<String, Object>> tasks) {
    Map<String, Integer> counts = new HashMap<String, Integer>();
    int records = 0;
    for (Map<String, Object> task : tasks) {
      String steelNo = string(task.get("steelNo"));
      if (steelNo.isEmpty()) continue;
      records++;
      counts.put(steelNo, counts.containsKey(steelNo) ? counts.get(steelNo) + 1 : 1);
    }
    if (records == 0) return null;
    int once = 0;
    for (Map<String, Object> task : tasks) if (counts.get(string(task.get("steelNo"))) != null && counts.get(string(task.get("steelNo"))) == 1) once++;
    return roundOneDecimal(once * 100D / records);
  }

  private Long averageDuration(List<Map<String, Object>> tasks) {
    long total = 0L, count = 0L;
    for (Map<String, Object> task : tasks) {
      Object raw = task.get("durationSec");
      if (!(raw instanceof Number) || ((Number) raw).longValue() <= 0) continue;
      total += ((Number) raw).longValue(); count++;
    }
    return count == 0 ? null : Math.round((double) total / count);
  }

  private ModelDuration modelDuration(List<Map<String, Object>> tasks, boolean longest) {
    Map<String, long[]> values = new TreeMap<String, long[]>();
    for (Map<String, Object> task : tasks) {
      String model = string(task.get("model")); Object raw = task.get("durationSec");
      if (model.isEmpty() || !(raw instanceof Number) || ((Number) raw).longValue() <= 0) continue;
      long[] aggregate = values.get(model);
      if (aggregate == null) { aggregate = new long[] { 0L, 0L }; values.put(model, aggregate); }
      aggregate[0] += ((Number) raw).longValue(); aggregate[1]++;
    }
    ModelDuration selected = null;
    for (Map.Entry<String, long[]> entry : values.entrySet()) {
      ModelDuration candidate = new ModelDuration(entry.getKey(), Math.round((double) entry.getValue()[0] / entry.getValue()[1]));
      if (selected == null || (longest ? candidate.durationSec > selected.durationSec : candidate.durationSec < selected.durationSec)) selected = candidate;
    }
    return selected;
  }

  private Map<String, Object> modelDurationMap(ModelDuration value) {
    return value == null ? map("model", null, "averageDurationSec", null) : map("model", value.model, "averageDurationSec", value.durationSec);
  }

  private List<Map<String, Object>> dailyCapacity(List<Map<String, Object>> completed, StatisticsRange range) {
    Map<LocalDate, Set<String>> pieces = new HashMap<LocalDate, Set<String>>();
    for (Map<String, Object> task : completed) {
      Timestamp endedAt = (Timestamp) task.get("endedAtValue"); String steelNo = string(task.get("steelNo"));
      if (endedAt == null || steelNo.isEmpty()) continue;
      LocalDate date = localDate(endedAt);
      Set<String> values = pieces.get(date);
      if (values == null) { values = new HashSet<String>(); pieces.put(date, values); }
      values.add(steelNo);
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (LocalDate date = range.from; !date.isAfter(range.to); date = date.plusDays(1)) {
      Set<String> values = pieces.get(date);
      result.add(map("date", date.toString(), "label", String.format("%02d-%02d", date.getMonthValue(), date.getDayOfMonth()), "count", values == null ? 0 : values.size()));
    }
    return result;
  }

  private List<String> statisticModels(List<Map<String, Object>> completed) {
    Set<String> values = new java.util.TreeSet<String>();
    values.addAll(jdbc.query("SELECT model FROM process_definition WHERE is_deleted=0 AND model<>'' ORDER BY model", (rs, n) -> rs.getString(1)));
    for (Map<String, Object> task : completed) if (!string(task.get("model")).isEmpty()) values.add(string(task.get("model")));
    return new ArrayList<String>(values);
  }

  private Map<Long, StationInfo> stationInfo() {
    Map<Long, StationInfo> result = new HashMap<Long, StationInfo>();
    jdbc.query("SELECT id,station_no,name FROM workstation WHERE is_deleted=0", (RowCallbackHandler) rs -> {
      long id = rs.getLong("id"); result.put(id, new StationInfo(id, rs.getInt("station_no"), rs.getString("name")));
    });
    return result;
  }

  private List<Map<String, Object>> liveStations(Map<Long, StationInfo> stations) {
    Map<Integer, StationInfo> stationsByNo = new HashMap<Integer, StationInfo>();
    for (StationInfo station : stations.values()) if (station.number >= 1 && station.number <= 10) stationsByNo.put(station.number, station);
    Map<Long, Set<Long>> confirmations = confirmationsForActiveTasks();
    Map<Integer, List<Map<String, Object>>> tasksByStation = new HashMap<Integer, List<Map<String, Object>>>();
    List<Map<String, Object>> active = jdbc.query("SELECT * FROM assembly_task WHERE status_code=0 ORDER BY started_at,id", (rs, n) -> row(rs));
    Map<Long, Map<Long, OperatorActivity>> operatorActivities = operatorActivities(taskIds(active));
    Map<Integer, OperatorActivity> operatorsByStation = new HashMap<Integer, OperatorActivity>();
    for (Map<String, Object> task : active) {
      Long taskId = (Long) task.get("idValue");
      Long stationId = targetStation(task, confirmations.get(taskId), stations);
      StationInfo station = stations.get(stationId);
      if (station == null || station.number < 1 || station.number > 10) continue;
      List<Map<String, Object>> stationTasks = tasksByStation.get(station.number);
      if (stationTasks == null) { stationTasks = new ArrayList<Map<String, Object>>(); tasksByStation.put(station.number, stationTasks); }
      stationTasks.add(map("id", String.valueOf(taskId), "model", task.get("model"), "steelNo", task.get("steelNo")));
      Map<Long, OperatorActivity> taskActivities = operatorActivities.get(taskId);
      OperatorActivity activity = taskActivities == null ? null : taskActivities.get(stationId);
      OperatorActivity current = operatorsByStation.get(station.number);
      if (activity != null && isNewerActivity(activity, current)) operatorsByStation.put(station.number, activity);
    }
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (int number = 1; number <= 10; number++) {
      StationInfo station = stationsByNo.get(number);
      List<Map<String, Object>> tasks = tasksByStation.get(number);
      if (tasks == null) tasks = new ArrayList<Map<String, Object>>();
      Collections.sort(tasks, (left, right) -> (string(left.get("model")) + string(left.get("steelNo"))).compareTo(string(right.get("model")) + string(right.get("steelNo"))));
      OperatorActivity activity = operatorsByStation.get(number);
      result.add(map("stationNo", number, "workstationId", station == null ? null : String.valueOf(station.id), "name", station == null ? "工位" + number : station.name,
          "configured", station != null, "tasks", tasks, "count", tasks.size(), "status", station == null ? "unconfigured" : stationStatus(tasks.size()),
          "operatorName", activity == null ? null : activity.name));
    }
    return result;
  }

  private Map<Long, Map<Long, OperatorActivity>> operatorActivities(List<Long> taskIds) {
    Map<Long, Map<Long, OperatorActivity>> result = new HashMap<Long, Map<Long, OperatorActivity>>();
    if (taskIds.isEmpty()) return result;
    StringBuilder placeholders = new StringBuilder();
    for (int index = 0; index < taskIds.size(); index++) {
      if (index > 0) placeholders.append(',');
      placeholders.append('?');
    }
    String sql = "SELECT l.task_id,l.station_id,l.step_no,l.updated_at,u.name operator_name FROM task_step_log l "
        + "JOIN sys_user u ON u.id=l.operator_id WHERE l.task_id IN (" + placeholders + ") "
        + "ORDER BY l.updated_at DESC,l.task_id DESC,l.step_no DESC";
    jdbc.query(sql, (RowCallbackHandler) rs -> {
      Long taskId = rs.getLong("task_id");
      Long stationId = rs.getLong("station_id");
      Map<Long, OperatorActivity> byStation = result.get(taskId);
      if (byStation == null) { byStation = new HashMap<Long, OperatorActivity>(); result.put(taskId, byStation); }
      if (!byStation.containsKey(stationId)) {
        byStation.put(stationId, new OperatorActivity(taskId, rs.getInt("step_no"), rs.getTimestamp("updated_at"), rs.getString("operator_name")));
      }
    }, taskIds.toArray(new Object[taskIds.size()]));
    return result;
  }

  private boolean isNewerActivity(OperatorActivity candidate, OperatorActivity current) {
    if (current == null) return true;
    int byTime = candidate.updatedAt.compareTo(current.updatedAt);
    if (byTime != 0) return byTime > 0;
    int byTask = candidate.taskId.compareTo(current.taskId);
    return byTask != 0 ? byTask > 0 : candidate.stepNo > current.stepNo;
  }

  private Map<Long, Set<Long>> confirmationsForActiveTasks() {
    Map<Long, Set<Long>> result = new HashMap<Long, Set<Long>>();
    jdbc.query("SELECT c.task_id,c.station_id FROM task_station_confirmation c JOIN assembly_task t ON t.id=c.task_id WHERE t.status_code=0", (RowCallbackHandler) rs -> {
      Long taskId = rs.getLong("task_id"); Set<Long> values = result.get(taskId);
      if (values == null) { values = new HashSet<Long>(); result.put(taskId, values); }
      values.add(rs.getLong("station_id"));
    });
    return result;
  }

  private Long targetStation(Map<String, Object> task, Set<Long> confirmed, Map<Long, StationInfo> stations) {
    List<Map<String, Object>> steps = new ArrayList<Map<String, Object>>(steps(task.get("processSnapshot")));
    Collections.sort(steps, (left, right) -> Integer.compare(stationNumber(number(left.get("stationId")), stations), stationNumber(number(right.get("stationId")), stations)));
    Long fallback = null;
    for (Map<String, Object> step : steps) {
      Long stationId = number(step.get("stationId")); fallback = stationId;
      if (confirmed == null || !confirmed.contains(stationId)) return stationId;
    }
    return fallback;
  }

  private int stationNumber(Long stationId, Map<Long, StationInfo> stations) { return stations.containsKey(stationId) ? stations.get(stationId).number : stationId.intValue(); }
  private String stationStatus(int count) { return count == 0 ? "idle" : count == 1 ? "normal" : count <= 3 ? "busy" : "congested"; }

  private List<Long> taskIds(List<Map<String, Object>> tasks) {
    List<Long> result = new ArrayList<Long>(); for (Map<String, Object> task : tasks) result.add((Long) task.get("idValue")); return result;
  }

  private Map<Long, List<Map<String, Object>>> logsFor(List<Long> taskIds) {
    Map<Long, List<Map<String, Object>>> result = new HashMap<Long, List<Map<String, Object>>>();
    if (taskIds.isEmpty()) return result;
    StringBuilder placeholders = new StringBuilder();
    for (int index = 0; index < taskIds.size(); index++) { if (index > 0) placeholders.append(','); placeholders.append('?'); }
    jdbc.query("SELECT task_id,step_no,station_id,action_code,values_json,occurred_at FROM task_step_log WHERE task_id IN (" + placeholders + ") ORDER BY task_id,occurred_at,step_no",
        (RowCallbackHandler) rs -> {
          Long taskId = rs.getLong("task_id"); List<Map<String, Object>> values = result.get(taskId);
          if (values == null) { values = new ArrayList<Map<String, Object>>(); result.put(taskId, values); }
          values.add(map("stepNo", rs.getInt("step_no"), "stationId", rs.getLong("station_id"), "actionCode", rs.getInt("action_code"), "values", readMap(rs.getString("values_json")), "occurredAt", rs.getTimestamp("occurred_at")));
        }, taskIds.toArray());
    return result;
  }

  private Map<String, List<Map<String, Object>>> parameterDeviations(List<Map<String, Object>> tasks, Map<Long, List<Map<String, Object>>> logs, Map<Long, StationInfo> stations) {
    Map<String, Map<String, Long>> result = new TreeMap<String, Map<String, Long>>();
    for (Map<String, Object> task : tasks) {
      String model = string(task.get("model")); if (model.isEmpty()) continue;
      Map<Integer, Map<String, Object>> stepsByNo = new HashMap<Integer, Map<String, Object>>();
      for (Map<String, Object> step : steps(task.get("processSnapshot"))) stepsByNo.put(((Number) step.get("stepNo")).intValue(), step);
      for (Map<String, Object> log : logs.containsKey((Long) task.get("idValue")) ? logs.get((Long) task.get("idValue")) : Collections.<Map<String, Object>>emptyList()) {
        if (((Number) log.get("actionCode")).intValue() != 0) continue;
        Map<String, Object> step = stepsByNo.get(((Number) log.get("stepNo")).intValue()); if (step == null) continue;
        Map<String, Object> values = (Map<String, Object>) log.get("values");
        for (Map<String, Object> parameter : (List<Map<String, Object>>) step.get("parameters")) {
          Object actual = values.get(String.valueOf(((Number) parameter.get("index")).intValue()));
          if (!parameterDeviation(actual, parameter)) continue;
          Long stationId = number(step.get("stationId")); StationInfo station = stations.get(stationId);
          String name = station == null ? "工位" + stationId : station.name;
          Map<String, Long> group = result.get(model); if (group == null) { group = new TreeMap<String, Long>(); result.put(model, group); }
          group.put(name, group.containsKey(name) ? group.get(name) + 1 : 1L);
        }
      }
    }
    return rankGroups(result);
  }

  private Map<String, List<Map<String, Object>>> stepDurations(List<Map<String, Object>> tasks, Map<Long, List<Map<String, Object>>> logs) {
    Map<String, Map<String, Long>> result = new TreeMap<String, Map<String, Long>>();
    for (Map<String, Object> task : tasks) {
      String model = string(task.get("model")); if (model.isEmpty()) continue;
      Map<Integer, Map<String, Object>> stepsByNo = new HashMap<Integer, Map<String, Object>>();
      for (Map<String, Object> step : steps(task.get("processSnapshot"))) stepsByNo.put(((Number) step.get("stepNo")).intValue(), step);
      Timestamp previous = (Timestamp) task.get("startedAtValue");
      for (Map<String, Object> log : logs.containsKey((Long) task.get("idValue")) ? logs.get((Long) task.get("idValue")) : Collections.<Map<String, Object>>emptyList()) {
        if (((Number) log.get("actionCode")).intValue() != 0) continue;
        Timestamp occurredAt = (Timestamp) log.get("occurredAt");
        long duration = previous == null || occurredAt == null ? 0L : Math.max(0L, (occurredAt.getTime() - previous.getTime()) / 1000L);
        previous = occurredAt == null ? previous : occurredAt;
        Map<String, Object> step = stepsByNo.get(((Number) log.get("stepNo")).intValue()); if (step == null || duration == 0L) continue;
        String name = step.get("stepNo") + ". " + string(step.get("title"));
        Map<String, Long> group = result.get(model); if (group == null) { group = new TreeMap<String, Long>(); result.put(model, group); }
        group.put(name, group.containsKey(name) ? group.get(name) + duration : duration);
      }
    }
    return rankGroups(result);
  }

  private Map<String, List<Map<String, Object>>> rankGroups(Map<String, Map<String, Long>> groups) {
    Map<String, List<Map<String, Object>>> result = new LinkedHashMap<String, List<Map<String, Object>>>();
    for (Map.Entry<String, Map<String, Long>> entry : groups.entrySet()) {
      List<Map.Entry<String, Long>> rows = new ArrayList<Map.Entry<String, Long>>(entry.getValue().entrySet());
      Collections.sort(rows, (left, right) -> right.getValue().compareTo(left.getValue()) != 0 ? right.getValue().compareTo(left.getValue()) : left.getKey().compareTo(right.getKey()));
      List<Map<String, Object>> values = new ArrayList<Map<String, Object>>();
      for (Map.Entry<String, Long> row : rows) values.add(map("name", row.getKey(), "value", row.getValue()));
      result.put(entry.getKey(), values);
    }
    return result;
  }

  private boolean parameterDeviation(Object rawActual, Map<String, Object> parameter) {
    String actual = string(rawActual); if (actual.isEmpty()) return false;
    String reference = string(parameter.get("referenceValue"));
    int type = parameter.get("type") instanceof Number ? ((Number) parameter.get("type")).intValue() : 0;
    if (type == 1) {
      List<String> options = new ArrayList<String>(); Object rawOptions = parameter.get("options");
      if (rawOptions instanceof List) for (Object option : (List<?>) rawOptions) if (!string(option).isEmpty()) options.add(string(option));
      if (options.isEmpty()) options.addAll(Arrays.asList(reference.split("\\r?\\n")));
      return options.isEmpty() || !actual.equals(string(options.get(0)));
    }
    if (type == 2) return !rangeValueValid(actual, reference);
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^(-?\\d+(?:\\.\\d+)?)\\s*(?:-|~|至)\\s*(-?\\d+(?:\\.\\d+)?)$").matcher(reference);
    if (matcher.matches()) try {
      double value = Double.parseDouble(actual), left = Double.parseDouble(matcher.group(1)), right = Double.parseDouble(matcher.group(2));
      return value < Math.min(left, right) || value > Math.max(left, right);
    } catch (NumberFormatException ignored) { return true; }
    return !actual.equals(reference);
  }

  private boolean rangeValueValid(String actual, String reference) {
    try {
      double value = Double.parseDouble(actual);
      if (reference.indexOf('\n') >= 0 || reference.indexOf('\r') >= 0) {
        String[] legacy = reference.trim().split("\\r?\\n");
        if (legacy.length != 2) return false;
        return value >= Double.parseDouble(legacy[0].trim()) && value <= Double.parseDouble(legacy[1].trim());
      }
      String[] parts = reference.replaceAll("\\s", "").split(",", -1);
      if (parts.length != 4) return false;
      Double lowerExclusive = rangeBound(parts[0], ">");
      Double lowerInclusive = rangeBound(parts[1], "=");
      Double upperInclusive = rangeBound(parts[2], "=");
      Double upperExclusive = rangeBound(parts[3], "<");
      if (lowerInclusive != null && (lowerExclusive == null || Double.compare(lowerInclusive, lowerExclusive) != 0)) return false;
      if (upperInclusive != null && (upperExclusive == null || Double.compare(upperInclusive, upperExclusive) != 0)) return false;
      if (lowerExclusive != null && (value < lowerExclusive || (Double.compare(value, lowerExclusive) == 0 && lowerInclusive == null))) return false;
      if (upperExclusive != null && (value > upperExclusive || (Double.compare(value, upperExclusive) == 0 && upperInclusive == null))) return false;
      return lowerExclusive != null || upperExclusive != null;
    } catch (Exception e) { return false; }
  }

  private Double rangeBound(String value, String operator) {
    if ("()".equals(value)) return null;
    java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("^\\(" + java.util.regex.Pattern.quote(operator) + "(-?\\d+(?:\\.\\d+)?)\\)$").matcher(value);
    return matcher.matches() ? Double.valueOf(matcher.group(1)) : null;
  }

  private Double roundOneDecimal(double value) { return Math.round(value * 10D) / 10D; }
  private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
  private boolean blank(String value) { return value == null || value.trim().isEmpty(); }

  private static final class StatisticsRange {
    private final LocalDate from, to;
    private StatisticsRange(LocalDate from, LocalDate to) { this.from = from; this.to = to; }
    private LocalDateTime utcStart() { return from.atStartOfDay(STATISTICS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
    private LocalDateTime utcEndExclusive() { return to.plusDays(1).atStartOfDay(STATISTICS_ZONE).withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(); }
  }
  private static final class StationInfo {
    private final long id; private final int number; private final String name;
    private StationInfo(long id, int number, String name) { this.id = id; this.number = number; this.name = name; }
  }
  private static final class OperatorActivity {
    private final Long taskId; private final int stepNo; private final Timestamp updatedAt; private final String name;
    private OperatorActivity(Long taskId, int stepNo, Timestamp updatedAt, String name) {
      this.taskId = taskId; this.stepNo = stepNo; this.updatedAt = updatedAt; this.name = name;
    }
  }
  private static final class ModelDuration {
    private final String model; private final long durationSec;
    private ModelDuration(String model, long durationSec) { this.model = model; this.durationSec = durationSec; }
  }

  private Map<String, Object> requiredTask(Long id) {
    List<Map<String, Object>> rows = jdbc.query("SELECT * FROM assembly_task WHERE id=?", (rs, n) -> row(rs), id);
    if (rows.isEmpty()) throw AppException.notFound("任务不存在"); return rows.get(0);
  }
  private Map<String, Object> row(java.sql.ResultSet rs) throws java.sql.SQLException {
    return map("idValue", rs.getLong("id"), "model", rs.getString("model"), "productCode", rs.getString("product_code"), "steelNo", rs.getString("steel_no"), "processSnapshot", steps(readList(rs.getString("process_snapshot_json"))), "statusCode", rs.getInt("status_code"), "startedBy", rs.getLong("started_by"), "startedAtValue", rs.getTimestamp("started_at"), "endedAtValue", rs.getTimestamp("ended_at"), "durationSec", rs.getObject("duration_sec"), "rowVersion", rs.getInt("row_version"));
  }
  private Map<String, Object> detail(Map<String, Object> raw) {
    Long id = (Long) raw.get("idValue"); Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", String.valueOf(id)); result.put("model", raw.get("model")); result.put("productCode", raw.get("productCode")); result.put("steelNo", raw.get("steelNo")); result.put("processSnapshot", raw.get("processSnapshot"));
    int status = ((Number) raw.get("statusCode")).intValue(); result.put("statusCode", status); result.put("status", status == 1 ? "已完成" : "进行中");
    result.put("startedAt", iso((Timestamp) raw.get("startedAtValue"))); result.put("endedAt", iso((Timestamp) raw.get("endedAtValue"))); result.put("durationSec", raw.get("durationSec")); result.put("rowVersion", raw.get("rowVersion"));
    Map<String, Object> logs = new LinkedHashMap<String, Object>();
    jdbc.query("SELECT l.*,u.name operator_name,w.name station_name FROM task_step_log l JOIN sys_user u ON u.id=l.operator_id JOIN workstation w ON w.id=l.station_id WHERE l.task_id=?", (RowCallbackHandler) rs -> {
      logs.put(String.valueOf(rs.getInt("step_no")), map("stepNo", rs.getInt("step_no"), "stationId", rs.getLong("station_id"), "stationName", rs.getString("station_name"), "operatorName", rs.getString("operator_name"), "actionCode", rs.getInt("action_code"), "values", readMap(rs.getString("values_json")), "time", iso(rs.getTimestamp("occurred_at"))));
    }, id);
    result.put("logs", logs);
    Map<String, Object> confirmations = new LinkedHashMap<String, Object>();
    jdbc.query("SELECT c.*,w.name station_name,u.name operator_name FROM task_station_confirmation c JOIN workstation w ON w.id=c.station_id JOIN sys_user u ON u.id=c.operator_id WHERE c.task_id=?", (RowCallbackHandler) rs -> {
      confirmations.put(String.valueOf(rs.getLong("station_id")), map("stationId", rs.getLong("station_id"), "stationName", rs.getString("station_name"), "operatorName", rs.getString("operator_name"), "confirmedAt", iso(rs.getTimestamp("confirmed_at")), "items", readList(rs.getString("confirmation_json"))));
    }, id);
    List<Map<String,Object>> snapshot=(List<Map<String,Object>>)raw.get("processSnapshot"); restoreConfirmedValues(logs,confirmations); normalizeLegacyLogValues(snapshot,logs);
    result.put("stationConfirmations", confirmations); result.put("currentStepIndex", nextIndex(snapshot, logs)); return result;
  }
  private int nextIndex(List<Map<String, Object>> snapshot, Map<String, Object> logs) { for (int i=0;i<snapshot.size();i++) if (!logs.containsKey(String.valueOf(((Number)snapshot.get(i).get("stepNo")).intValue()))) return i; return Math.max(0, snapshot.size()-1); }
  private Map<String, Object> step(Map<String, Object> task, int no) { for (Map<String, Object> item : steps(task.get("processSnapshot"))) if (((Number)item.get("stepNo")).intValue() == no) return item; throw AppException.notFound("工艺步骤不存在"); }
  private boolean hasLog(Long taskId, int stepNo) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM task_step_log WHERE task_id=? AND step_no=?", Integer.class,taskId,stepNo); return count != null && count.intValue()>0; }
  private boolean confirmed(Long taskId, Long stationId) { Integer count=jdbc.queryForObject("SELECT COUNT(*) FROM task_station_confirmation WHERE task_id=? AND station_id=?", Integer.class,taskId,stationId); return count != null && count.intValue()>0; }
  private boolean previousStationsConfirmed(Map<String,Object> task, Long stationId) {
    int currentStationNo = stationNo(stationId);
    for (Long id : stationIds(steps(task.get("processSnapshot")))) {
      if (stationNo(id) < currentStationNo && !confirmed((Long) task.get("idValue"), id)) return false;
    }
    return true;
  }
  private int stationNo(Long stationId) {
    List<Integer> numbers = jdbc.query("SELECT station_no FROM workstation WHERE id=? AND is_deleted=0", (rs, n) -> Integer.valueOf(rs.getInt(1)), stationId);
    return numbers.isEmpty() ? stationId.intValue() : numbers.get(0).intValue();
  }
  private List<Long> stationIds(List<Map<String,Object>> snapshot) { List<Long> ids=new ArrayList<Long>(); for(Map<String,Object> step:snapshot){Long id=number(step.get("stationId"));if(!ids.contains(id))ids.add(id);} Collections.sort(ids);return ids; }
  private boolean manualCaptureStation(CurrentUser user) {
    List<Integer> modes = jdbc.query("SELECT capture_mode FROM workstation WHERE id=? AND is_deleted=0", (rs, n) -> Integer.valueOf(rs.getInt(1)), user.getWorkstationId());
    return !modes.isEmpty() && modes.get(0).intValue() == 0;
  }
  private void requireOperatorStation(CurrentUser user) { if (user.isAdmin() || user.getWorkstationId()==null) throw AppException.forbidden("需要工位端登录"); }
  private Long number(Object value) { return value instanceof Number ? ((Number)value).longValue() : Long.valueOf(String.valueOf(value)); }
  private List<Map<String,Object>> steps(Object value) {
    List<Map<String,Object>> result=value instanceof List?(List<Map<String,Object>>)value:readList(String.valueOf(value));
    for(int stepIndex=0;stepIndex<result.size();stepIndex++){
      Map<String,Object> step=result.get(stepIndex); if(!(step.get("stepNo") instanceof Number))step.put("stepNo",stepIndex+1);
      Object rawParameters=step.get("parameters"); List<Map<String,Object>> parameters=rawParameters instanceof List?(List<Map<String,Object>>)rawParameters:new ArrayList<Map<String,Object>>();
      for(int parameterIndex=0;parameterIndex<parameters.size();parameterIndex++){Map<String,Object> parameter=parameters.get(parameterIndex);if(!(parameter.get("index") instanceof Number))parameter.put("index",parameterIndex+1);} step.put("parameters",parameters);
    }
    return result;
  }
  private void mergeSummaryValues(Long taskId, Long onlyStationId, Object rawItems, List<Map<String,Object>> snapshot, LocalDateTime now) {
    if(!(rawItems instanceof List)) return;
    Map<Integer,Map<String,Object>> stepsByNo=new LinkedHashMap<Integer,Map<String,Object>>(); for(Map<String,Object> step:snapshot)stepsByNo.put(((Number)step.get("stepNo")).intValue(),step);
    for(Object rawItem:(List<?>)rawItems){ if(!(rawItem instanceof Map))continue; Map<String,Object> item=(Map<String,Object>)rawItem; Object rawNo=item.get("stepNo"); if(!(rawNo instanceof Number))continue; int stepNo=((Number)rawNo).intValue(); Map<String,Object> step=stepsByNo.get(stepNo); if(step==null)continue;
      Long stationId=number(step.get("stationId")); if(onlyStationId!=null&&!onlyStationId.equals(stationId))continue;
      Map<String,Object> values=stepValues(taskId,stepNo); Object rawParameters=item.get("parameters"); if(rawParameters instanceof List)for(Object rawParameter:(List<?>)rawParameters){if(!(rawParameter instanceof Map))continue;Map<String,Object> parameter=(Map<String,Object>)rawParameter;Object rawIndex=parameter.get("index");if(!(rawIndex instanceof Number))continue;Object value=parameter.get("operatorValue");values.put(String.valueOf(((Number)rawIndex).intValue()),value==null?"":String.valueOf(value));}
      jdbc.update("UPDATE task_step_log SET values_json=?,updated_at=? WHERE task_id=? AND step_no=?",write(values),now,taskId,stepNo);
    }
  }
  private Map<String,Object> stepValues(Long taskId,int stepNo){ List<String> values=jdbc.query("SELECT values_json FROM task_step_log WHERE task_id=? AND step_no=?",(rs,n)->rs.getString(1),taskId,stepNo); return values.isEmpty()?new LinkedHashMap<String,Object>():readMap(values.get(0)); }
  private void ensureAllParametersFilled(Long taskId,List<Map<String,Object>> snapshot){for(Map<String,Object> step:snapshot){Map<String,Object> values=stepValues(taskId,((Number)step.get("stepNo")).intValue());for(Map<String,Object> parameter:(List<Map<String,Object>>)step.get("parameters")){Object value=values.get(String.valueOf(((Number)parameter.get("index")).intValue()));if(value==null||String.valueOf(value).trim().isEmpty())throw AppException.invalid("仍有参数未填写");}}}
  private void normalizeLegacyLogValues(List<Map<String,Object>> snapshot,Map<String,Object> logs){for(Map<String,Object> step:snapshot){Map<String,Object> log=(Map<String,Object>)logs.get(String.valueOf(step.get("stepNo")));if(log==null)continue;Map<String,Object> values=(Map<String,Object>)log.get("values");if(values==null||!values.containsKey("undefined"))continue;for(Map<String,Object> parameter:(List<Map<String,Object>>)step.get("parameters")){String key=String.valueOf(((Number)parameter.get("index")).intValue());if(!values.containsKey(key)){values.put(key,values.get("undefined"));break;}}values.remove("undefined");}}
  private void restoreConfirmedValues(Map<String,Object> logs,Map<String,Object> confirmations){for(Object rawConfirmation:confirmations.values()){if(!(rawConfirmation instanceof Map))continue;Object rawItems=((Map<?,?>)rawConfirmation).get("items");if(!(rawItems instanceof List))continue;for(Object rawItem:(List<?>)rawItems){if(!(rawItem instanceof Map))continue;Map<?,?> item=(Map<?,?>)rawItem;Object rawStepNo=item.get("stepNo");if(!(rawStepNo instanceof Number))continue;Map<String,Object> log=(Map<String,Object>)logs.get(String.valueOf(((Number)rawStepNo).intValue()));if(log==null)continue;Map<String,Object> values=(Map<String,Object>)log.get("values");Object rawParameters=item.get("parameters");if(!(rawParameters instanceof List))continue;for(Object rawParameter:(List<?>)rawParameters){if(!(rawParameter instanceof Map))continue;Map<?,?> parameter=(Map<?,?>)rawParameter;Object rawIndex=parameter.get("index"),value=parameter.get("operatorValue");if(!(rawIndex instanceof Number)||value==null||String.valueOf(value).trim().isEmpty())continue;String key=String.valueOf(((Number)rawIndex).intValue());Object current=values.get(key);if(current==null||String.valueOf(current).trim().isEmpty())values.put(key,value);}}}}
  private String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw AppException.invalid("任务数据格式不合法");} }
  private List<Map<String,Object>> readList(String value) { try{return json.readValue(value,new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){throw AppException.invalid("任务快照数据损坏");} }
  private Map<String,Object> readMap(String value) { try{return json.readValue(value,new TypeReference<Map<String,Object>>(){});}catch(Exception e){return new LinkedHashMap<String,Object>();} }
  private String iso(Timestamp value) { return value == null ? null : value.toInstant().toString(); }
  private Map<String,Object> map(Object... values) { Map<String,Object> result=new LinkedHashMap<String,Object>(); for(int i=0;i<values.length;i+=2) result.put(String.valueOf(values[i]),values[i+1]); return result; }
}
