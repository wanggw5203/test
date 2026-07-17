package io.testkit.basetest.assertion;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.config.ConfigLoader;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class JsonDiff {
    private static final ObjectMapper MAPPER = ConfigLoader.jsonMapper();

    private JsonDiff() {
    }

    public static List<Difference> strict(Object expected, Object actual, String... ignoredPaths) {
        return compare(expected, actual, false, Set.of(ignoredPaths));
    }

    public static List<Difference> lenient(Object expected, Object actual, String... ignoredPaths) {
        return compare(expected, actual, true, Set.of(ignoredPaths));
    }

    private static List<Difference> compare(Object expected, Object actual, boolean allowExtraActual,
                                            Set<String> ignoredPaths) {
        List<Difference> differences = new ArrayList<>();
        compareNode(MAPPER.valueToTree(expected), MAPPER.valueToTree(actual), "$",
                allowExtraActual, new HashSet<>(ignoredPaths), differences);
        return List.copyOf(differences);
    }

    private static void compareNode(JsonNode expected, JsonNode actual, String path,
                                    boolean allowExtraActual, Set<String> ignored,
                                    List<Difference> differences) {
        if (isIgnored(path, ignored)) return;
        if (actual == null || actual.isMissingNode()) {
            differences.add(new Difference(Difference.Type.MISSING, path, text(expected), "<missing>"));
            return;
        }
        if (expected.getNodeType() != actual.getNodeType()) {
            differences.add(new Difference(Difference.Type.TYPE, path,
                    expected.getNodeType().name(), actual.getNodeType().name()));
            return;
        }
        if (expected.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = expected.fields();
            Set<String> expectedNames = new HashSet<>();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                expectedNames.add(field.getKey());
                compareNode(field.getValue(), actual.get(field.getKey()), path + "." + field.getKey(),
                        allowExtraActual, ignored, differences);
            }
            if (!allowExtraActual) {
                actual.fieldNames().forEachRemaining(name -> {
                    String childPath = path + "." + name;
                    if (!expectedNames.contains(name) && !isIgnored(childPath, ignored)) {
                        differences.add(new Difference(Difference.Type.UNEXPECTED, childPath,
                                "<missing>", text(actual.get(name))));
                    }
                });
            }
            return;
        }
        if (expected.isArray()) {
            int common = Math.min(expected.size(), actual.size());
            for (int index = 0; index < common; index++) {
                compareNode(expected.get(index), actual.get(index), path + "[" + index + "]",
                        allowExtraActual, ignored, differences);
            }
            for (int index = common; index < expected.size(); index++) {
                differences.add(new Difference(Difference.Type.MISSING, path + "[" + index + "]",
                        text(expected.get(index)), "<missing>"));
            }
            if (!allowExtraActual) {
                for (int index = common; index < actual.size(); index++) {
                    differences.add(new Difference(Difference.Type.UNEXPECTED, path + "[" + index + "]",
                            "<missing>", text(actual.get(index))));
                }
            }
            return;
        }
        if (!expected.equals(actual)) {
            differences.add(new Difference(Difference.Type.VALUE, path, text(expected), text(actual)));
        }
    }

    private static boolean isIgnored(String path, Set<String> ignored) {
        if (ignored.contains(path)) return true;
        for (String pattern : ignored) {
            String regex = java.util.Arrays.stream(pattern.split("\\*", -1))
                    .map(Pattern::quote)
                    .collect(Collectors.joining("[^.\\[\\]]+"));
            if (path.matches(regex)) return true;
        }
        return false;
    }

    private static String text(JsonNode node) {
        return node == null ? "<missing>" : node.toString();
    }
}
