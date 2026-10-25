package io.testkit.basetest.lease;

import java.time.Duration;

/**
 * Explicit ownership of a shared test resource for a bounded lease period.
 *
 * <p>Use try-with-resources and renew before expiry when needed. A successful validity check is a
 * point-in-time observation; the protected business system must enforce fencing tokens to reject
 * operations from expired owners.
 */
public interface ResourceLease extends AutoCloseable {
  /** Returns the monotonically increasing ownership generation for this resource. */
  long fencingToken();

  /** Returns whether this owner and generation remain current and unexpired. */
  boolean valid();

  /** Renews only a current unexpired owner; false means ownership was not renewed. */
  boolean renew(Duration ttl);

  /** Releases only this ownership generation; successful repeat closure is harmless. */
  @Override
  void close();
}
