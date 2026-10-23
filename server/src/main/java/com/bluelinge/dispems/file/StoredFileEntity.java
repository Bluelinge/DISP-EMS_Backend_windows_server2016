package com.bluelinge.dispems.file;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("stored_file")
public class StoredFileEntity {
  @TableId private Long id;
  private String originalName;
  private String objectKey;
  private String mimeType;
  private Long byteSize;
  private String sha256;
  private Integer fileKind;
  private Integer processingState;
  private Long durationMs;
  private Long createdBy;
  private Integer isDeleted;
  private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;
  public Long getId() { return id; } public void setId(Long v) { id=v; }
  public String getOriginalName() { return originalName; } public void setOriginalName(String v) { originalName=v; }
  public String getObjectKey() { return objectKey; } public void setObjectKey(String v) { objectKey=v; }
  public String getMimeType() { return mimeType; } public void setMimeType(String v) { mimeType=v; }
  public Long getByteSize() { return byteSize; } public void setByteSize(Long v) { byteSize=v; }
  public String getSha256() { return sha256; } public void setSha256(String v) { sha256=v; }
  public Integer getFileKind() { return fileKind; } public void setFileKind(Integer v) { fileKind=v; }
  public Integer getProcessingState() { return processingState; } public void setProcessingState(Integer v) { processingState=v; }
  public Long getDurationMs() { return durationMs; } public void setDurationMs(Long v) { durationMs=v; }
  public Long getCreatedBy() { return createdBy; } public void setCreatedBy(Long v) { createdBy=v; }
  public Integer getIsDeleted() { return isDeleted; } public void setIsDeleted(Integer v) { isDeleted=v; }
  public Integer getRowVersion() { return rowVersion; } public void setRowVersion(Integer v) { rowVersion=v; }
  public LocalDateTime getCreatedAt() { return createdAt; } public void setCreatedAt(LocalDateTime v) { createdAt=v; }
  public LocalDateTime getUpdatedAt() { return updatedAt; } public void setUpdatedAt(LocalDateTime v) { updatedAt=v; }
}
