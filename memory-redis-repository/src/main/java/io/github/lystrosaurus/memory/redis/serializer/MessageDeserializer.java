package io.github.lystrosaurus.memory.redis.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

/**
 * Custom JSON deserializer for Message objects
 */
public class MessageDeserializer extends JsonDeserializer<Message> {

  private static final Logger logger = LoggerFactory.getLogger(MessageDeserializer.class);

  private static final Map<String, Function<String, Message>> MESSAGE_FACTORIES = new ConcurrentHashMap<>();

  static {
    MESSAGE_FACTORIES.put("USER", UserMessage::new);
    MESSAGE_FACTORIES.put("ASSISTANT", AssistantMessage::new);
    MESSAGE_FACTORIES.put("SYSTEM", SystemMessage::new);
  }

  @Override
  public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    JsonNode node = p.getCodec().readTree(p);

    logger.debug("Deserializing message: {}", node);

    // If node is plain text, create a UserMessage by default
    if (node.isTextual()) {
      return new UserMessage(node.asText());
    }

    // Extract message type
    String type = extractMessageType(node);

    // Extract content
    String content = extractContent(node);

    // Create corresponding message object based on type
    return Optional.ofNullable(type).map(String::toUpperCase).map(MESSAGE_FACTORIES::get)
        .orElseGet(() -> {
          if (type == null) {
            logger.warn("Message type not found, defaulting to USER");
          } else {
            logger.warn("Unknown message type: {}, defaulting to USER", type);
          }
          return MESSAGE_FACTORIES.get("USER");
        }).apply(content);
  }

  /**
   * Extract message type from JsonNode
   */
  private String extractMessageType(JsonNode node) {
    return Optional.ofNullable(node.get("messageType"))
        .map(JsonNode::asText)
        .orElseGet(() -> Optional.ofNullable(node.get("type"))
            .map(JsonNode::asText)
            .orElseGet(
                () -> Optional.ofNullable(node.get("role")).map(n -> n.asText().toUpperCase())
                    .orElse(null)));
  }

  /**
   * Extract message content from JsonNode
   */
  private String extractContent(JsonNode node) {
    return Optional.ofNullable(node.get("content"))
        .map(JsonNode::asText)
        .orElseGet(
            () -> Optional.ofNullable(node.get("text")).map(JsonNode::asText)
                .orElseGet(node::toString));
  }
}
