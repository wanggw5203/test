package io.testkit.adapters;

import io.testkit.adapters.xxljob.*;
import io.testkit.basetest.job.*;
import java.time.Duration;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class XxlJobLiveIT {
  @Test
  public void realJobCompletes() {
    int group =
        Integer.parseInt(IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_GROUP_ID"));
    int id = Integer.parseInt(IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_JOB_ID"));
    try (var jobs =
        new XxlJobService(
            IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_URL"),
            IntegrationTestConfiguration.requiredVariable("TEST_ENV"),
            group,
            Map.of("smoke", id),
            XxlJobDialect.forVersion(
                IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_VERSION")),
            Duration.ofSeconds(10))) {
      jobs.login(
          IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_USERNAME"),
          IntegrationTestConfiguration.requiredVariable("TEST_XXLJOB_PASSWORD"));
      var execution =
          jobs.trigger(
              new JobRequest(
                  "test",
                  "smoke",
                  IntegrationTestConfiguration.requiredVariable("TEST_ENV"),
                  null,
                  Map.of("runId", UUID.randomUUID().toString())));
      var result =
          JobAwait.succeeded(jobs, execution.id(), Duration.ofSeconds(60), Duration.ofMillis(200));
      Assert.assertEquals(result.state(), JobExecution.State.SUCCEEDED);
    }
  }
}
