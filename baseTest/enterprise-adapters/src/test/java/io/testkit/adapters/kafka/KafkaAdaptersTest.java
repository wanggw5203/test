package io.testkit.adapters.kafka;

import io.testkit.basetest.message.TestMessage;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.testng.Assert;
import org.testng.annotations.Test;

public class KafkaAdaptersTest {
  private MockConsumer<String, String> consumer() {
    var consumer = new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST);
    TopicPartition tp = new TopicPartition("orders", 0);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(tp));
          consumer.updateBeginningOffsets(Map.of(tp, 0L));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "run-1", "a"));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "run-2", "b"));
        });
    return consumer;
  }

  @Test
  public void consumerProbePreservesUnreadBatchAndDoesNotCommit() {
    var consumer = consumer();
    try (var probe = new KafkaMessageProbe(consumer, "orders")) {
      probe.ready(Duration.ofSeconds(1));
      Assert.assertEquals(
          probe.await(m -> m.key().equals("run-1"), Duration.ofSeconds(1)).body(), "a");
      Assert.assertEquals(
          probe.await(m -> m.key().equals("run-2"), Duration.ofSeconds(1)).body(), "b");
      Assert.assertNull(
          consumer
              .committed(Set.of(new TopicPartition("orders", 0)))
              .get(new TopicPartition("orders", 0)));
    }
  }

  @Test
  public void consumerCanInitializeOffsetsBeforeTheBusinessActionWithoutDiscardingItsFirstBatch() {
    var consumer = consumer();
    var partition = new TopicPartition("orders", 0);
    List<String> handled = new ArrayList<>();
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      runner.ready(Duration.ofSeconds(1));
      Assert.assertEquals(consumer.assignment(), Set.of(partition));
      Assert.assertNull(consumer.committed(Set.of(partition)).get(partition));
      Assert.assertTrue(
          runner.consumeOne(Duration.ofSeconds(1), record -> handled.add(record.value())));
      Assert.assertTrue(
          runner.consumeOne(Duration.ofSeconds(1), record -> handled.add(record.value())));
      Assert.assertEquals(handled, List.of("a", "b"));
      Assert.assertEquals(consumer.committed(Set.of(partition)).get(partition).offset(), 2L);
    }
  }

  @Test
  public void latestOffsetIsEstablishedBeforeProducingTheMessageUnderTest() {
    var consumer = new MockConsumer<String, String>(OffsetResetStrategy.LATEST);
    var partition = new TopicPartition("orders", 0);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(Map.of(partition, 0L));
          consumer.updateEndOffsets(Map.of(partition, 5L));
        });
    List<Long> offsets = new ArrayList<>();
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      runner.ready(Duration.ofSeconds(1));
      Assert.assertEquals(consumer.position(partition), 5L);
      consumer.addRecord(new ConsumerRecord<>("orders", 0, 5, "run-1", "new-business-event"));
      Assert.assertTrue(
          runner.consumeOne(Duration.ofSeconds(1), record -> offsets.add(record.offset())));
      Assert.assertEquals(offsets, List.of(5L));
      Assert.assertEquals(consumer.committed(Set.of(partition)).get(partition).offset(), 6L);
    }
  }

  @Test
  public void failedHandlerDoesNotCommitAndSuccessfulHandlerCommitsOnlyProcessedOffset() {
    var consumer = consumer();
    var tp = new TopicPartition("orders", 0);
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      Assert.assertTrue(runner.consumeOne(Duration.ofSeconds(1), r -> {}));
      Assert.assertEquals(consumer.committed(Set.of(tp)).get(tp).offset(), 1L);
      Assert.expectThrows(
          IllegalStateException.class,
          () ->
              runner.consumeOne(
                  Duration.ofSeconds(1),
                  r -> {
                    throw new IllegalStateException("fail");
                  }));
      Assert.assertEquals(consumer.committed(Set.of(tp)).get(tp).offset(), 1L);
      Assert.assertEquals(consumer.position(tp), 1L);
    }
  }

  @Test
  public void publisherPreservesCorrelationHeader() {
    var producer =
        new MockProducer<String, String>(true, new StringSerializer(), new StringSerializer());
    try (var publisher = new KafkaPublisher(producer)) {
      publisher.send(
          new TestMessage("orders", "run-1", "payload", Map.of("runId", "run-1")),
          Duration.ofSeconds(1));
      Assert.assertEquals(producer.history().get(0).key(), "run-1");
      Assert.assertNotNull(producer.history().get(0).headers().lastHeader("runId"));
    }
  }

  @Test
  public void failedRewindStopsProcessingBeforeAnyLaterOffsetCanCommit() {
    AtomicBoolean failRewind = new AtomicBoolean();
    var consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized void seek(TopicPartition partition, long offset) {
            if (failRewind.get()) throw new IllegalStateException("seek unavailable");
            super.seek(partition, offset);
          }
        };
    var partition = new TopicPartition("orders", 0);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(Map.of(partition, 0L));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "run-1", "first"));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "run-1", "second"));
        });
    AtomicInteger handled = new AtomicInteger();
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      var failure =
          Assert.expectThrows(
              IllegalArgumentException.class,
              () ->
                  runner.consumeOne(
                      Duration.ofSeconds(1),
                      record -> {
                        handled.incrementAndGet();
                        failRewind.set(true);
                        throw new IllegalArgumentException("handler failed");
                      }));
      Assert.assertEquals(failure.getSuppressed().length, 1);
      Assert.expectThrows(
          IllegalStateException.class,
          () -> runner.consumeOne(Duration.ofSeconds(1), record -> handled.incrementAndGet()));
      Assert.assertEquals(handled.get(), 1);
      Assert.assertNull(consumer.committed(Set.of(partition)).get(partition));
    }
    Assert.assertTrue(consumer.closed());
  }

  @Test
  public void committedOffsetWithLostAcknowledgementCannotRepeatTheSuccessfulHandler() {
    var consumer =
        new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
          @Override
          public synchronized void commitSync(
              Map<TopicPartition, OffsetAndMetadata> offsets, Duration timeout) {
            super.commitSync(offsets, timeout);
            throw new org.apache.kafka.common.errors.TimeoutException(
                "commit acknowledgement lost");
          }
        };
    var partition = new TopicPartition("orders", 0);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(Map.of(partition, 0L));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 0, "run-1", "first"));
          consumer.addRecord(new ConsumerRecord<>("orders", 0, 1, "run-1", "second"));
        });
    AtomicInteger handled = new AtomicInteger();
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      var failure =
          Assert.expectThrows(
              KafkaOffsetCommitException.class,
              () -> runner.consumeOne(Duration.ofSeconds(1), record -> handled.incrementAndGet()));
      Assert.assertEquals(failure.nextOffset(), 1L);
      Assert.assertEquals(consumer.committed(Set.of(partition)).get(partition).offset(), 1L);
      Assert.expectThrows(
          IllegalStateException.class,
          () -> runner.consumeOne(Duration.ofSeconds(1), record -> handled.incrementAndGet()));
      Assert.assertEquals(handled.get(), 1);
    }
    Assert.assertTrue(consumer.closed());
  }

  @Test
  public void failedSubscriptionClosesTheTransferredConsumer() {
    for (boolean probe : List.of(true, false)) {
      var failure = new IllegalStateException("subscribe failed");
      var consumer =
          new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST) {
            @Override
            public synchronized void subscribe(Collection<String> topics) {
              throw failure;
            }
          };
      var actual =
          Assert.expectThrows(
              IllegalStateException.class,
              () -> {
                if (probe) new KafkaMessageProbe(consumer, "orders");
                else new KafkaConsumerRunner(consumer, "orders");
              });
      Assert.assertSame(actual, failure);
      Assert.assertTrue(consumer.closed());
    }
  }

  @Test
  public void oversizedPollCannotResumeAndCommitPastDiscardedRecords() {
    var consumer = new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST);
    var partition = new TopicPartition("orders", 0);
    consumer.schedulePollTask(
        () -> {
          consumer.rebalance(List.of(partition));
          consumer.updateBeginningOffsets(Map.of(partition, 0L));
          for (int index = 0; index < 1001; index++)
            consumer.addRecord(new ConsumerRecord<>("orders", 0, index, "run-1", "body"));
        });
    AtomicInteger handled = new AtomicInteger();
    try (var runner = new KafkaConsumerRunner(consumer, "orders")) {
      Assert.expectThrows(
          IllegalStateException.class,
          () -> runner.consumeOne(Duration.ofSeconds(1), record -> handled.incrementAndGet()));
      Assert.expectThrows(
          IllegalStateException.class,
          () -> runner.consumeOne(Duration.ofSeconds(1), record -> handled.incrementAndGet()));
      Assert.assertEquals(handled.get(), 0);
      Assert.assertNull(consumer.committed(Set.of(partition)).get(partition));
    }
  }

  @Test
  public void timeoutIsUnknownAndDoesNotCancelOrResendTheOriginalDelivery() {
    var producer =
        new MockProducer<String, String>(false, new StringSerializer(), new StringSerializer());
    try (var publisher = new KafkaPublisher(producer)) {
      var failure =
          Assert.expectThrows(
              KafkaDeliveryException.class,
              () ->
                  publisher.send(
                      new TestMessage("orders", "run-1", "private-body", Map.of()),
                      Duration.ofMillis(5)));
      Assert.assertEquals(failure.outcome(), KafkaDeliveryException.Outcome.OUTCOME_UNKNOWN);
      Assert.assertFalse(failure.getMessage().contains("private-body"));
      Assert.assertEquals(producer.history().size(), 1);
      Assert.assertTrue(
          producer.completeNext(),
          "The original send can still be acknowledged after the caller timed out");
      Assert.assertEquals(producer.history().size(), 1);
    }
  }

  @Test
  public void interruptedSendPreservesInterruptAndUnknownOutcome() {
    var producer =
        new MockProducer<String, String>(false, new StringSerializer(), new StringSerializer());
    try (var publisher = new KafkaPublisher(producer)) {
      Thread.currentThread().interrupt();
      var failure =
          Assert.expectThrows(
              KafkaDeliveryException.class,
              () ->
                  publisher.send(
                      new TestMessage("orders", "run-1", "body", Map.of()), Duration.ofSeconds(1)));
      Assert.assertEquals(failure.outcome(), KafkaDeliveryException.Outcome.OUTCOME_UNKNOWN);
      Assert.assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }
}
