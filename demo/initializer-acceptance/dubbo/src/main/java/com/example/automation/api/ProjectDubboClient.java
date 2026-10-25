package com.example.automation.api;
import io.testkit.adapters.dubbo.DubboClient;
import com.example.automation.config.ProjectSettings;
import java.time.Duration;
import java.util.Map;
public final class ProjectDubboClient implements AutoCloseable {
    private final DubboClient client;
    public ProjectDubboClient(String interfaceName, String group, String version) {
        String registry = ProjectSettings.value("dubbo.registry");
        String direct = ProjectSettings.value("dubbo.directUrl");
        if (registry.isBlank() && direct.isBlank()) throw new IllegalStateException("Configure dubbo.registry or dubbo.directUrl before real invocation");
        client = new DubboClient("dubbo-automation", interfaceName, group, version,
                registry.isBlank() ? null : registry, direct.isBlank() ? null : direct, Duration.ofSeconds(10));
    }
    public Object invoke(String method, String[] types, Object[] args, Map<String,String> attachments) {
        return client.invoke(method, types, args, attachments);
    }
    @Override public void close() { client.close(); }
}
