package io.github.apitestkit.scaffold;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class Json5Writer {
    private Json5Writer() {
    }

    static String write(String comment, Object value) {
        StringBuilder out = new StringBuilder();
        if (comment != null && !comment.isBlank()) {
            out.append("// ").append(comment.replace('\n', ' ')).append('\n');
        }
        append(out, value, 0);
        out.append('\n');
        return out.toString();
    }

    private static void append(StringBuilder out, Object value, int indent) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof Map) {
            appendMap(out, castMap(value), indent);
        } else if (value instanceof List) {
            appendList(out, (List<?>) value, indent);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else {
            out.append('"').append(escape(String.valueOf(value))).append('"');
        }
    }

    private static void appendMap(StringBuilder out, Map<String, Object> map, int indent) {
        out.append('{');
        if (!map.isEmpty()) {
            out.append('\n');
            Iterator<Map.Entry<String, Object>> iterator = map.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Object> entry = iterator.next();
                indent(out, indent + 2);
                out.append('"').append(escape(entry.getKey())).append("\": ");
                append(out, entry.getValue(), indent + 2);
                if (iterator.hasNext()) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, indent);
        }
        out.append('}');
    }

    private static void appendList(StringBuilder out, List<?> list, int indent) {
        out.append('[');
        if (!list.isEmpty()) {
            out.append('\n');
            for (int i = 0; i < list.size(); i++) {
                indent(out, indent + 2);
                append(out, list.get(i), indent + 2);
                if (i + 1 < list.size()) {
                    out.append(',');
                }
                out.append('\n');
            }
            indent(out, indent);
        }
        out.append(']');
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    private static void indent(StringBuilder out, int indent) {
        out.append(" ".repeat(Math.max(0, indent)));
    }

    private static String escape(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
