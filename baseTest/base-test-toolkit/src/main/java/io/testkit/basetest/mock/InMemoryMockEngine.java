package io.testkit.basetest.mock;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** A learning and unit-test implementation; it records scenes but does not intercept network traffic. */
public final class InMemoryMockEngine implements MockEngine {
    private final Map<String, MockScenario> active = new ConcurrentHashMap<>();

    @Override
    public MockSession open(MockScenario scenario) {
        String sessionId = UUID.randomUUID().toString();
        active.put(sessionId, scenario);
        return new Session(sessionId, scenario);
    }

    public Map<String, MockScenario> activeScenarios() {
        return Map.copyOf(active);
    }

    private final class Session implements MockSession {
        private final String id;
        private final MockScenario scenario;

        private Session(String id, MockScenario scenario) {
            this.id = id;
            this.scenario = scenario;
        }

        public String id() { return id; }
        public MockScenario scenario() { return scenario; }
        public boolean active() { return active.containsKey(id); }
        public void close() { active.remove(id); }
    }
}
