package io.testkit.basetest.enterprise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Deterministic method-level partition assignment shared by all CI workers.
 *
 * <p>All DataProvider rows for a logical ID stay on its assigned shard. The hash algorithm is part
 * of the execution compatibility contract and must not change within a run or a compatible
 * framework release.
 *
 * @param index zero-based worker partition index
 * @param count total partitions, from 1 through 256
 */
public record Shard(int index, int count) {
  public Shard {
    if (count < 1 || count > 256 || index < 0 || index >= count) {
      throw new IllegalArgumentException("Invalid shard index/count");
    }
  }

  /** Returns whether this shard owns the given stable logical test identity. */
  public boolean owns(String logicalId) {
    CaseDefinition.validateId(logicalId);
    int hash = Integer.parseUnsignedInt(digest(logicalId).substring(0, 8), 16);
    return Math.floorMod(hash, count) == index;
  }

  static String digest(String text) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException failure) {
      throw new IllegalStateException("SHA-256 is unavailable", failure);
    }
  }
}
