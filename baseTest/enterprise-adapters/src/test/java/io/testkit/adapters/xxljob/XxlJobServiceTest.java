package io.testkit.adapters.xxljob;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.job.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.testng.Assert;
import org.testng.annotations.*;

public class XxlJobServiceTest {
  @DataProvider
  public Object[][] versions() {
    return new Object[][] {{"2.4.1"}, {"3.1.1"}};
  }

  @Test(dataProvider = "versions")
  public void nativeAdminFormCookieTriggerAndActualLogStatus(String version) throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicBoolean triggered = new AtomicBoolean();
    server.createContext(
        "/xxl-job-admin/",
        exchange -> {
          String path = exchange.getRequestURI().getPath();
          String form =
              new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
          String response;
          if (path.endsWith("/login")) {
            Assert.assertTrue(form.contains("userName=admin"));
            exchange
                .getResponseHeaders()
                .add("Set-Cookie", "XXL_JOB_LOGIN_IDENTITY=secret; Path=/; HttpOnly");
            response = "{\"code\":200}";
          } else {
            Assert.assertEquals(
                exchange.getRequestHeaders().getFirst("Cookie"), "XXL_JOB_LOGIN_IDENTITY=secret");
            if (path.endsWith("/trigger")) {
              Assert.assertTrue(form.contains("id=7"));
              triggered.set(true);
              response = "{\"code\":200}";
            } else
              response =
                  triggered.get()
                      ? "{\"data\":[{\"id\":101,\"triggerCode\":200,\"handleCode\":200}]}"
                      : "{\"data\":[]}";
          }
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(200, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var jobs =
        new XxlJobService(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/xxl-job-admin",
            "qa",
            1,
            Map.of("settlement", 7),
            XxlJobDialect.forVersion(version),
            Duration.ofSeconds(2))) {
      jobs.login("admin", "not-logged");
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> jobs.trigger(new JobRequest("app", "settlement", "dev", null, Map.of())));
      JobExecution result =
          jobs.trigger(new JobRequest("app", "settlement", "qa", null, Map.of("runId", "run-1")));
      Assert.assertEquals(result.id(), "101");
      Assert.assertEquals(
          jobs.query(result.id()).orElseThrow().state(), JobExecution.State.SUCCEEDED);
    } finally {
      server.stop(0);
    }
  }

  @Test
  public void unknownVersionRequiresDialect() {
    Assert.expectThrows(IllegalArgumentException.class, () -> XxlJobDialect.forVersion("99.0"));
    Assert.expectThrows(IllegalArgumentException.class, () -> XxlJobDialect.forVersion(null));
    Assert.assertEquals(
        XxlJobDialect.legacy("2.4.1").triggerPath(),
        XxlJobDialect.forVersion("2.4.1").triggerPath());
  }

  @Test
  public void malformedLogsCannotPretendToSucceed() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    Assert.expectThrows(
        IllegalStateException.class,
        () -> XxlJobDialect.forVersion("2.4.1").rows(json.readTree("{\"data\":[{\"id\":0}]}")));
    Assert.assertFalse(XxlJobDialect.forVersion("3.1.1").accepted(json.readTree("{\"code\":500}")));
    Assert.assertFalse(
        XxlJobDialect.forVersion("3.1.1").accepted(json.readTree("{\"code\":\"200\"}")));
    Assert.assertFalse(
        XxlJobDialect.forVersion("3.1.1").accepted(json.readTree("{\"code\":4294967496}")));
    Assert.expectThrows(
        IllegalStateException.class,
        () ->
            XxlJobDialect.forVersion("2.4.1")
                .rows(
                    json.readTree(
                        "{\"data\":[{\"id\":101,\"triggerCode\":4294967496,\"handleCode\":200}]}")));
    Assert.expectThrows(
        IllegalStateException.class,
        () ->
            XxlJobDialect.forVersion("2.4.1")
                .rows(
                    json.readTree(
                        "{\"data\":[{\"id\":18446744073709551717,\"triggerCode\":200,\"handleCode\":200}]}")));
  }

  @DataProvider
  public Object[][] submissionOutcomes() {
    return new Object[][] {
      {"missing-log"},
      {"ambiguous-log"},
      {"trigger-transport-error"},
      {"malformed-acknowledgement"},
      {"rejected"},
      {"contradictory-status"}
    };
  }

  @Test(dataProvider = "submissionOutcomes")
  public void unknownSubmissionCannotBeMistakenForARejectionOrResubmitted(String scenario)
      throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    AtomicInteger submissions = new AtomicInteger();
    server.createContext(
        "/admin/",
        exchange -> {
          exchange.getRequestBody().readAllBytes();
          String path = exchange.getRequestURI().getPath();
          String response;
          int status = 200;
          if (path.endsWith("/login")) {
            exchange
                .getResponseHeaders()
                .add("Set-Cookie", "XXL_JOB_LOGIN_IDENTITY=session; Path=/; HttpOnly");
            response = "{\"code\":200}";
          } else if (path.endsWith("/trigger")) {
            submissions.incrementAndGet();
            status = scenario.equals("trigger-transport-error") ? 500 : 200;
            response =
                scenario.equals("rejected")
                    ? "{\"code\":500}"
                    : scenario.equals("malformed-acknowledgement")
                        ? "{\"code\":\"200\"}"
                        : "{\"code\":200}";
          } else if (submissions.get() == 0 || scenario.equals("missing-log"))
            response = "{\"data\":[]}";
          else if (scenario.equals("ambiguous-log"))
            response =
                "{\"data\":[{\"id\":101,\"triggerCode\":200,\"handleCode\":200},{\"id\":102,\"triggerCode\":200,\"handleCode\":200}]}";
          else response = "{\"data\":[{\"id\":101,\"triggerCode\":500,\"handleCode\":200}]}";
          byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(status, bytes.length);
          exchange.getResponseBody().write(bytes);
          exchange.close();
        });
    server.start();
    try (var jobs =
        new XxlJobService(
            "http://127.0.0.1:" + server.getAddress().getPort() + "/admin",
            "qa",
            1,
            Map.of("settlement", 7),
            XxlJobDialect.forVersion("2.4.1"),
            Duration.ofMillis(500))) {
      jobs.login("admin", "not-logged");
      var request = new JobRequest("app", "settlement", "qa", null, Map.of());
      if (scenario.equals("contradictory-status")) {
        Assert.assertEquals(jobs.trigger(request).state(), JobExecution.State.FAILED);
      } else {
        var failure =
            Assert.expectThrows(XxlJobSubmissionException.class, () -> jobs.trigger(request));
        Assert.assertEquals(
            failure.outcome(),
            scenario.equals("rejected")
                ? XxlJobSubmissionException.Outcome.REJECTED
                : XxlJobSubmissionException.Outcome.OUTCOME_UNKNOWN);
        Assert.assertEquals(failure.jobId(), 7);
        Assert.expectThrows(XxlJobSubmissionException.class, () -> jobs.trigger(request));
        Assert.assertEquals(
            submissions.get(),
            scenario.equals("rejected") ? 2 : 1,
            "An uncertain original execution must never be automatically submitted a second time");
      }
    } finally {
      server.stop(0);
    }
  }
}
