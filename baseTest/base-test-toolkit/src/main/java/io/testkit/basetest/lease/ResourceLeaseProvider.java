package io.testkit.basetest.lease;

import java.time.Duration;
import java.util.Optional;

/** Atomic admission to a shared resource; providers do not wait or automatically renew. */
public interface ResourceLeaseProvider {
  /**
   * Attempts to acquire one resource or returns empty while another owner holds it. Infrastructure
   * failures are raised rather than reported as contention.
   *
   * @param resourceKey stable nonsecret environment/resource identity
   * @param ttl requested ownership period, subject to provider limits
   */
  Optional<ResourceLease> tryAcquire(String resourceKey, Duration ttl);
}
