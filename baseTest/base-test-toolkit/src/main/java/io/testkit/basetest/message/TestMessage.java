package io.testkit.basetest.message;

import java.util.Map;

public record TestMessage(String topic, String key, String body, Map<String, String> headers) {
  public TestMessage {
    headers = Map.copyOf(headers == null ? Map.of() : headers);
  }

  @Override
  public String toString() {
    return "TestMessage[topic=" + topic + ", payload=<omitted>]";
  }
}
