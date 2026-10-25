package io.testkit.basetest.runtime;

import org.testng.annotations.Factory;

public class CompletionPublisherFactoryFixture {
  @Factory
  public Object[] independentPublishers() {
    return new Object[] {new CompletionPublisherFixture(), new CompletionPublisherFixture()};
  }
}
