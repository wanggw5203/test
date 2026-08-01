package io.testkit.basetest.identity;

import org.testng.Assert;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

public class IdentityContextTest {
    @Test
    public void accountPoolAndNestedContextAreReleased() {
        UserIdentity first = new UserIdentity("reviewer-1", "101", "secret", Map.of());
        InMemoryIdentityProvider provider = new InMemoryIdentityProvider(Map.of("reviewer", List.of(first)));
        UserIdentity acquired = provider.acquire("reviewer");

        try (IdentityContext ignored = IdentityContext.use(acquired)) {
            Assert.assertEquals(IdentityContext.requireCurrent().alias(), "reviewer-1");
        } finally {
            provider.release(acquired);
        }

        Assert.assertTrue(IdentityContext.current().isEmpty());
        Assert.assertEquals(provider.acquire("reviewer"), first);
    }
}
