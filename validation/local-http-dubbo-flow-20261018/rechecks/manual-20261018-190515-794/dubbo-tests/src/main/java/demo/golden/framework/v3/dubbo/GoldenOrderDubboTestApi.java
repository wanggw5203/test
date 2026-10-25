package demo.golden.framework.v3.dubbo;

import java.util.Map;
import io.testkit.adapters.dubbo.DubboClient;
import java.time.Duration;

public class GoldenOrderDubboTestApi implements AutoCloseable {
    private final DubboClient client = new DubboClient("ai-test-scaffold", "demo.golden.OrderService", "", "",
            System.getProperty("dubbo.registry"), System.getProperty("dubbo.directUrl"), Duration.ofMillis(10000L));
    public Object invoke(Map<String, Object> request) {
        return client.invoke("create", new String[] {"java.util.Map"}, new Object[] {request}, Map.of());
    }
    @Override public void close() { client.close(); }
}
