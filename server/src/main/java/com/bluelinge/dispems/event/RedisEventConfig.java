package com.bluelinge.dispems.event;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
public class RedisEventConfig {
  @Bean
  public RedisMessageListenerContainer taskEventListenerContainer(RedisConnectionFactory factory, TaskSseHub hub) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(factory);
    container.addMessageListener(hub, new PatternTopic("disp:task:*"));
    return container;
  }
}
