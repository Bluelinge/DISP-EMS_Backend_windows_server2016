package com.bluelinge.dispems.task;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/statistics")
public class StatisticsController {
  private final TaskService tasks;
  public StatisticsController(TaskService tasks) { this.tasks=tasks; }
  @GetMapping("/production")
  public ApiResponse<Map<String,Object>> production(
      @RequestParam(required = false) String from,
      @RequestParam(required = false) String to) {
    AuthPrincipal.requireAdmin();
    return ApiResponse.ok(tasks.statistics(from, to));
  }
}
