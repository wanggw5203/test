package io.testkit.basetest.data;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.assertion.JsonDiff;
import io.testkit.basetest.config.ConfigLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class DataDriveUtil {
    private DataDriveUtil() {
    }

    public static Object[][] loadTestData(Method method, String suiteName) {
        String packagePath = method.getDeclaringClass().getPackageName().replace('.', '/');
        String resource = packagePath + "/" + suiteName + ".yaml";
        URL url = Thread.currentThread().getContextClassLoader().getResource(resource);
        if (url == null) {
            throw new IllegalArgumentException("Test data suite not found: " + resource);
        }
        if (!"file".equalsIgnoreCase(url.getProtocol())) {
            throw new IllegalArgumentException("Classpath suite must be a file resource so relative case files can be resolved: " + resource);
        }
        try {
            return loadTestData(method, Paths.get(url.toURI()));
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid suite resource URI: " + resource, e);
        }
    }

    public static Object[][] loadTestData(Method method, Path suiteFile) {
        TestDataDocument document = ConfigLoader.read(suiteFile, TestDataDocument.class);
        if (document.getTestDataSuite() == null) {
            throw new IllegalArgumentException("Missing root node testDataSuite: " + suiteFile);
        }
        Path baseDirectory = suiteFile.toAbsolutePath().getParent();
        List<Object[]> rows = new ArrayList<>();
        for (TestCaseData testCase : document.getTestDataSuite().getCases()) {
            if (Boolean.FALSE.equals(testCase.getEnabled())) {
                continue;
            }
            if (!document.getTestDataSuite().getDebugCases().isEmpty()
                    && !document.getTestDataSuite().getDebugCases().contains(testCase.getName())) {
                continue;
            }
            rows.add(toArguments(method, baseDirectory, testCase));
        }
        return rows.toArray(Object[][]::new);
    }

    private static Object[] toArguments(Method method, Path baseDirectory, TestCaseData testCase) {
        List<Path> payloads = new ArrayList<>();
        testCase.getRequest().forEach(name -> payloads.add(baseDirectory.resolve(name)));
        testCase.getExpect().forEach(name -> payloads.add(baseDirectory.resolve(name)));

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
                throw new IllegalArgumentException("Case '" + testCase.getName()
                        + "' has fewer payload files than method parameters");
            }
            Path payload = payloads.get(payloadIndex++);
            arguments[index] = readPayload(payload, type);
        }
        if (payloadIndex != payloads.size()) {
            throw new IllegalArgumentException("Case '" + testCase.getName()
                    + "' has more payload files than method parameters");
        }
        return arguments;
    }

    private static Object readPayload(Path payload, Type type) {
        ObjectMapper mapper = ConfigLoader.mapper(payload.getFileName().toString());
        JavaType javaType = mapper.getTypeFactory().constructType(type);
        try (InputStream input = Files.newInputStream(payload)) {
            return mapper.readValue(input, javaType);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot read test payload: " + payload, e);
        }
    }

    public static List<String> diffFieldValue(Object expected, Object actual) {
        return JsonDiff.strict(expected, actual).stream().map(Object::toString).toList();
    }

    public static List<String> diffFieldValueLenient(Object expected, Object actual) {
        return JsonDiff.lenient(expected, actual).stream().map(Object::toString).toList();
    }
}
