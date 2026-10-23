package com.bluelinge.dispems.file;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.bluelinge.dispems.config.DispProperties;
import com.bluelinge.dispems.file.mapper.StoredFileMapper;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.TimeUnit;
import org.springframework.scheduling.annotation.Async;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class VideoProbeService {
  private final StoredFileMapper files;
  private final MinioClient minio;
  private final DispProperties properties;
  public VideoProbeService(StoredFileMapper files, @Qualifier("minioClient") MinioClient minio, DispProperties properties) {
    this.files = files; this.minio = minio; this.properties = properties;
  }
  @Async
  public void inspect(StoredFileEntity file) {
    File local = null;
    try {
      local = File.createTempFile("disp-ems-video-", ".media");
      try (InputStream input = minio.getObject(GetObjectArgs.builder().bucket(properties.getStorage().getBucket()).object(file.getObjectKey()).build())) {
        Files.copy(input, local.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
      Process process = new ProcessBuilder(properties.getStorage().getFfprobePath(), "-v", "error", "-show_entries", "format=duration",
          "-of", "default=noprint_wrappers=1:nokey=1", local.getAbsolutePath()).redirectErrorStream(true).start();
      String output = new String(readAll(process.getInputStream()), StandardCharsets.UTF_8).trim();
      if (!process.waitFor(30, TimeUnit.SECONDS) || process.exitValue() != 0) throw new IllegalStateException("ffprobe failed");
      long duration = Math.round(Double.parseDouble(output) * 1000);
      files.update(null, new LambdaUpdateWrapper<StoredFileEntity>().eq(StoredFileEntity::getId, file.getId())
          .set(StoredFileEntity::getDurationMs, duration).set(StoredFileEntity::getProcessingState, FileService.STATE_READY)
          .setSql("row_version = row_version + 1"));
    } catch (Exception e) {
      files.update(null, new LambdaUpdateWrapper<StoredFileEntity>().eq(StoredFileEntity::getId, file.getId())
          .set(StoredFileEntity::getProcessingState, 2).setSql("row_version = row_version + 1"));
    } finally {
      if (local != null) local.delete();
    }
  }
  private byte[] readAll(InputStream stream) throws java.io.IOException {
    java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
    byte[] buffer = new byte[8192]; int count;
    while ((count = stream.read(buffer)) >= 0) output.write(buffer, 0, count);
    return output.toByteArray();
  }
}
