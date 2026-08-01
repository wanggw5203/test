package io.testkit.basetest.config;

import com.fasterxml.jackson.databind.JsonMappingException;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class ConfigCatalogTest {
    @Test
    public void localConfigurationCanOverrideRemoteWithoutLeakingSecrets() {
        ConnectionSpec remote = new ConnectionSpec("orders", ConnectionSpec.Kind.DATABASE,
                Map.of("host", "remote", "password", "remote-secret"));
        ConnectionSpec local = new ConnectionSpec("orders", ConnectionSpec.Kind.DATABASE,
                Map.of("host", "localhost"));

        ConnectionSpec merged = ConfigCatalog.merge(List.of(remote), List.of(local), true)
                .require(ConnectionSpec.Kind.DATABASE, "orders");

        Assert.assertEquals(merged.property("host"), "localhost");
        Assert.assertEquals(merged.property("password"), "remote-secret");
        Assert.assertFalse(merged.toString().contains("remote-secret"));
    }

    @Test(expectedExceptions = JsonMappingException.class)
    public void strictMapperRejectsStringToNumberCoercion() throws Exception {
        ConfigLoader.strictJsonMapper().readValue("{\"value\":\"1\"}", NumberValue.class);
    }

    public record NumberValue(Integer value) {}
}
