package io.testkit.basetest.enterprise;

import io.testkit.basetest.config.ConfigLoader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Persists the validated pre-execution plan consumed by the CI completeness gate. */
public final class EnterprisePlanManifest {
  private EnterprisePlanManifest() {}

  /**
   * Creates a new manifest without overwriting another worker's artifact.
   *
   * <p>The selected IDs must exactly match the planned IDs assigned to this shard. All workers must
   * use the same complete logical plan. The stable plan hash covers logical IDs, not source
   * revisions or DataProvider row counts; CI must additionally pin code, dependencies and input
   * datasets.
   *
   * @param execution validated worker identity and shard coordinates
   * @param planned complete logical plan before partitioning
   * @param selected all logical IDs assigned to this worker
   * @param directory worker artifact directory
   * @return path to a newly persisted versioned JSON manifest
   * @throws IllegalArgumentException if sets are empty, invalid or misassigned
   * @throws IllegalStateException if the manifest cannot be persisted
   */
  public static Path write(
      EnterpriseExecution execution,
      Collection<String> planned,
      Collection<String> selected,
      Path directory) {
    Objects.requireNonNull(execution, "execution");
    Objects.requireNonNull(directory, "directory");
    List<String> all = planned.stream().sorted().distinct().toList();
    List<String> assigned = selected.stream().sorted().distinct().toList();
    if (all.isEmpty() || assigned.isEmpty() || !all.containsAll(assigned)) {
      throw new IllegalArgumentException("Invalid enterprise manifest case sets");
    }
    List<String> expected = all.stream().filter(execution.shard()::owns).toList();
    if (!assigned.equals(expected)) {
      throw new IllegalArgumentException(
          "Manifest case assignments differ from the deterministic shard plan");
    }

    RunMetadata run = execution.run();
    Map<String, Object> row = new LinkedHashMap<>();
    row.put("schemaVersion", 1);
    row.put("runId", run.runId());
    row.put("workerId", run.workerId());
    row.put("environment", run.environment());
    row.put("team", run.team());
    row.put("shardIndex", execution.shard().index());
    row.put("shardCount", execution.shard().count());
    row.put("planHash", Shard.digest(String.join("\n", all)));
    row.put("plannedCaseIds", all);
    row.put("selectedCaseIds", assigned);
    try {
      Files.createDirectories(directory);
      Path file = directory.resolve(run.workerId() + "-" + UUID.randomUUID() + ".plan.json");
      Files.writeString(
          file,
          ConfigLoader.jsonMapper().writeValueAsString(row),
          StandardCharsets.UTF_8,
          StandardOpenOption.CREATE_NEW);
      return file;
    } catch (IOException failure) {
      throw new IllegalStateException("Cannot persist enterprise execution plan", failure);
    }
  }
}
