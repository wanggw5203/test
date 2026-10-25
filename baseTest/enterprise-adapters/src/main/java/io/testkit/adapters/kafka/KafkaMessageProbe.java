package io.testkit.adapters.kafka;

import io.testkit.basetest.message.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.function.Predicate;
import org.apache.kafka.clients.consumer.*;

/**
 * Dedicated consumer group, owner-thread only, no auto-commit. Retains unread records from a poll
 * batch.
 */
public final class KafkaMessageProbe implements MessageProbe {
  private final Consumer<String, String> consumer;
  private final Thread owner = Thread.currentThread();
  private final ArrayDeque<TestMessage> buffered = new ArrayDeque<>();
  private boolean closed;

  public KafkaMessageProbe(Properties config, String environment, String topic) {
    this(new KafkaConsumer<>(properties(config, environment)), topic);
  }

  /**
   * Injection seam for Kafka MockConsumer and externally configured clients. Consumer ownership is
   * transferred.
   */
  public KafkaMessageProbe(Consumer<String, String> consumer, String topic) {
    this.consumer = Objects.requireNonNull(consumer);
    try {
      consumer.subscribe(List.of(topic));
    } catch (RuntimeException | Error failure) {
      try {
        consumer.close(Duration.ofSeconds(5));
      } catch (RuntimeException | Error close) {
        if (close != failure) failure.addSuppressed(close);
      }
      throw failure;
    }
  }

  static Properties properties(Properties input, String environment) {
    if (environment == null || !environment.matches("[A-Za-z0-9_-]+"))
      throw new IllegalArgumentException("Invalid Kafka environment");
    Properties p = new Properties();
    p.putAll(input);
    p.put(ConsumerConfig.GROUP_ID_CONFIG, "testkit-" + environment + "-" + UUID.randomUUID());
    p.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    p.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest");
    p.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, "100");
    p.putIfAbsent(ConsumerConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "5000");
    p.put(
        ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    p.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
        "org.apache.kafka.common.serialization.StringDeserializer");
    return p;
  }

  /** Call BEFORE the action that produces the message; positions resolved before returning. */
  public void ready(Duration timeout) {
    check();
    positive(timeout);
    long start = System.nanoTime();
    while (consumer.assignment().isEmpty()) {
      long remaining = timeout.toNanos() - (System.nanoTime() - start);
      if (remaining <= 0) throw new AssertionError("Kafka assignment timed out");
      add(consumer.poll(Duration.ofNanos(Math.min(remaining, Duration.ofMillis(100).toNanos()))));
    }
    for (var partition : consumer.assignment()) {
      long remaining = timeout.toNanos() - (System.nanoTime() - start);
      if (remaining <= 0) throw new AssertionError("Kafka position initialization timed out");
      consumer.position(partition, Duration.ofNanos(remaining));
    }
  }

  @Override
  public TestMessage await(Predicate<TestMessage> matches, Duration timeout) {
    check();
    positive(timeout);
    long start = System.nanoTime();
    do {
      while (!buffered.isEmpty()) {
        TestMessage message = buffered.removeFirst();
        if (matches.test(message)) return message;
      }
      long remaining = timeout.toNanos() - (System.nanoTime() - start);
      if (remaining <= 0) throw new AssertionError("No correlated Kafka message before timeout");
      add(consumer.poll(Duration.ofNanos(Math.min(remaining, Duration.ofMillis(100).toNanos()))));
    } while (true);
  }

  private void add(ConsumerRecords<String, String> records) {
    for (var record : records) {
      if (buffered.size() >= 1000)
        throw new IllegalStateException("Kafka evidence buffer exceeded capacity");
      Map<String, String> headers = new HashMap<>();
      record
          .headers()
          .forEach(
              h ->
                  headers.put(
                      h.key(),
                      h.value() == null ? "" : new String(h.value(), StandardCharsets.UTF_8)));
      buffered.addLast(new TestMessage(record.topic(), record.key(), record.value(), headers));
    }
  }

  private static void positive(Duration d) {
    if (d.isNegative() || d.isZero())
      throw new IllegalArgumentException("Positive timeout required");
  }

  private void check() {
    if (Thread.currentThread() != owner)
      throw new IllegalStateException("Kafka consumer must stay on its owning thread");
    if (closed) throw new IllegalStateException("Kafka probe closed");
  }

  @Override
  public void close() {
    if (closed) return;
    check();
    closed = true;
    buffered.clear();
    consumer.close(Duration.ofSeconds(5));
  }
}
