package com.bluelinge.dispems.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class TaskSseHub implements MessageListener {
  private final ObjectMapper json;
  private final Map<Long, Set<SseEmitter>> emitters = new ConcurrentHashMap<Long, Set<SseEmitter>>();
  public TaskSseHub(ObjectMapper json) { this.json = json; }
  public SseEmitter subscribe(Long taskId) {
    final SseEmitter emitter = new SseEmitter(0L);
    emitters.computeIfAbsent(taskId, ignored -> ConcurrentHashMap.newKeySet()).add(emitter);
    emitter.onCompletion(() -> remove(taskId, emitter));
    emitter.onTimeout(() -> remove(taskId, emitter));
    try { emitter.send(SseEmitter.event().name("connected").data("{\"taskId\":\"" + taskId + "\"}")); }
    catch (IOException e) { remove(taskId, emitter); }
    return emitter;
  }
  @Override public void onMessage(Message message, byte[] pattern) {
    try {
      Map<String, Object> event = json.readValue(message.getBody(), new TypeReference<Map<String, Object>>() { });
      Long taskId = Long.valueOf((String) event.get("taskId"));
      Set<SseEmitter> listeners = emitters.get(taskId);
      if (listeners == null) return;
      for (SseEmitter emitter : listeners) {
        try { emitter.send(SseEmitter.event().name((String) event.get("type")).data(json.writeValueAsString(event))); }
        catch (IOException e) { remove(taskId, emitter); }
      }
    } catch (Exception ignored) { }
  }
  private void remove(Long taskId, SseEmitter emitter) {
    Set<SseEmitter> values = emitters.get(taskId);
    if (values != null) { values.remove(emitter); if (values.isEmpty()) emitters.remove(taskId, values); }
  }
}
