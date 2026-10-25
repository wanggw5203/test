package io.testkit.basetest.discovery;

import java.net.URI;
import java.util.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class StaticServiceDiscoveryTest {
  @Test
  public void filtersHealthAndEveryDimension() {
    var metadata =
        Map.of("service", "orders", "environment", "local", "lane", "stable", "zone", "east");
    var good = new ServiceInstance("a", URI.create("http://localhost:8080"), true, metadata);
    var bad = new ServiceInstance("b", URI.create("http://localhost:8081"), false, metadata);
    var discovery = new StaticServiceDiscovery(List.of(good, bad));
    Assert.assertEquals(
        discovery.discover(new ServiceQuery("orders", "local", "stable", "east")), List.of(good));
    Assert.assertTrue(
        discovery.discover(new ServiceQuery("orders", "local", "stable", "west")).isEmpty());
    Assert.assertEquals(
        discovery.discover(new ServiceQuery(null, null, null, null)), List.of(good));
  }
}
