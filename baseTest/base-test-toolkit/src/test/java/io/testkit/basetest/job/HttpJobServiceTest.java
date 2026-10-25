package io.testkit.basetest.job;

import com.sun.net.httpserver.HttpServer;
import io.testkit.basetest.http.ApiClient;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.testng.Assert;
import org.testng.annotations.Test;

public class HttpJobServiceTest {
  @Test
  public void explicitGatewayContractAndInstantDecode() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/test-jobs",
        x -> {
          byte[] body =
              "{\"id\":\"j1\",\"state\":\"SUCCEEDED\",\"triggeredAt\":\"2026-01-01T00:00:00Z\",\"message\":null}"
                  .getBytes(StandardCharsets.UTF_8);
          x.getRequestBody().readAllBytes();
          x.sendResponseHeaders(x.getRequestMethod().equals("POST") ? 202 : 200, body.length);
          x.getResponseBody().write(body);
          x.close();
        });
    server.start();
    try {
      var service =
          new HttpJobService(
              new ApiClient("http://127.0.0.1:" + server.getAddress().getPort()), "qa", Map.of());
      var triggered = service.trigger(new JobRequest("app", "handler", "qa", null, Map.of()));
      Assert.assertEquals(triggered.id(), "j1");
      Assert.assertEquals(service.query("j1").orElseThrow().state(), JobExecution.State.SUCCEEDED);
      Assert.assertNotNull(triggered.triggeredAt());
    } finally {
      server.stop(0);
    }
  }
}
