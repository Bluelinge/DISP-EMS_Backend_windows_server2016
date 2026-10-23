package com.bluelinge.dispems.media;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("video_category")
public class VideoCategoryEntity {
  @TableId private Long id; private String name; private Long parentId; private Integer levelNo; private Integer sortOrder;
  private Integer isDeleted; private Integer rowVersion; private LocalDateTime createdAt; private LocalDateTime updatedAt;
  public Long getId(){return id;} public void setId(Long v){id=v;} public String getName(){return name;} public void setName(String v){name=v;}
  public Long getParentId(){return parentId;} public void setParentId(Long v){parentId=v;} public Integer getLevelNo(){return levelNo;} public void setLevelNo(Integer v){levelNo=v;}
  public Integer getSortOrder(){return sortOrder;} public void setSortOrder(Integer v){sortOrder=v;} public Integer getIsDeleted(){return isDeleted;} public void setIsDeleted(Integer v){isDeleted=v;}
  public Integer getRowVersion(){return rowVersion;} public void setRowVersion(Integer v){rowVersion=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
  public LocalDateTime getUpdatedAt(){return updatedAt;} public void setUpdatedAt(LocalDateTime v){updatedAt=v;}
}
