package io.testkit.basetest.enterprise;

import java.util.Set;
import org.testng.Assert;
import org.testng.annotations.Test;

/** Compatibility and fail-closed resolution tests for the public metadata contract. */
@SuppressWarnings("deprecation")
public class TestCaseMetadataTest {
  @Test
  public void shouldResolveEquivalentLegacyAndCurrentDeclarations() throws Exception {
    CaseDefinition legacy =
        CaseDefinition.resolve(
            LegacyDeclaration.class.getMethod("shouldReadOrder"), LegacyDeclaration.class);
    CaseDefinition current =
        CaseDefinition.resolve(
            CurrentDeclaration.class.getMethod("shouldReadOrder"), CurrentDeclaration.class);
    Assert.assertEquals(current, legacy);
    Assert.assertEquals(current.id(), "orders.read");
  }

  @Test
  public void shouldInheritOwnershipAndKeepDestructiveClassRestriction() throws Exception {
    CaseDefinition definition =
        CaseDefinition.resolve(
            MethodOverrideDeclaration.class.getMethod("shouldDeleteOrder"),
            MethodOverrideDeclaration.class);
    Assert.assertEquals(definition.id(), "orders.delete");
    Assert.assertEquals(definition.owner(), "orders-platform");
    Assert.assertEquals(definition.team(), "orders");
    Assert.assertEquals(definition.service(), "orders-api");
    Assert.assertEquals(definition.level(), CaseLevel.REGRESSION);
    Assert.assertTrue(definition.destructive());
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            ExecutionPolicy.localSafe()
                .validate(new RunMetadata("run", "worker", "qa", "orders"), definition));
  }

  @Test
  public void shouldMigrateSubclassesWithoutConflictingInheritedLegacyMetadata() throws Exception {
    CaseDefinition definition =
        CaseDefinition.resolve(
            MigratedChild.class.getMethod("shouldReadOrder"), MigratedChild.class);
    Assert.assertEquals(definition.id(), "orders.migrated.shouldReadOrder");
    Assert.assertEquals(definition.owner(), "orders-platform");
    CaseDefinition inherited =
        CaseDefinition.resolve(LegacyChild.class.getMethod("shouldReadOrder"), LegacyChild.class);
    Assert.assertEquals(inherited.id(), "orders.legacy.shouldReadOrder");
  }

  @Test
  public void shouldRejectMixedDeclarationsOnSameElement() throws Exception {
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            CaseDefinition.resolve(
                MixedMethod.class.getMethod("shouldReadOrder"), MixedMethod.class));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            CaseDefinition.resolve(
                MixedClass.class.getMethod("shouldReadOrder"), MixedClass.class));
  }

  @Test
  public void shouldRejectMissingOrInvalidRequiredMetadata() throws Exception {
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            CaseDefinition.resolve(
                MissingMetadata.class.getMethod("shouldReadOrder"), MissingMetadata.class));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () ->
            CaseDefinition.resolve(
                MissingOwner.class.getMethod("shouldReadOrder"), MissingOwner.class));
    Assert.expectThrows(IllegalArgumentException.class, () -> new Shard(0, 1).owns(" "));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPolicy(Set.of("qa production"), Set.of(), false));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> new ExecutionPolicy(Set.of("qa"), Set.of("orders/api"), false));
  }

  public static class LegacyDeclaration {
    @CaseInfo(
        id = "orders.read",
        owner = "orders-platform",
        team = "orders",
        service = "orders-api",
        level = CaseLevel.SMOKE)
    public void shouldReadOrder() {}
  }

  public static class CurrentDeclaration {
    @TestCaseMetadata(
        id = "orders.read",
        owner = "orders-platform",
        team = "orders",
        service = "orders-api",
        level = CaseLevel.SMOKE)
    public void shouldReadOrder() {}
  }

  @CaseInfo(
      id = "orders.legacy",
      owner = "orders-platform",
      team = "orders",
      service = "orders-api",
      level = CaseLevel.SMOKE)
  public static class LegacyBase {
    public void shouldReadOrder() {}
  }

  public static class LegacyChild extends LegacyBase {}

  @TestCaseMetadata(
      id = "orders.migrated",
      owner = "orders-platform",
      team = "orders",
      service = "orders-api")
  public static class MigratedChild extends LegacyBase {}

  @CaseInfo(
      owner = "orders-platform",
      team = "orders",
      service = "orders-api",
      level = CaseLevel.SMOKE,
      destructive = true)
  public static class MethodOverrideDeclaration {
    @TestCaseMetadata(id = "orders.delete")
    public void shouldDeleteOrder() {}
  }

  public static class MixedMethod {
    @CaseInfo(
        id = "orders.read",
        owner = "orders-platform",
        team = "orders",
        service = "orders-api")
    @TestCaseMetadata(
        id = "orders.read",
        owner = "orders-platform",
        team = "orders",
        service = "orders-api")
    public void shouldReadOrder() {}
  }

  @CaseInfo(id = "orders", owner = "orders-platform", team = "orders", service = "orders-api")
  @TestCaseMetadata(
      id = "orders",
      owner = "orders-platform",
      team = "orders",
      service = "orders-api")
  public static class MixedClass {
    public void shouldReadOrder() {}
  }

  public static class MissingMetadata {
    public void shouldReadOrder() {}
  }

  public static class MissingOwner {
    @TestCaseMetadata(id = "orders.read", team = "orders", service = "orders-api")
    public void shouldReadOrder() {}
  }
}
