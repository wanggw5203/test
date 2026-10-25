package io.testkit.basetest.enterprise;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.testng.IMethodInstance;

/**
 * TestNG plan admission, level selection, deterministic sharding and manifest creation.
 *
 * <p>Every supplied enterprise method is validated before filtering, preventing a level selector or
 * shard from hiding missing ownership or denied operations. DataProvider expansion occurs after
 * this method-level selection.
 */
public final class EnterprisePlan {
  private EnterprisePlan() {}

  /**
   * Selects worker methods and persists each nonempty enterprise execution plan.
   *
   * <p>Ordinary test instances remain compatible unless strict enterprise mode is enabled. Sharded
   * enterprise methods must be independently executable; TestNG method/group dependencies are
   * rejected. Admission and persistence errors fail the plan before business test invocations
   * start.
   *
   * @throws IllegalArgumentException for invalid metadata, duplicate logical IDs, denied cases or
   *     empty assignments
   */
  public static List<IMethodInstance> select(List<IMethodInstance> methods) {
    List<IMethodInstance> selected = new ArrayList<>();
    Map<String, String> identities = new HashMap<>();
    boolean enterprise = false;
    String level = System.getProperty("testkit.level", "all");
    if (!Set.of("all", "smoke", "regression").contains(level))
      throw new IllegalArgumentException("Invalid enterprise case level selector");
    Map<EnterpriseExecution, Set<String>> planned = new LinkedHashMap<>(),
        assigned = new LinkedHashMap<>();
    for (IMethodInstance instance : methods) {
      if (!(instance.getInstance() instanceof EnterpriseRuntimeProvider provider)) {
        if (Boolean.getBoolean("testkit.enterprise"))
          throw new IllegalArgumentException("Enterprise mode requires EnterpriseRuntimeProvider");
        selected.add(instance);
        continue;
      }
      enterprise = true;
      EnterpriseExecution execution = Objects.requireNonNull(provider.enterpriseExecution());
      var method = instance.getMethod().getConstructorOrMethod().getMethod();
      CaseDefinition definition = CaseDefinition.resolve(method, instance.getInstance().getClass());
      execution.policy().validate(execution.run(), definition);
      String signature = method.toGenericString();
      String existing = identities.putIfAbsent(definition.id(), signature);
      if (existing != null && !existing.equals(signature))
        throw new IllegalArgumentException(
            "Duplicate enterprise logical case ID: " + definition.id());
      if ((instance.getMethod().getMethodsDependedUpon().length > 0
              || instance.getMethod().getGroupsDependedUpon().length > 0)
          && execution.shard().count() > 1)
        throw new IllegalArgumentException(
            "Sharded cases must be independently executable; method dependencies are unsupported");
      if (!"all".equals(level) && !definition.level().name().equalsIgnoreCase(level)) continue;
      planned.computeIfAbsent(execution, key -> new LinkedHashSet<>()).add(definition.id());
      if (execution.shard().owns(definition.id())) {
        selected.add(instance);
        assigned.computeIfAbsent(execution, key -> new LinkedHashSet<>()).add(definition.id());
      }
    }
    if ((enterprise || Boolean.getBoolean("testkit.enterprise")) && selected.isEmpty())
      throw new IllegalArgumentException("Enterprise plan selected zero tests");
    for (var entry : planned.entrySet()) {
      if (!assigned.containsKey(entry.getKey()))
        throw new IllegalArgumentException("Enterprise execution selected zero tests");
      EnterprisePlanManifest.write(
          entry.getKey(),
          entry.getValue(),
          assigned.get(entry.getKey()),
          Path.of(System.getProperty("testkit.plan.directory", "target/enterprise-plans")));
    }
    return selected;
  }
}
