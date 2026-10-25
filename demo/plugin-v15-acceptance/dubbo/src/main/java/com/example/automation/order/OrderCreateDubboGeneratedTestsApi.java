package com.example.automation.order;

import java.util.Map;
import io.testkit.adapters.dubbo.DubboClient;
import java.time.Duration;

public class OrderCreateDubboGeneratedTestsApi implements AutoCloseable {
    private final DubboClient client = new DubboClient("ai-test-scaffold", "com.example.order.OrderService", "", "",
            System.getProperty("dubbo.registry"), System.getProperty("dubbo.directUrl"), Duration.ofMillis(10000L));
    public Object invoke(Map<String, Object> request) {
        return client.invoke("create", new String[] {"com.example.order.CreateOrderBO"}, new Object[] {request}, Map.ofEntries(Map.entry("x-test-source", "ai-test-flow")));
    }
    @Override public void close() { client.close(); }
}
