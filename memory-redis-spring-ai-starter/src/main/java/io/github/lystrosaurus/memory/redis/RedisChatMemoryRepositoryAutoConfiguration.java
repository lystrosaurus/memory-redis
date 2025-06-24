package io.github.lystrosaurus.memory.redis;

import org.springframework.ai.model.chat.memory.autoconfigure.ChatMemoryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class, before = ChatMemoryAutoConfiguration.class)
@ConditionalOnClass({RedisChatMemoryRepository.class, StringRedisTemplate.class})
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisChatMemoryRepositoryAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public RedisChatMemoryRepository redisChatMemoryRepository(
      StringRedisTemplate stringRedisTemplate) {
    return RedisChatMemoryRepository.builder()
        .stringRedisTemplate(stringRedisTemplate)
        .build();
  }
}
