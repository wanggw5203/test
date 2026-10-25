package io.testkit.adapters.kafka;

import io.testkit.basetest.message.TestMessage;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;
import java.util.concurrent.*;
import org.apache.kafka.clients.producer.*;

/**
 * Producer send confirmation is transport evidence, not proof that the business consumer succeeded.
 */
public final class KafkaPublisher implements AutoCloseable {
  private final Producer<String, String> producer;

  public KafkaPublisher(Properties config) {
    Properties p = new Properties();
    p.putAll(config);
    p.put(
        ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    p.put(
        ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringSerializer");
    p.put(ProducerConfig.ACKS_CONFIG, "all");
    p.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
    p.putIfAbsent(ProducerConfig.MAX_BLOCK_MS_CONFIG, "5000");
    p.putIfAbsent(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, "5000");
    p.putIfAbsent(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, "10000");
    producer = new KafkaProducer<>(p);
  }

  public KafkaPublisher(Producer<String, String> producer) {
    this.producer = java.util.Objects.requireNonNull(producer);
  }

  public RecordMetadata send(TestMessage message, Duration timeout) {
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive timeout required");
    ProducerRecord<String, String> record =
        new ProducerRecord<>(message.topic(), message.key(), message.body());
    message
        .headers()
        .forEach((key, value) -> record.headers().add(key, value.getBytes(StandardCharsets.UTF_8)));
    try {
      return producer.send(record).get(timeout.toNanos(), TimeUnit.NANOSECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new KafkaDeliveryException(KafkaDeliveryException.Outcome.OUTCOME_UNKNOWN, e);
    } catch (TimeoutException e) {
      // Timing out the wait neither cancels Kafka delivery nor makes an application-level resend
      // safe.
      throw new KafkaDeliveryException(KafkaDeliveryException.Outcome.OUTCOME_UNKNOWN, e);
    } catch (ExecutionException e) {
      KafkaDeliveryException.Outcome outcome =
          e.getCause() instanceof org.apache.kafka.common.errors.TimeoutException
              ? KafkaDeliveryException.Outcome.OUTCOME_UNKNOWN
              : KafkaDeliveryException.Outcome.ACKNOWLEDGEMENT_FAILED;
      throw new KafkaDeliveryException(outcome, e.getCause());
    }
  }

  @Override
  public void close() {
    producer.close(Duration.ofSeconds(5));
  }
}
