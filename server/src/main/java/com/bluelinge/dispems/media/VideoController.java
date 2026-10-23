package com.bluelinge.dispems.media;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import java.util.List;
import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class VideoController {
  private final VideoService service;
  public VideoController(VideoService service) { this.service = service; }
  @GetMapping("/video-categories/tree") public ApiResponse<List<Map<String,Object>>> tree() { AuthPrincipal.required(); return ApiResponse.ok(service.categoryTree()); }
  @PostMapping("/video-categories") public ApiResponse<Map<String,Object>> createCategory(@Valid @RequestBody CategoryRequest request) { AuthPrincipal.requireAdmin(); return ApiResponse.ok(service.createCategory(request.name, request.parentId)); }
  @PatchMapping("/video-categories/{id}") public ApiResponse<Map<String,Object>> renameCategory(@PathVariable Long id, @Valid @RequestBody RenameRequest request) { AuthPrincipal.requireAdmin(); return ApiResponse.ok(service.renameCategory(id, request.name, request.expectedVersion)); }
  @DeleteMapping("/video-categories/{id}") public ApiResponse<Void> deleteCategory(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) { AuthPrincipal.requireAdmin(); service.deleteCategory(id, expectedVersion); return ApiResponse.ok(null); }
  @GetMapping("/videos") public ApiResponse<Map<String,Object>> list(@RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize, @RequestParam(required=false) String keyword, @RequestParam(required=false) Long categoryId) { AuthPrincipal.required(); return ApiResponse.ok(service.pageVideos(Math.max(1,page), Math.min(100,Math.max(1,pageSize)), keyword, categoryId)); }
  @GetMapping("/videos/resolve") public ApiResponse<Map<String,Object>> resolve(@RequestParam(name = "name") String name) { AuthPrincipal.required(); return ApiResponse.ok(service.resolveByName(name)); }
  @PostMapping("/videos") public ApiResponse<Map<String,Object>> createVideos(@Valid @RequestBody CreateVideosRequest request) { AuthPrincipal.requireAdmin(); return ApiResponse.ok(service.createVideos(request.categoryId, request.files, request.replaceMode == null ? 0 : request.replaceMode)); }
  @PostMapping("/videos/{id}/versions") public ApiResponse<Map<String,Object>> replace(@PathVariable Long id, @Valid @RequestBody VersionRequest request) { AuthPrincipal.requireAdmin(); return ApiResponse.ok(service.replaceVersion(id, request.fileId, request.name, request.categoryId, request.expectedVersion)); }
  @GetMapping("/videos/{id}/playback") public ApiResponse<Map<String,Object>> playback(@PathVariable Long id) { AuthPrincipal.required(); return ApiResponse.ok(service.playback(id)); }
  @DeleteMapping("/videos/{id}") public ApiResponse<Void> delete(@PathVariable Long id, @RequestParam(name = "expectedVersion") Integer expectedVersion) { AuthPrincipal.requireAdmin(); service.deleteVideo(id, expectedVersion); return ApiResponse.ok(null); }
  public static class CategoryRequest { @NotBlank public String name; public Long parentId; }
  public static class RenameRequest { @NotBlank public String name; @NotNull public Integer expectedVersion; }
  public static class CreateVideosRequest { @NotNull public Long categoryId; @NotNull public List<VideoService.VideoInput> files; public Integer replaceMode; }
  public static class VersionRequest { @NotNull public Long fileId; @NotBlank public String name; @NotNull public Long categoryId; @NotNull public Integer expectedVersion; }
}
