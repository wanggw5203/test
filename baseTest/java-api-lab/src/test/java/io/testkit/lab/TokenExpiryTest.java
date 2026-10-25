package io.testkit.lab;

import java.time.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class TokenExpiryTest {
  private static final class MutableClock extends Clock {
    private volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");

    @Override
    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return Clock.fixed(now, zone);
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  @Test
  public void tokenExpiresExactlyAtBoundary() throws Exception {
    MutableClock clock = new MutableClock();
    try (LabServer server = new LabServer(0, clock, Duration.ofSeconds(30))) {
      server.start();
      ApiClient api = new ApiClient(server.baseUrl());
      String token = api.login("alice");
      clock.now = clock.now.plusSeconds(29);
      Assert.assertEquals(api.call("GET", "/api/me", token, null).status(), 200);
      clock.now = clock.now.plusSeconds(1);
      OrderApiTest.assertError(api.call("GET", "/api/me", token, null), 401, "TOKEN_EXPIRED");
      OrderApiTest.assertError(api.call("GET", "/api/me", token, null), 401, "INVALID_TOKEN");
    }
  }
}
