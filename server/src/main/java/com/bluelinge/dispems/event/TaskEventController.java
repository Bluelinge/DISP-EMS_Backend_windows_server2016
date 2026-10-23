package com.bluelinge.dispems.event;

import com.bluelinge.dispems.auth.AuthPrincipal;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/tasks")
public class TaskEventController {
  private final TaskSseHub hub;
  public TaskEventController(TaskSseHub hub) { this.hub = hub; }
  @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter events(@PathVariable Long id) {
    AuthPrincipal.required();
    return hub.subscribe(id);
  }
}
