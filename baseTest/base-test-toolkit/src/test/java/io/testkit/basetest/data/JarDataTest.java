package io.testkit.basetest.data;

import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.jar.*;
import org.testng.Assert;
import org.testng.annotations.Test;

public class JarDataTest {
  public void sample(CaseContext context, Integer request, Integer expected) {}

  @Test
  public void loadsRelativePayloadsFromJar() throws Exception {
    Path jar = Files.createTempFile("data-suite", ".jar");
    ClassLoader previous = Thread.currentThread().getContextClassLoader();
    try {
      try (var out = new JarOutputStream(Files.newOutputStream(jar))) {
        entry(
            out,
            "packed.yaml",
            "testDataSuite:\n  cases:\n    - name: from-jar\n      request: [request.json]\n      expect: [expected.json]\n");
        entry(out, "request.json", "42");
        entry(out, "expected.json", "43");
      }
      try (var loader = new URLClassLoader(new java.net.URL[] {jar.toUri().toURL()}, previous)) {
        Thread.currentThread().setContextClassLoader(loader);
        var rows =
            DataDriveUtil.loadTestData(
                getClass().getMethod("sample", CaseContext.class, Integer.class, Integer.class),
                "packed");
        Assert.assertEquals(rows.length, 1);
        Assert.assertEquals(rows[0][1], 42);
        Assert.assertEquals(rows[0][2], 43);
      }
    } finally {
      Thread.currentThread().setContextClassLoader(previous);
      Files.deleteIfExists(jar);
    }
  }

  private static void entry(JarOutputStream out, String name, String content) throws Exception {
    out.putNextEntry(new JarEntry("io/testkit/basetest/data/" + name));
    out.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    out.closeEntry();
  }
}
