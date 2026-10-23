package com.bluelinge.dispems.task;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
  private final TaskService tasks;
  public TaskController(TaskService tasks) { this.tasks=tasks; }
  @PostMapping public ApiResponse<Map<String,Object>> create(@Valid @RequestBody CreateRequest request) { return ApiResponse.ok(tasks.create(AuthPrincipal.required(),request.productCode,request.model,request.steelNo,request.processSnapshot)); }
  @GetMapping("/binding") public ApiResponse<Map<String,Object>> binding(@RequestParam(name = "productCode") @NotBlank String productCode) { return ApiResponse.ok(tasks.bindingFor(AuthPrincipal.required(), productCode)); }
  @GetMapping("/{id}") public ApiResponse<Map<String,Object>> get(@PathVariable Long id) { return ApiResponse.ok(tasks.task(id)); }
  @GetMapping public ApiResponse<Map<String,Object>> list(@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@RequestParam(required=false) Integer status,@RequestParam(required=false) String startedFrom,@RequestParam(required=false) String startedTo,@RequestParam(required=false) String model) { AuthPrincipal.requireAdmin(); return ApiResponse.ok(tasks.page(Math.max(1,page),tasks.normalizePageSize(pageSize),status,startedFrom,startedTo,model)); }
  @GetMapping("/models") public ApiResponse<List<String>> models() { AuthPrincipal.requireAdmin(); return ApiResponse.ok(tasks.models()); }
  @GetMapping("/active") public ApiResponse<Map<String,Object>> active() { return ApiResponse.ok(tasks.activeFor(AuthPrincipal.required())); }
  @PutMapping("/{id}/steps/{stepNo}") public ApiResponse<Map<String,Object>> step(@PathVariable Long id,@PathVariable Integer stepNo,@RequestBody StepRequest request) { return ApiResponse.ok(tasks.recordStep(AuthPrincipal.required(),id,stepNo,request.values)); }
  @PostMapping("/{id}/stations/{stationId}/confirm") public ApiResponse<Map<String,Object>> confirm(@PathVariable Long id,@PathVariable Long stationId,@RequestBody ConfirmationRequest request) { return ApiResponse.ok(tasks.confirmStation(AuthPrincipal.required(),id,stationId,request.items)); }
  @PutMapping("/{id}/steel-no") public ApiResponse<Map<String,Object>> steelNo(@PathVariable Long id,@Valid @RequestBody SteelNoRequest request) { return ApiResponse.ok(tasks.updateSteelNo(AuthPrincipal.required(),id,request.steelNo)); }
  @PostMapping("/{id}/complete") public ApiResponse<Map<String,Object>> complete(@PathVariable Long id,@RequestBody(required=false) CompleteRequest request) { return ApiResponse.ok(tasks.complete(AuthPrincipal.required(),id,request == null ? null : request.items)); }
  public static class CreateRequest { @NotBlank public String productCode; @NotBlank public String model; @NotBlank public String steelNo; @NotEmpty public List<Map<String,Object>> processSnapshot; }
  public static class StepRequest { public Map<String,Object> values; }
  public static class ConfirmationRequest { public Object items; }
  public static class SteelNoRequest { @NotBlank public String steelNo; }
  public static class CompleteRequest { public Object items; }
}
