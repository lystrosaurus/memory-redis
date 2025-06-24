package io.github.lystrosaurus.memory.redis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.github.lystrosaurus.memory.redis.serializer.MessageDeserializer;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.Assert;

/**
 * Redis implementation of ChatMemoryRepository
 */
public class RedisChatMemoryRepository implements ChatMemoryRepository {

  private static final String DEFAULT_KEY_PREFIX = "spring_ai_chat_memory:";

  private final StringRedisTemplate redisTemplate;

  private final ObjectMapper objectMapper;

  private RedisChatMemoryRepository(StringRedisTemplate redisTemplate) {
    Assert.notNull(redisTemplate, "redisTemplate cannot be null");
    this.redisTemplate = redisTemplate;
    this.objectMapper = new ObjectMapper();
    SimpleModule module = new SimpleModule();
    module.addDeserializer(Message.class, new MessageDeserializer());
    this.objectMapper.registerModule(module);
  }

  public static RedisBuilder builder() {
    return new RedisBuilder();
  }

  public static class RedisBuilder {

    private StringRedisTemplate redisTemplate1;

    public RedisBuilder stringRedisTemplate(StringRedisTemplate redisTemplate) {
      this.redisTemplate1 = redisTemplate;
      return this;
    }

    public RedisChatMemoryRepository build() {
      return new RedisChatMemoryRepository(redisTemplate1);
    }

  }

  @Override
  public List<String> findConversationIds() {
    List<String> keys = new ArrayList<>(redisTemplate.keys(DEFAULT_KEY_PREFIX + "*"));
    return keys.stream().map(key -> key.substring(DEFAULT_KEY_PREFIX.length())).toList();

  }

  @Override
  public List<Message> findByConversationId(String conversationId) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");

    String key = DEFAULT_KEY_PREFIX + conversationId;
    List<String> messageStrings = redisTemplate.opsForList().range(key, 0, -1);
    List<Message> messages = new ArrayList<>();
    if (messageStrings == null) {
      return messages;
    }

    for (String messageString : messageStrings) {
      try {
        Message message = objectMapper.readValue(messageString, Message.class);
        messages.add(message);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Error deserializing message", e);
      }
    }
    return messages;

  }

  @Override
  public void saveAll(String conversationId, List<Message> messages) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");
    Assert.notNull(messages, "messages cannot be null");
    Assert.noNullElements(messages, "messages cannot contain null elements");

    String key = DEFAULT_KEY_PREFIX + conversationId;
    // Clear existing messages first
    deleteByConversationId(conversationId);

    // Add all messages in order
    for (Message message : messages) {
      try {
        String messageJson = objectMapper.writeValueAsString(message);
        redisTemplate.opsForList().rightPush(key, messageJson);
      } catch (JsonProcessingException e) {
        throw new RuntimeException("Error serializing message", e);
      }
    }
  }

  @Override
  public void deleteByConversationId(String conversationId) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");
    String key = DEFAULT_KEY_PREFIX + conversationId;
    redisTemplate.delete(key);

  }

  /**
   * Clear messages over the limit for a conversation
   *
   * @param conversationId the conversation ID
   * @param maxLimit       maximum number of messages to keep
   * @param deleteSize     number of messages to delete when over limit
   */
  public void clearOverLimit(String conversationId, int maxLimit, int deleteSize) {
    Assert.hasText(conversationId, "conversationId cannot be null or empty");

    String key = DEFAULT_KEY_PREFIX + conversationId;
    List<String> all = redisTemplate.opsForList().range(key, 0, -1);

    if (all != null && all.size() >= maxLimit) {
      all = all.stream().skip(Math.max(0, deleteSize)).toList();
      deleteByConversationId(conversationId);
      for (String message : all) {
        redisTemplate.opsForList().rightPush(key, message);
      }
    }

  }

}