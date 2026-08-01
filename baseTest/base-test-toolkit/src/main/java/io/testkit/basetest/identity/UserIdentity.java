package io.testkit.basetest.identity;

import java.util.Map;

public record UserIdentity(String alias, String subject, String accessToken,
                           Map<String, String> attributes) {
    public UserIdentity {
        if (alias == null || alias.isBlank()) throw new IllegalArgumentException("alias must not be blank");
        attributes = Map.copyOf(attributes == null ? Map.of() : attributes);
    }

    @Override
    public String toString() {
        return "UserIdentity[alias=" + alias + ", subject=" + subject + ", accessToken=<redacted>]";
    }
}
