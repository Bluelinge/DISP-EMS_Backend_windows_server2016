package com.bluelinge.dispems.file;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluelinge.dispems.auth.CurrentUser;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.bluelinge.dispems.config.DispProperties;
import com.bluelinge.dispems.file.mapper.StoredFileMapper;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.DigestInputStream;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FileService {
  public static final int KIND_VIDEO = 0;
  public static final int KIND_ICON = 1;
  public static final int KIND_EXCEL = 2;
  public static final int KIND_EXPORT = 3;
  public static final int STATE_READY = 1;
  public static final int STATE_PROCESSING = 0;
  private final StoredFileMapper files;
  private final MinioClient minio;
  private final MinioClient publicMinio;
  private final DispProperties properties;
  private final VideoProbeService probe;
  public FileService(StoredFileMapper files, @Qualifier("minioClient") MinioClient minio,
      @Qualifier("publicMinioClient") MinioClient publicMinio, DispProperties properties, VideoProbeService probe) {
    this.files = files; this.minio = minio; this.publicMinio = publicMinio; this.properties = properties; this.probe = probe;
  }
  @Transactional
  public Map<String, Object> upload(CurrentUser user, MultipartFile multipart, int kind) {
    validate(multipart, kind);
    String objectKey = objectKey(kind, multipart.getOriginalFilename());
    boolean uploaded = false;
    try (InputStream input = multipart.getInputStream()) {
      ensureBucket();
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      DigestInputStream content = new DigestInputStream(input, digest);
      minio.putObject(PutObjectArgs.builder().bucket(properties.getStorage().getBucket()).object(objectKey)
          .stream(content, multipart.getSize(), -1)
          .contentType(safeMime(multipart)).build());
      uploaded = true;
      LocalDateTime now = TimeProvider.now();
      StoredFileEntity record = new StoredFileEntity();
      record.setOriginalName(multipart.getOriginalFilename()); record.setObjectKey(objectKey); record.setMimeType(safeMime(multipart));
      record.setByteSize(multipart.getSize()); record.setSha256(hex(digest.digest())); record.setFileKind(kind);
      record.setProcessingState(kind == KIND_VIDEO ? STATE_PROCESSING : STATE_READY); record.setCreatedBy(user.getId());
      record.setIsDeleted(0); record.setRowVersion(1); record.setCreatedAt(now); record.setUpdatedAt(now);
      files.insert(record);
      if (kind == KIND_VIDEO) probe.inspect(record);
      return fileMap(record);
    } catch (AppException e) {
      throw e;
    } catch (Exception e) {
      if (uploaded) removeQuietly(objectKey);
      throw new AppException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, 1500, "文件上传失败");
    }
  }
  public StoredFileEntity required(Long id) {
    StoredFileEntity file = files.selectOne(new LambdaQueryWrapper<StoredFileEntity>().eq(StoredFileEntity::getId, id).eq(StoredFileEntity::getIsDeleted, 0));
    if (file == null) throw AppException.notFound("文件不存在");
    return file;
  }
  public String signedUrl(StoredFileEntity file) {
    try {
      return publicMinio.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET)
          .bucket(properties.getStorage().getBucket()).object(file.getObjectKey())
          .expiry(properties.getStorage().getUrlExpiryMinutes(), TimeUnit.MINUTES).build());
    } catch (Exception e) {
      throw new AppException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, 1500, "文件地址签发失败");
    }
  }
  public Map<String, Object> fileMap(StoredFileEntity file) {
    Map<String, Object> result = new LinkedHashMap<String, Object>();
    result.put("id", String.valueOf(file.getId())); result.put("originalName", file.getOriginalName());
    result.put("mimeType", file.getMimeType()); result.put("byteSize", file.getByteSize());
    result.put("sha256", file.getSha256()); result.put("fileKind", file.getFileKind());
    result.put("processingState", file.getProcessingState()); result.put("durationMs", file.getDurationMs());
    result.put("rowVersion", file.getRowVersion()); result.put("createdAt", file.getCreatedAt().toString() + "Z");
    return result;
  }
  private void ensureBucket() throws Exception {
    if (!minio.bucketExists(BucketExistsArgs.builder().bucket(properties.getStorage().getBucket()).build())) {
      minio.makeBucket(MakeBucketArgs.builder().bucket(properties.getStorage().getBucket()).build());
    }
  }
  private void validate(MultipartFile file, int kind) {
    if (file == null || file.isEmpty()) throw AppException.badRequest("请选择文件");
    String type = safeMime(file).toLowerCase();
    String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
    if (kind == KIND_VIDEO && !(type.startsWith("video/") || name.matches(".*\\.(mp4|webm|ogg|ogv|mov|avi|mkv|m4v)$"))) throw AppException.badRequest("仅支持视频文件");
    if (kind == KIND_ICON && !(type.equals("image/png") || name.endsWith(".png"))) throw AppException.badRequest("投影图标仅支持 PNG");
    if (kind == KIND_EXCEL && !(name.endsWith(".xlsx") || name.endsWith(".xls"))) throw AppException.badRequest("仅支持 Excel 文件");
    if (kind < KIND_VIDEO || kind > KIND_EXPORT) throw AppException.badRequest("未知文件类型");
  }
  private String objectKey(int kind, String name) {
    String extension = name == null || !name.contains(".") ? "" : name.substring(name.lastIndexOf('.')).replaceAll("[^A-Za-z0-9.]", "");
    return "objects/" + kind + "/" + java.time.LocalDate.now(java.time.Clock.systemUTC()).toString().replace("-", "/") + "/"
        + UUID.randomUUID().toString().replace("-", "") + extension;
  }
  private String safeMime(MultipartFile file) { return file.getContentType() == null ? "application/octet-stream" : file.getContentType(); }
  private String hex(byte[] value) { StringBuilder builder = new StringBuilder(); for (byte item : value) builder.append(String.format("%02x", item)); return builder.toString(); }
  private void removeQuietly(String objectKey) { try { minio.removeObject(RemoveObjectArgs.builder().bucket(properties.getStorage().getBucket()).object(objectKey).build()); } catch (Exception ignored) { } }
}
