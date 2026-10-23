package com.bluelinge.dispems.media;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
@TableName("video_file_version")
public class VideoFileVersionEntity {
  @TableId private Long id; private Long videoId; private Integer versionNo; private Long fileId; private String batchNo; private LocalDateTime createdAt;
  public Long getId(){return id;} public void setId(Long v){id=v;} public Long getVideoId(){return videoId;} public void setVideoId(Long v){videoId=v;}
  public Integer getVersionNo(){return versionNo;} public void setVersionNo(Integer v){versionNo=v;} public Long getFileId(){return fileId;} public void setFileId(Long v){fileId=v;}
  public String getBatchNo(){return batchNo;} public void setBatchNo(String v){batchNo=v;} public LocalDateTime getCreatedAt(){return createdAt;} public void setCreatedAt(LocalDateTime v){createdAt=v;}
}
