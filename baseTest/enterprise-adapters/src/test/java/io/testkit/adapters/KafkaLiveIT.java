package io.testkit.adapters;

import io.testkit.adapters.kafka.*;
import io.testkit.basetest.message.TestMessage;
import java.time.Duration;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class KafkaLiveIT {
  @Test
  public void brokerRoundTrip() {
    Properties properties = new Properties();
    properties.put(
        "bootstrap.servers", IntegrationTestConfiguration.requiredVariable("TEST_KAFKA_BOOTSTRAP"));
    properties.put("max.block.ms", "5000");
    properties.put("default.api.timeout.ms", "5000");
    String topic = IntegrationTestConfiguration.requiredVariable("TEST_KAFKA_TOPIC"),
        key = "testkit-" + UUID.randomUUID();
    try (var probe =
            new KafkaMessageProbe(
                properties, IntegrationTestConfiguration.requiredVariable("TEST_ENV"), topic);
        var publisher = new KafkaPublisher(properties)) {
      probe.ready(Duration.ofSeconds(15));
      publisher.send(
          new TestMessage(topic, key, "round-trip", Map.of("runId", key)), Duration.ofSeconds(10));
      Assert.assertEquals(
          probe.await(m -> key.equals(m.key()), Duration.ofSeconds(15)).body(), "round-trip");
    }
  }
}
