package io.testkit.adapters.kafka;

import java.time.Duration;
import java.util.*;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;

/**
 * Tests a consumer handler: rewind handler failures; stop after an unconfirmed commit to avoid
 * repeating side effects.
 */
public final class KafkaConsumerRunner implements AutoCloseable {
  private final Consumer<String, String> consumer;
  private final Thread owner = Thread.currentThread();
  private final Deque<ConsumerRecord<String, String>> pending = new ArrayDeque<>();
  private boolean closed;
  private boolean unusable;

  public KafkaConsumerRunner(Properties config, String environment, String topic) {
    this(new KafkaConsumer<>(KafkaMessageProbe.properties(config, environment)), topic);
  }

  public KafkaConsumerRunner(Consumer<String, String> consumer, String topic) {
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

  /**
   * Resolve assignment and starting offsets BEFORE the action that produces the records under test.
   */
  public void ready(Duration timeout) {
    check();
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive initialization timeout required");
    long started = System.nanoTime();
    while (consumer.assignment().isEmpty()) {
      long remaining = timeout.toNanos() - (System.nanoTime() - started);
      if (remaining <= 0) throw new AssertionError("Kafka consumer assignment timed out");
      buffer(
          consumer.poll(Duration.ofNanos(Math.min(remaining, Duration.ofMillis(100).toNanos()))));
    }
    for (var partition : consumer.assignment()) {
      long remaining = timeout.toNanos() - (System.nanoTime() - started);
      if (remaining <= 0) throw new AssertionError("Kafka consumer starting position timed out");
      consumer.position(partition, Duration.ofNanos(remaining));
    }
  }

  public boolean consumeOne(
      Duration timeout, java.util.function.Consumer<ConsumerRecord<String, String>> handler) {
    check();
    Objects.requireNonNull(handler, "Kafka handler required");
    if (timeout.isNegative() || timeout.isZero())
      throw new IllegalArgumentException("Positive poll timeout required");
    if (pending.isEmpty()) buffer(consumer.poll(timeout));
    if (pending.isEmpty()) return false;
    var record = pending.removeFirst();
    var partition = new TopicPartition(record.topic(), record.partition());
    try {
      handler.accept(record);
    } catch (RuntimeException | Error failure) {
      try {
        Map<TopicPartition, Long> rewind = new HashMap<>();
        rewind.put(partition, record.offset());
        for (var unread : pending)
          rewind.merge(
              new TopicPartition(unread.topic(), unread.partition()), unread.offset(), Math::min);
        rewind.forEach(consumer::seek);
      } catch (RuntimeException | Error rollback) {
        // A partial seek must not permit a later commit to skip the failed record.
        unusable = true;
        if (rollback != failure) failure.addSuppressed(rollback);
      } finally {
        pending.clear();
      }
      throw failure;
    }
    try {
      consumer.commitSync(Map.of(partition, new OffsetAndMetadata(record.offset() + 1)), timeout);
    } catch (RuntimeException failure) {
      unusable = true;
      pending.clear();
      throw new KafkaOffsetCommitException(record.offset() + 1, failure);
    } catch (Error failure) {
      unusable = true;
      pending.clear();
      throw failure;
    }
    return true;
  }

  private void buffer(ConsumerRecords<String, String> records) {
    for (var record : records) {
      if (pending.size() >= 1000) {
        // poll has already advanced positions for the entire batch. Never resume after losing its
        // tail.
        unusable = true;
        pending.clear();
        throw new IllegalStateException(
            "Consumer buffer capacity exceeded; close and recreate the runner");
      }
      pending.addLast(record);
    }
  }

  private void check() {
    requireOwner();
    if (closed || unusable)
      throw new IllegalStateException(
          "Open, recoverable consumer required; close and recreate the runner");
  }

  private void requireOwner() {
    if (owner != Thread.currentThread())
      throw new IllegalStateException("Consumer owner thread required");
  }

  @Override
  public void close() {
    if (closed) return;
    requireOwner();
    closed = true;
    pending.clear();
    consumer.close(Duration.ofSeconds(5));
  }
}
