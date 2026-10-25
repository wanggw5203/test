package io.testkit.basetest.enterprise;

import java.net.URI;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * HTTP origin allowlist keyed by {@code environment/service} identifiers.
 *
 * <p>Origins include normalized scheme, host and port. Credentials, paths, queries and fragments
 * are prohibited in configured origins. This invocation guard does not enforce network ACLs,
 * authentication or path-level access.
 */
public record TargetPolicy(Map<String, Set<String>> httpOrigins) {
  public TargetPolicy {
    Objects.requireNonNull(httpOrigins, "httpOrigins");
    Map<String, Set<String>> normalized = new LinkedHashMap<>();
    httpOrigins.forEach(
        (key, values) -> {
          if (key == null || !key.matches("[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Expected environment/service target key");
          }
          String[] identifiers = key.split("/", -1);
          RunMetadata.token(identifiers[0], "target environment");
          RunMetadata.token(identifiers[1], "target service");
          Set<String> origins = new HashSet<>();
          for (String value : values) {
            URI uri = URI.create(value);
            if (uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
              throw new IllegalArgumentException("HTTP allowlist entries must be origins");
            }
            origins.add(origin(uri));
          }
          normalized.put(key, Set.copyOf(origins));
        });
    httpOrigins = Map.copyOf(normalized);
  }

  /** Returns a policy that rejects every HTTP target. */
  public static TargetPolicy denyAll() {
    return new TargetPolicy(Map.of());
  }

  /** Rejects a target unless its origin is allowed for this environment/service. */
  public void requireHttp(RunMetadata run, CaseDefinition definition, URI target) {
    if (!httpOrigins
        .getOrDefault(run.environment() + "/" + definition.service(), Set.of())
        .contains(origin(target))) {
      throw new IllegalArgumentException("HTTP origin denied by enterprise target policy");
    }
  }

  private static String origin(URI uri) {
    if (uri.getHost() == null
        || !("http".equalsIgnoreCase(uri.getScheme())
            || "https".equalsIgnoreCase(uri.getScheme()))) {
      throw new IllegalArgumentException("Expected HTTP(S) origin");
    }
    int port =
        uri.getPort() >= 0 ? uri.getPort() : "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    if (port < 1 || port > 65535) {
      throw new IllegalArgumentException("HTTP origin port must be between 1 and 65535");
    }
    return uri.getScheme().toLowerCase(Locale.ROOT)
        + "://"
        + uri.getHost().toLowerCase(Locale.ROOT)
        + ":"
        + port;
  }

  /** Omits target addresses from diagnostic output. */
  @Override
  public String toString() {
    return "TargetPolicy[keys=" + httpOrigins.keySet() + "]";
  }
}
