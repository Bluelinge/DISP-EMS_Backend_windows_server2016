package com.bluelinge.dispems.event;

import com.bluelinge.dispems.common.TimeProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class TaskEventPublisher {
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  public TaskEventPublisher(StringRedisTemplate redis, ObjectMapper json) { this.redis = redis; this.json = json; }
  public void publish(Long taskId, String type, Object payload) {
    try {
      Map<String, Object> event = new LinkedHashMap<String, Object>();
      event.put("taskId", String.valueOf(taskId)); event.put("type", type); event.put("asOf", TimeProvider.now().toString() + "Z"); event.put("payload", payload);
      redis.convertAndSend("disp:task:" + taskId, json.writeValueAsString(event));
    } catch (Exception e) {
      throw new IllegalStateException("任务事件发布失败", e);
    }
  }
}
