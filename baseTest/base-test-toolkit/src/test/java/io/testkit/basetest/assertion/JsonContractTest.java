package io.testkit.basetest.assertion;

import io.testkit.basetest.config.ConfigLoader;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JsonContractTest {
  @Test
  public void validatesTypesEscapedPointersAndArrayBounds() throws Exception {
    var root =
        ConfigLoader.jsonMapper()
            .readTree(
                "{\"a/b\":{\"~key\":[1]},\"id\":\"x\",\"count\":2,\"price\":2.5,\"optional\":null}");
    JsonContract.of(root)
        .required("", JsonContract.Type.OBJECT)
        .required("/count", JsonContract.Type.INTEGER)
        .required("/price", JsonContract.Type.NUMBER)
        .required("/optional", JsonContract.Type.NULL)
        .nonBlank("/id")
        .arraySize("/a~1b/~0key", 1, 2);
    Assert.expectThrows(
        AssertionError.class,
        () -> JsonContract.of(root).required("/price", JsonContract.Type.INTEGER));
    Assert.expectThrows(
        AssertionError.class,
        () -> JsonContract.of(root).required("/missing", JsonContract.Type.NULL));
    Assert.expectThrows(
        AssertionError.class, () -> JsonContract.of(root).arraySize("/a~1b/~0key", 2, 3));
    Assert.expectThrows(
        IllegalArgumentException.class,
        () -> JsonContract.of(root).required("bad-pointer", JsonContract.Type.STRING));
  }

  @Test
  public void failuresDoNotExposeSensitiveValues() throws Exception {
    var root =
        ConfigLoader.jsonMapper().readTree("{\"token\":\"private-secret\",\"blank\":\"  \"}");
    var failure =
        Assert.expectThrows(
            AssertionError.class,
            () -> JsonContract.of(root).required("/token", JsonContract.Type.INTEGER));
    Assert.assertFalse(failure.getMessage().contains("private-secret"));
    Assert.expectThrows(AssertionError.class, () -> JsonContract.of(root).nonBlank("/blank"));
  }
}
