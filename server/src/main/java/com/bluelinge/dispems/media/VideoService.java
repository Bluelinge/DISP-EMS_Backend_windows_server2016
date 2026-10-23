package com.bluelinge.dispems.media;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluelinge.dispems.common.AppException;
import com.bluelinge.dispems.common.TimeProvider;
import com.bluelinge.dispems.file.FileService;
import com.bluelinge.dispems.file.StoredFileEntity;
import com.bluelinge.dispems.media.mapper.VideoCategoryMapper;
import com.bluelinge.dispems.media.mapper.VideoFileVersionMapper;
import com.bluelinge.dispems.media.mapper.VideoResourceMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VideoService {
  private final VideoCategoryMapper categories;
  private final VideoResourceMapper videos;
  private final VideoFileVersionMapper versions;
  private final FileService files;
  public VideoService(VideoCategoryMapper categories, VideoResourceMapper videos, VideoFileVersionMapper versions, FileService files) {
    this.categories = categories; this.videos = videos; this.versions = versions; this.files = files;
  }
  public List<Map<String, Object>> categoryTree() {
    List<VideoCategoryEntity> all = categories.selectList(new LambdaQueryWrapper<VideoCategoryEntity>()
        .eq(VideoCategoryEntity::getIsDeleted, 0).orderByAsc(VideoCategoryEntity::getSortOrder).orderByAsc(VideoCategoryEntity::getId));
    return categoryNodes(all, null);
  }
  @Transactional
  public Map<String, Object> createCategory(String rawName, Long parentId) {
    String name = requiredName(rawName);
    int level = 0;
    if (parentId != null) {
      VideoCategoryEntity parent = requiredCategory(parentId);
      if (parent.getParentId() != null) throw AppException.invalid("视频分类最多两级");
      level = 1;
    }
    LambdaQueryWrapper<VideoCategoryEntity> duplicateQuery = new LambdaQueryWrapper<VideoCategoryEntity>()
        .eq(VideoCategoryEntity::getIsDeleted, 0).eq(VideoCategoryEntity::getName, name);
    if (parentId == null) duplicateQuery.isNull(VideoCategoryEntity::getParentId);
    else duplicateQuery.eq(VideoCategoryEntity::getParentId, parentId);
    Long duplicate = categories.selectCount(duplicateQuery);
    if (duplicate.longValue() > 0) throw AppException.conflict("同级分类名称不能重复");
    LambdaQueryWrapper<VideoCategoryEntity> siblingQuery = new LambdaQueryWrapper<VideoCategoryEntity>().eq(VideoCategoryEntity::getIsDeleted, 0);
    if (parentId == null) siblingQuery.isNull(VideoCategoryEntity::getParentId);
    else siblingQuery.eq(VideoCategoryEntity::getParentId, parentId);
    Long max = categories.selectCount(siblingQuery);
    LocalDateTime now = TimeProvider.now();
    VideoCategoryEntity entity = new VideoCategoryEntity();
    entity.setName(name); entity.setParentId(parentId); entity.setLevelNo(level); entity.setSortOrder(max.intValue() + 1);
    entity.setIsDeleted(0); entity.setRowVersion(1); entity.setCreatedAt(now); entity.setUpdatedAt(now);
    categories.insert(entity); return categoryMap(entity, 0, 0);
  }
  @Transactional
  public Map<String, Object> renameCategory(Long id, String rawName, int expectedVersion) {
    VideoCategoryEntity category = requiredCategory(id); checkVersion(category.getRowVersion(), expectedVersion);
    String name = requiredName(rawName);
    LambdaQueryWrapper<VideoCategoryEntity> duplicateQuery = new LambdaQueryWrapper<VideoCategoryEntity>()
        .eq(VideoCategoryEntity::getIsDeleted, 0).eq(VideoCategoryEntity::getName, name).ne(VideoCategoryEntity::getId, id);
    if (category.getParentId() == null) duplicateQuery.isNull(VideoCategoryEntity::getParentId);
    else duplicateQuery.eq(VideoCategoryEntity::getParentId, category.getParentId());
    Long duplicate = categories.selectCount(duplicateQuery);
    if (duplicate.longValue() > 0) throw AppException.conflict("同级分类名称不能重复");
    category.setName(name); category.setRowVersion(category.getRowVersion() + 1); category.setUpdatedAt(TimeProvider.now());
    categories.updateById(category); return categoryMap(category, 0, 0);
  }
  @Transactional
  public void deleteCategory(Long id, int expectedVersion) {
    VideoCategoryEntity category = requiredCategory(id); checkVersion(category.getRowVersion(), expectedVersion);
    Long children = categories.selectCount(new LambdaQueryWrapper<VideoCategoryEntity>().eq(VideoCategoryEntity::getParentId, id).eq(VideoCategoryEntity::getIsDeleted, 0));
    Long videoCount = videos.selectCount(new LambdaQueryWrapper<VideoResourceEntity>().eq(VideoResourceEntity::getCategoryId, id).eq(VideoResourceEntity::getIsDeleted, 0));
    if (children.longValue() > 0 || videoCount.longValue() > 0) throw AppException.invalid("分类下仍有子分类或视频资源");
    category.setIsDeleted(1); category.setRowVersion(category.getRowVersion() + 1); category.setUpdatedAt(TimeProvider.now());
    categories.updateById(category);
  }
  public Map<String, Object> pageVideos(int page, int pageSize, String keyword, Long categoryId) {
    LambdaQueryWrapper<VideoResourceEntity> query = new LambdaQueryWrapper<VideoResourceEntity>().eq(VideoResourceEntity::getIsDeleted, 0);
    if (keyword != null && !keyword.trim().isEmpty()) query.like(VideoResourceEntity::getName, keyword.trim());
    if (categoryId != null) query.eq(VideoResourceEntity::getCategoryId, categoryId);
    Page<VideoResourceEntity> result = videos.selectPage(new Page<VideoResourceEntity>(page, pageSize), query.orderByDesc(VideoResourceEntity::getUpdatedAt));
    List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
    for (VideoResourceEntity video : result.getRecords()) items.add(videoMap(video));
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    data.put("items", items); data.put("page", page); data.put("pageSize", pageSize); data.put("total", result.getTotal());
    return data;
  }
  public Map<String, Object> resolveByName(String name) {
    String normalized = normalizeName(requiredVideoName(name));
    VideoResourceEntity video = videos.selectOne(new LambdaQueryWrapper<VideoResourceEntity>()
        .eq(VideoResourceEntity::getNormalizedName, normalized).eq(VideoResourceEntity::getIsDeleted, 0));
    if (video == null) throw AppException.notFound("步骤绑定的视频资源不存在或已删除");
    return videoMap(video);
  }
  @Transactional
  public Map<String, Object> createVideos(Long categoryId, List<VideoInput> inputs, int replaceMode) {
    requiredCategory(categoryId);
    if (inputs == null || inputs.isEmpty()) throw AppException.badRequest("请至少提交一个视频文件");
    String batch = "B-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    List<Map<String, Object>> output = new ArrayList<Map<String, Object>>();
    for (VideoInput input : inputs) {
      StoredFileEntity file = files.required(input.fileId);
      if (file.getFileKind().intValue() != FileService.KIND_VIDEO) throw AppException.badRequest("只能关联视频文件");
      String name = requiredVideoName(input.name);
      String normalized = normalizeName(name);
      VideoResourceEntity existing = videos.selectOne(new LambdaQueryWrapper<VideoResourceEntity>()
          .eq(VideoResourceEntity::getNormalizedName, normalized));
      if (existing != null) {
        if (replaceMode != 1) throw new AppException(HttpStatus.UNPROCESSABLE_ENTITY, 1013, "存在同名视频，请确认替换");
        output.add(replace(existing, file, name, categoryId, input.expectedVersion, batch));
      } else {
        VideoResourceEntity video = new VideoResourceEntity();
        LocalDateTime now = TimeProvider.now();
        video.setName(name); video.setNormalizedName(normalized); video.setCategoryId(categoryId); video.setAllowPlayback(1);
        video.setBatchNo(batch); video.setIsDeleted(0); video.setRowVersion(1); video.setCreatedAt(now); video.setUpdatedAt(now);
        videos.insert(video);
        VideoFileVersionEntity version = version(video.getId(), 1, file.getId(), batch);
        versions.insert(version); video.setCurrentVersionId(version.getId()); videos.updateById(video);
        output.add(videoMap(video));
      }
    }
    Map<String, Object> response = new LinkedHashMap<String, Object>(); response.put("batchNo", batch); response.put("items", output); return response;
  }
  @Transactional
  public Map<String, Object> replaceVersion(Long id, Long fileId, String name, Long categoryId, int expectedVersion) {
    VideoResourceEntity video = requiredVideo(id); StoredFileEntity file = files.required(fileId);
    if (file.getFileKind().intValue() != FileService.KIND_VIDEO) throw AppException.badRequest("只能关联视频文件");
    return replace(video, file, requiredVideoName(name), categoryId, expectedVersion, "B-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12));
  }
  public Map<String, Object> playback(Long id) {
    VideoResourceEntity video = requiredVideo(id);
    Map<String, Object> result = videoMap(video);
    if (video.getAllowPlayback().intValue() != 1) { result.put("playable", 0); result.put("unavailableCode", 1); return result; }
    VideoFileVersionEntity version = versions.selectById(video.getCurrentVersionId());
    StoredFileEntity file = version == null ? null : files.required(version.getFileId());
    if (file == null || file.getProcessingState().intValue() != FileService.STATE_READY) {
      result.put("playable", 0); result.put("unavailableCode", file == null ? 3 : 2); return result;
    }
    result.put("playable", 1); result.put("versionId", String.valueOf(version.getId())); result.put("url", files.signedUrl(file)); return result;
  }
  @Transactional
  public void deleteVideo(Long id, int expectedVersion) {
    VideoResourceEntity video = requiredVideo(id); checkVersion(video.getRowVersion(), expectedVersion);
    video.setIsDeleted(1); video.setRowVersion(video.getRowVersion() + 1); video.setUpdatedAt(TimeProvider.now()); videos.updateById(video);
  }
  private Map<String, Object> replace(VideoResourceEntity video, StoredFileEntity file, String name, Long categoryId, Integer expectedVersion, String batch) {
    if (video.getIsDeleted().intValue() == 0) checkVersion(video.getRowVersion(), expectedVersion);
    if (!video.getNormalizedName().equals(normalizeName(name))) throw AppException.badRequest("替换视频不能改变标准名称");
    requiredCategory(categoryId);
    Long count = versions.selectCount(new LambdaQueryWrapper<VideoFileVersionEntity>().eq(VideoFileVersionEntity::getVideoId, video.getId()));
    VideoFileVersionEntity version = version(video.getId(), count.intValue() + 1, file.getId(), batch); versions.insert(version);
    video.setName(name); video.setCategoryId(categoryId); video.setCurrentVersionId(version.getId()); video.setBatchNo(batch); video.setIsDeleted(0);
    video.setRowVersion(video.getRowVersion() + 1); video.setUpdatedAt(TimeProvider.now()); videos.updateById(video); return videoMap(video);
  }
  private VideoFileVersionEntity version(Long videoId, int no, Long fileId, String batch) {
    VideoFileVersionEntity value = new VideoFileVersionEntity();
    value.setVideoId(videoId); value.setVersionNo(no); value.setFileId(fileId); value.setBatchNo(batch); value.setCreatedAt(TimeProvider.now()); return value;
  }
  private List<Map<String, Object>> categoryNodes(List<VideoCategoryEntity> all, Long parent) {
    List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
    for (VideoCategoryEntity category : all) if (parent == null ? category.getParentId() == null : parent.equals(category.getParentId())) {
      List<Map<String, Object>> children = categoryNodes(all, category.getId());
      int direct = videos.selectCount(new LambdaQueryWrapper<VideoResourceEntity>().eq(VideoResourceEntity::getCategoryId, category.getId()).eq(VideoResourceEntity::getIsDeleted, 0)).intValue();
      Map<String, Object> value = categoryMap(category, direct, direct + childVideoCount(children)); value.put("children", children); result.add(value);
    }
    return result;
  }
  @SuppressWarnings("unchecked") private int childVideoCount(List<Map<String, Object>> nodes) {
    int count = 0; for (Map<String, Object> node : nodes) count += ((Number) node.get("totalVideoCount")).intValue(); return count;
  }
  private Map<String, Object> categoryMap(VideoCategoryEntity item, int direct, int total) {
    Map<String, Object> value = new LinkedHashMap<String, Object>(); value.put("id", String.valueOf(item.getId()));
    value.put("name", item.getName()); value.put("parentId", item.getParentId() == null ? null : String.valueOf(item.getParentId()));
    value.put("levelNo", item.getLevelNo()); value.put("sortOrder", item.getSortOrder()); value.put("rowVersion", item.getRowVersion());
    value.put("directVideoCount", direct); value.put("totalVideoCount", total); return value;
  }
  private Map<String, Object> videoMap(VideoResourceEntity video) {
    Map<String, Object> result = new LinkedHashMap<String, Object>(); result.put("id", String.valueOf(video.getId()));
    result.put("name", video.getName()); result.put("categoryId", String.valueOf(video.getCategoryId()));
    result.put("allowPlayback", video.getAllowPlayback()); result.put("batchNo", video.getBatchNo()); result.put("rowVersion", video.getRowVersion());
    VideoFileVersionEntity version = video.getCurrentVersionId() == null ? null : versions.selectById(video.getCurrentVersionId());
    StoredFileEntity file = version == null ? null : files.required(version.getFileId());
    result.put("fileId", file == null ? null : String.valueOf(file.getId()));
    result.put("processingState", file == null ? 2 : file.getProcessingState());
    result.put("durationMs", file == null ? null : file.getDurationMs());
    result.put("duration", formatDuration(file == null ? null : file.getDurationMs()));
    return result;
  }
  private String formatDuration(Long durationMs) {
    if (durationMs == null) return "待解析";
    long seconds = durationMs.longValue() / 1000L;
    return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
  }
  private VideoCategoryEntity requiredCategory(Long id) { VideoCategoryEntity value = categories.selectById(id); if (value == null || value.getIsDeleted().intValue() == 1) throw AppException.notFound("视频分类不存在"); return value; }
  private VideoResourceEntity requiredVideo(Long id) { VideoResourceEntity value = videos.selectById(id); if (value == null || value.getIsDeleted().intValue() == 1) throw AppException.notFound("视频资源不存在"); return value; }
  private String requiredName(String raw) { if (raw == null || raw.trim().isEmpty()) throw AppException.badRequest("请填写名称"); return raw.trim(); }
  private String requiredVideoName(String raw) { return requiredName(raw); }
  private String normalizeName(String raw) { return raw.trim().replaceAll("^.*[\\\\/]", "").replaceFirst("(?i)\\.(mp4|webm|ogg|ogv|mov|avi|mkv|m4v)$", "").toLowerCase(Locale.ROOT); }
  private void checkVersion(Integer actual, Integer expected) { if (expected == null || actual.intValue() != expected.intValue()) throw AppException.conflict("资源已被其他操作更新，请刷新后重试"); }
  public static class VideoInput { public Long fileId; public String name; public Integer expectedVersion; }
}
