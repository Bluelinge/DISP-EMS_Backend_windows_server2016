package com.bluelinge.dispems.file;

import com.bluelinge.dispems.auth.AuthPrincipal;
import com.bluelinge.dispems.common.ApiResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
  private final FileService service;
  public FileController(FileService service) { this.service = service; }
  @PostMapping
  public ApiResponse<Map<String, Object>> upload(@RequestParam("file") MultipartFile file,
      @RequestParam("fileKind") @Min(0) @Max(3) Integer fileKind) {
    AuthPrincipal.requireAdmin();
    return ApiResponse.ok(service.upload(AuthPrincipal.required(), file, fileKind));
  }
  @GetMapping("/{id}")
  public ApiResponse<Map<String, Object>> detail(@PathVariable Long id) {
    AuthPrincipal.required(); return ApiResponse.ok(service.fileMap(service.required(id)));
  }
  @GetMapping("/{id}/url")
  public ApiResponse<Map<String, Object>> url(@PathVariable Long id) {
    AuthPrincipal.required();
    StoredFileEntity file = service.required(id);
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("url", service.signedUrl(file)); data.put("expiresInSeconds", 900);
    return ApiResponse.ok(data);
  }
}
