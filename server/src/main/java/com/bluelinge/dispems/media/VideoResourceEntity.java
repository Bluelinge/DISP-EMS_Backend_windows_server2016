package com.bluelinge.dispems.media;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("video_resource")
public class VideoResourceEntity {
  @TableId private Long id; private String name; private String normalizedName; private Long categoryId; private Long currentVersionId;
  private Integer allowPlayback; private String batchNo; private Integer isDeleted; private Integer rowVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
  public Long getId(){return id;} public void setId(Long v){id=v;} public String getName(){return name;} public void setName(String v){name=v;}
  public String getNormalizedName(){return normalizedName;} public void setNormalizedName(String v){normalizedName=v;} public Long getCategoryId(){return categoryId;} public void setCategoryId(Long v){categoryId=v;}
  public Long getCurrentVersionId(){return currentVersionId;} public void setCurrentVersionId(Long v){currentVersionId=v;} public Integer getAllowPlayback(){return allowPlayback;} public void setAllowPlayback(Integer v){allowPlayback=v;}
  public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public Integer getIsDeleted(){return isDeleted;} public void setIsDeleted(Integer v){isDeleted=v;}
  public Integer getRowVersion(){return rowVersion;} public void setRowVersion(Integer v){rowVersion=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
  public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
