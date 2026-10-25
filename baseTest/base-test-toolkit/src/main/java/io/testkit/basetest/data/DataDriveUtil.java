package io.testkit.basetest.data;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.config.ConfigLoader;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URL;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DataDriveUtil {
  private DataDriveUtil() {}

  public static Object[][] loadTestData(Method method, String suiteName) {
    String packagePath = method.getDeclaringClass().getPackageName().replace('.', '/');
    String resource = packagePath + "/" + suiteName + ".yaml";
    URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
    if (url == null) {
      throw new IllegalArgumentException("Test data suite not found: " + resource);
    }
    try (InputStream input = open(url)) {
      TestDataDocument document =
          ConfigLoader.mapper(resource).readValue(input, TestDataDocument.class);
      return load(method, document, name -> new URL(url, name));
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read suite: " + resource, e);
    }
  }

  public static Object[][] loadTestData(Method method, Path suiteFile) {
    TestDataDocument document = ConfigLoader.read(suiteFile, TestDataDocument.class);
    Path baseDirectory = suiteFile.toAbsolutePath().getParent();
    return load(method, document, name -> baseDirectory.resolve(name).toUri().toURL());
  }

  @FunctionalInterface
  private interface PayloadResolver {
    URL resolve(String name) throws IOException;
  }

  private static Object[][] load(
      Method method, TestDataDocument document, PayloadResolver resolver) {
    if (document == null || document.getTestDataSuite() == null)
      throw new IllegalArgumentException("Missing root node testDataSuite");
    List<Object[]> rows = new ArrayList<>();
    for (TestCaseData testCase : document.getTestDataSuite().getCases()) {
      if (Boolean.FALSE.equals(testCase.getEnabled())) {
        continue;
      }
      if (!document.getTestDataSuite().getDebugCases().isEmpty()
          && !document.getTestDataSuite().getDebugCases().contains(testCase.getName())) {
        continue;
      }
      rows.add(toArguments(method, resolver, testCase));
    }
    return rows.toArray(Object[][]::new);
  }

  private static Object[] toArguments(
      Method method, PayloadResolver resolver, TestCaseData testCase) {
    List<String> payloads = new ArrayList<>();
    payloads.addAll(testCase.getRequest());
    payloads.addAll(testCase.getExpect());

    Type[] parameterTypes = method.getGenericParameterTypes();
    Object[] arguments = new Object[parameterTypes.length];
    int payloadIndex = 0;
    for (int index = 0; index < parameterTypes.length; index++) {
      Type type = parameterTypes[index];
      if (type == CaseContext.class) {
        arguments[index] = CaseContext.from(testCase);
        continue;
      }
      if (payloadIndex >= payloads.size()) {
        throw new IllegalArgumentException(
            "Case '" + testCase.getName() + "' has fewer payload files than method parameters");
      }
      String payload = payloads.get(payloadIndex++);
      try {
        arguments[index] = readPayload(resolver.resolve(payload), payload, type);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot resolve test payload: " + payload, e);
      }
    }
    if (payloadIndex != payloads.size()) {
      throw new IllegalArgumentException(
          "Case '" + testCase.getName() + "' has more payload files than method parameters");
    }
    return arguments;
  }

  private static Object readPayload(URL payload, String name, Type type) {
    ObjectMapper mapper =
        ConfigLoader.mapper(name)
            .copy()
            .registerModule(new io.testkit.basetest.config.StrictTypeModule());
    JavaType javaType = mapper.getTypeFactory().constructType(type);
    try (InputStream input = open(payload)) {
      return mapper.readValue(input, javaType);
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot read test payload: " + payload, e);
    }
  }

  public static List<String> diffFieldValue(Object expected, Object actual) {
    return JsonDiff.strict(expected, actual).stream().map(Object::toString).toList();
  }

  private static InputStream open(URL url) throws IOException {
    var connection = url.openConnection();
    connection.setUseCaches(false);
    return connection.getInputStream();
  }

  public static List<String> diffFieldValueLenient(Object expected, Object actual) {
    return JsonDiff.lenient(expected, actual).stream().map(Object::toString).toList();
  }
}
