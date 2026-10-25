package io.testkit.basetest.enterprise;

import io.testkit.basetest.config.ConfigLoader;
import java.nio.file.*;
import java.util.*;
import java.util.stream.IntStream;
import org.testng.Assert;
import org.testng.annotations.Test;

public class EnterprisePlanManifestTest {
  @Test
  public void manifestIncludesFullAndAssignedSetsWithStableHash() throws Exception {
    Path directory = Files.createTempDirectory("testkit-plan");
    try {
      int index = new Shard(0, 2).owns("orders.a") ? 0 : 1;
      var execution =
          new EnterpriseExecution(
              new RunMetadata("run", "worker", "qa", "orders"),
              ExecutionPolicy.localSafe(),
              new Shard(index, 2));
      var selected =
          List.of("orders.a", "orders.b").stream().filter(execution.shard()::owns).toList();
      var first =
          EnterprisePlanManifest.write(
              execution, List.of("orders.b", "orders.a"), selected, directory);
      var second =
          EnterprisePlanManifest.write(
              execution, List.of("orders.a", "orders.b"), selected, directory);
      var row = ConfigLoader.jsonMapper().readTree(first.toFile());
      Assert.assertEquals(
          row.path("planHash").asText(),
          ConfigLoader.jsonMapper().readTree(second.toFile()).path("planHash").asText());
      Assert.assertEquals(row.path("plannedCaseIds").size(), 2);
      Assert.assertEquals(row.path("selectedCaseIds").size(), selected.size());
      Assert.assertEquals(row.path("shardCount").asInt(), 2);
      Assert.assertEquals(row.path("runId").asText(), "run");
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnterprisePlanManifest.write(execution, List.of("orders.a"), List.of(), directory));
    } finally {
      try (var files = Files.list(directory)) {
        for (Path file : files.toList()) Files.delete(file);
      }
      Files.delete(directory);
    }
  }

  @Test
  public void shouldRejectIncompleteOrForeignShardAssignmentsBeforeWriting() throws Exception {
    Path directory = Files.createTempDirectory("testkit-invalid-plan");
    try {
      var execution =
          new EnterpriseExecution(
              new RunMetadata("run", "worker", "qa", "orders"),
              ExecutionPolicy.localSafe(),
              new Shard(0, 2));
      List<String> planned =
          IntStream.range(0, 40).mapToObj(index -> "orders.case-" + index).toList();
      List<String> selected = planned.stream().filter(execution.shard()::owns).toList();
      Assert.assertTrue(selected.size() > 1);
      Assert.expectThrows(
          IllegalArgumentException.class,
          () ->
              EnterprisePlanManifest.write(
                  execution, planned, selected.subList(1, selected.size()), directory));
      String foreign =
          planned.stream().filter(id -> !execution.shard().owns(id)).findFirst().orElseThrow();
      List<String> mixed = new ArrayList<>(selected);
      mixed.add(foreign);
      Assert.expectThrows(
          IllegalArgumentException.class,
          () -> EnterprisePlanManifest.write(execution, planned, mixed, directory));
      try (var files = Files.list(directory)) {
        Assert.assertEquals(files.count(), 0L);
      }
    } finally {
      Files.delete(directory);
    }
  }
}
