package io.testkit.basetest.mock;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class InMemoryMockEngineTest {
    @Test
    public void sceneLifecycleIsExplicitAndIdempotent() {
        InMemoryMockEngine engine = new InMemoryMockEngine();
        MockScenario scenario = new MockScenario("payment-timeout", MockScope.CASE,
                List.of(new MockRule("http", "POST /payments", Map.of("amount", 10),
                        Map.of("status", 504), 1)));

        MockSession session = engine.open(scenario);
        Assert.assertTrue(session.active());
        Assert.assertEquals(engine.activeScenarios().size(), 1);
        session.close();
        session.close();
        Assert.assertFalse(session.active());
    }
}
