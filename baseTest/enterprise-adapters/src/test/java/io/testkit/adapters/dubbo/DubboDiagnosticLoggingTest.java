package io.testkit.adapters.dubbo;

import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.apache.dubbo.config.*;
import org.apache.dubbo.rpc.service.GenericService;
import org.testng.Assert;
import org.testng.annotations.Test;

public class DubboDiagnosticLoggingTest {
  @Test
  public void realRpcLogsArgumentsResponseAndRedactsAttachments() throws Exception {
    Path file = Files.createTempDirectory("dubbo-call-log").resolve("calls.jsonl");
    Map<String, String> previous = new HashMap<>();
    for (String key :
        List.of(
            "testkit.log.enabled", "testkit.log.console", "testkit.log.bodies", "testkit.log.file"))
      previous.put(key, System.getProperty(key));
    var framework = new org.apache.dubbo.rpc.model.FrameworkModel();
    var providerModel = framework.newApplication();
    var provider = new ServiceConfig<GenericService>(providerModel.getDefaultModule());
    var app = new ApplicationConfig(providerModel, "testkit-log-provider");
    app.setQosEnable(false);
    app.setMetadataServiceProtocol("injvm");
    provider.setApplication(app);
    provider.setRegistry(new RegistryConfig("N/A"));
    int freePort;
    try (var reservation = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("::1"))) {
      freePort = reservation.getLocalPort();
    }
    var protocol = new ProtocolConfig("dubbo", freePort);
    protocol.setHost("::1");
    provider.setProtocol(protocol);
    provider.setInterface("io.testkit.demo.LoggingService");
    provider.setGeneric("true");
    provider.setRef(
        (method, types, args) -> Map.of("code", "OK", "access_token", "RawRpcResponseSecret"));
    try {
      System.setProperty("testkit.log.enabled", "true");
      System.setProperty("testkit.log.console", "false");
      System.setProperty("testkit.log.bodies", "true");
      System.setProperty("testkit.log.file", file.toString());
      provider.export();
      int port = provider.getExportedUrls().get(0).getPort();
      try (var client =
          new DubboClient(
              "testkit-dubbo",
              "io.testkit.demo.LoggingService",
              null,
              null,
              null,
              "dubbo://[::1]:" + port,
              Duration.ofSeconds(5))) {
        Object result =
            client.invoke(
                "create",
                new String[] {"java.util.Map"},
                new Object[] {Map.of("sku", "SKU-001", "password", "RawRpcRequestSecret")},
                Map.of("authorization", "Bearer RawRpcAttachmentSecret"));
        Assert.assertTrue(result.toString().contains("RawRpcResponseSecret"));
      }
      String output = Files.readString(file);
      Assert.assertTrue(output.contains("SKU-001"));
      Assert.assertTrue(output.contains("LoggingService"));
      Assert.assertTrue(output.contains("create"));
      for (String secret :
          List.of("RawRpcRequestSecret", "RawRpcResponseSecret", "RawRpcAttachmentSecret"))
        Assert.assertFalse(output.contains(secret), secret);
      Assert.assertEquals(Files.readAllLines(file).size(), 2);
    } finally {
      provider.unexport();
      framework.destroy();
      previous.forEach(
          (key, value) -> {
            if (value == null) System.clearProperty(key);
            else System.setProperty(key, value);
          });
    }
  }
}
