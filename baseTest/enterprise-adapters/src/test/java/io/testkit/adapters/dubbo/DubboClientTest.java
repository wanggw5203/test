package io.testkit.adapters.dubbo;

import java.time.Duration;
import java.util.Map;
import org.apache.dubbo.config.*;
import org.apache.dubbo.rpc.service.GenericService;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DubboClientTest {
  @Test
  public void subMillisecondTimeoutCannotBecomeAnUnboundedDubboTimeout() {
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            new DubboClient(
                "testkit-dubbo",
                "io.testkit.demo.GreetingService",
                null,
                null,
                null,
                "dubbo://127.0.0.1:20880",
                Duration.ofNanos(1)));
  }

  @Test
  public void realLocalDubboGenericInvocationAndReferenceCleanup() {
    var service = new ServiceConfig<GenericService>();
    var app = new ApplicationConfig("testkit-dubbo");
    app.setQosEnable(false);
    service.setApplication(app);
    service.setRegistry(new RegistryConfig("N/A"));
    service.setProtocol(new ProtocolConfig("dubbo", -1));
    service.setInterface("io.testkit.demo.GreetingService");
    service.setGeneric("true");
    service.setRef((method, types, arguments) -> "hello " + arguments[0]);
    service.export();
    try {
      int port = service.getExportedUrls().get(0).getPort();
      try (var client =
          new DubboClient(
              "testkit-dubbo",
              "io.testkit.demo.GreetingService",
              null,
              null,
              null,
              "dubbo://127.0.0.1:" + port,
              Duration.ofSeconds(3))) {
        Assert.assertEquals(
            client.invoke(
                "greet", new String[] {"java.lang.String"}, new Object[] {"world"}, Map.of()),
            "hello world");
        Assert.expectThrows(
            IllegalArgumentException.class,
            () -> client.invoke("greet", new String[0], new Object[] {"world"}, Map.of()));
      }
    } finally {
      service.unexport();
    }
  }
}
