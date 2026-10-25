package io.testkit.basetest.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.testkit.basetest.data.CaseContextHolder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Diagnostic request/response logs, deliberately separate from the payload-free result publisher.
 */
public final class ProtocolCallLog {
  private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
  private static final Object OUTPUT_LOCK = new Object();
  private static final AtomicBoolean WARNED = new AtomicBoolean();
  private static final Set<String> SECRET_KEYS =
      Set.of(
          "password",
          "passwd",
          "pwd",
          "token",
          "secret",
          "authorization",
          "cookie",
          "credential",
          "signature",
          "apikey",
          "accesskey",
          "privatekey",
          "sessionid",
          "mobile",
          "phone",
          "email",
          "idcard",
          "cardnumber",
          "bankaccount",
          "密码",
          "密钥",
          "令牌",
          "手机号",
          "身份证",
          "银行卡");

  private ProtocolCallLog() {}

  public static Call start(String protocol, String target, Map<String, ?> request) {
    try {
      if (!Boolean.parseBoolean(System.getProperty("testkit.log.enabled", "true")))
        return new Call(null, null);
      Options options = new Options();
      Map<String, Object> context = new LinkedHashMap<>();
      context.put("type", "protocol-call");
      context.put("callId", UUID.randomUUID().toString());
      context.put("protocol", protocol);
      context.put("target", safeTarget(target));
      context.put("thread", Thread.currentThread().getName());
      context.put(
          "environment",
          System.getProperty("test.env", System.getenv().getOrDefault("TEST_ENV", "local")));
      TraceContext.current()
          .ifPresent(
              trace -> {
                context.put("runId", trace.runId());
                context.put("caseId", trace.caseId());
              });
      CaseContextHolder.current()
          .ifPresent(value -> context.put("caseName", scrubText(value.name())));
      Call call = new Call(context, options);
      call.emit("REQUEST", "request", safeFields(request, options));
      return call;
    } catch (Exception failure) {
      warning(failure);
      return new Call(null, null);
    }
  }

  public static final class Call {
    private final Map<String, Object> context;
    private final Options options;
    private final long nanos = System.nanoTime();
    private boolean finished;

    private Call(Map<String, Object> context, Options options) {
      this.context = context;
      this.options = options;
    }

    public void complete(Map<String, ?> response, Throwable failure) {
      if (context == null || finished) return;
      finished = true;
      try {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("durationMillis", (System.nanoTime() - nanos) / 1_000_000);
        fields.put("response", safeFields(response, options));
        if (failure != null) {
          List<String> causes = new ArrayList<>();
          Throwable current = failure;
          for (int i = 0; current != null && i < 5; i++, current = current.getCause())
            causes.add(current.getClass().getName());
          fields.put("errorTypes", causes);
          // Exception messages may echo tokens, SQL or request bodies. Log code locations, not
          // messages.
          fields.put(
              "errorFrames",
              Arrays.stream(failure.getStackTrace())
                  .limit(6)
                  .map(
                      frame ->
                          frame.getClassName()
                              + "."
                              + frame.getMethodName()
                              + ":"
                              + frame.getLineNumber())
                  .toList());
        }
        emit(failure == null ? "RESPONSE" : "ERROR", null, fields);
      } catch (Exception logging) {
        warning(logging);
      }
    }

    private void emit(String phase, String field, Object value) {
      try {
        Map<String, Object> event = new LinkedHashMap<>(context);
        event.put("time", Instant.now().toString());
        event.put("phase", phase);
        if (field == null) event.putAll((Map<String, Object>) value);
        else event.put(field, value);
        String line = JSON.writeValueAsString(event);
        synchronized (OUTPUT_LOCK) {
          if (options.console) System.out.println("[TESTKIT CALL] " + line);
          if (!options.file.isBlank()) {
            Path path =
                Path.of(
                        options.file.replace(
                            "${pid}", Long.toString(ProcessHandle.current().pid())))
                    .toAbsolutePath();
            Files.createDirectories(path.getParent());
            byte[] bytes = (line + "\n").getBytes(StandardCharsets.UTF_8);
            if (Files.exists(path) && Files.size(path) + bytes.length > options.maxFileBytes) {
              for (int i = 2; i >= 1; i--) {
                Path previous = Path.of(path + "." + i);
                if (Files.exists(previous))
                  Files.move(
                      previous, Path.of(path + "." + (i + 1)), StandardCopyOption.REPLACE_EXISTING);
              }
              Files.move(path, Path.of(path + ".1"), StandardCopyOption.REPLACE_EXISTING);
            }
            Files.write(path, bytes, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
          }
        }
      } catch (Exception failure) {
        warning(failure);
      }
    }
  }

  private static final class Options {
    final boolean console = Boolean.parseBoolean(System.getProperty("testkit.log.console", "true"));
    final boolean bodies = Boolean.parseBoolean(System.getProperty("testkit.log.bodies", "false"));
    final String file =
        System.getProperty("testkit.log.file", "target/logs/api-calls-${pid}.jsonl");
    final int maxChars = number("testkit.log.maxBodyChars", 4096, 128, 65536);
    final int maxFileBytes = number("testkit.log.maxFileBytes", 10485760, 1024, 104857600);
    final Set<String> keys = new HashSet<>(SECRET_KEYS);

    Options() {
      for (String key : System.getProperty("testkit.log.redactKeys", "").split(","))
        if (!key.isBlank()) keys.add(normalize(key));
    }
  }

  private static Map<String, Object> safeFields(Map<String, ?> fields, Options options) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (fields == null) return result;
    int count = 0;
    for (var field : fields.entrySet()) {
      if (++count > 64) {
        result.put("_truncated", true);
        break;
      }
      String key = field.getKey();
      if (isSecret(key, options)) result.put(key, "***");
      else if ((key.equals("body") || key.equals("arguments")) && !options.bodies)
        result.put(key, "<body logging disabled>");
      else
        result.put(
            key,
            safeValue(field.getValue(), options, key.equals("body") || key.equals("arguments")));
    }
    return result;
  }

  private static Object safeValue(Object value, Options options, boolean payload) {
    try {
      if (value == null) return null;
      JsonNode tree;
      if (payload && value instanceof String text) {
        if (text.length() > 262144) return "<oversized text omitted>";
        String trimmed = text.trim();
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) tree = JSON.readTree(text);
        else return "<non-JSON text omitted>";
      } else if (value instanceof byte[]) return "<binary omitted>";
      else tree = JSON.valueToTree(value);
      Object cleaned = clean(tree, options, 0, new int[] {512});
      String encoded = JSON.writeValueAsString(cleaned);
      if (encoded.length() > options.maxChars)
        return Map.of(
            "_truncated",
            true,
            "preview",
            encoded.substring(0, options.maxChars),
            "originalChars",
            encoded.length());
      return cleaned;
    } catch (Exception ignored) {
      return "<unserializable or non-JSON payload omitted>";
    }
  }

  private static Object clean(JsonNode node, Options options, int depth, int[] remaining) {
    if (node == null || node.isNull()) return null;
    if (depth > 8 || --remaining[0] < 0) return "<depth or item limit>";
    if (node.isObject()) {
      Map<String, Object> result = new LinkedHashMap<>();
      var fields = node.fields();
      while (fields.hasNext() && remaining[0] > 0) {
        var field = fields.next();
        if (isSecret(field.getKey(), options)) {
          remaining[0]--;
          result.put(field.getKey(), "***");
        } else result.put(field.getKey(), clean(field.getValue(), options, depth + 1, remaining));
      }
      return result;
    }
    if (node.isArray()) {
      List<Object> result = new ArrayList<>();
      for (JsonNode element : node) {
        if (remaining[0] <= 0) break;
        result.add(clean(element, options, depth + 1, remaining));
      }
      return result;
    }
    if (node.isTextual()) {
      String text = node.textValue();
      if (text.trim().startsWith("{") || text.trim().startsWith("[")) {
        try {
          return clean(JSON.readTree(text), options, depth + 1, remaining);
        } catch (Exception ignored) {
          return "<unparseable embedded JSON omitted>";
        }
      }
      return scrubText(text);
    }
    return JSON.convertValue(node, Object.class);
  }

  private static boolean isSecret(String key, Options options) {
    String normalized = normalize(key);
    return options.keys.stream().anyMatch(part -> !part.isBlank() && normalized.contains(part));
  }

  private static String normalize(String text) {
    return text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  private static String scrubText(String text) {
    return text.replaceAll("(?i)Bearer\\s+[^\\s\"',;]+", "Bearer ***")
        .replaceAll(
            "(?i)(password|passwd|pwd|token|secret|authorization|api[-_]?key|cookie)\\s*[:=]\\s*[^\\s,;]+",
            "$1=***");
  }

  private static String safeTarget(String text) {
    try {
      URI uri = URI.create(text);
      if (uri.getScheme() == null || uri.getHost() == null) {
        String safe = scrubText(text);
        return safe.substring(0, Math.min(safe.length(), 1024));
      }
      String host = uri.getHost();
      String safe =
          uri.getScheme()
              + "://"
              + host
              + (uri.getPort() < 0 ? "" : ":" + uri.getPort())
              + (uri.getRawPath() == null ? "" : uri.getRawPath());
      if (uri.getRawQuery() != null) safe += "?<query values redacted>";
      return safe.substring(0, Math.min(safe.length(), 1024));
    } catch (Exception ignored) {
      return "<target omitted>";
    }
  }

  private static int number(String key, int fallback, int min, int max) {
    try {
      return Math.max(
          min,
          Math.min(max, Integer.parseInt(System.getProperty(key, Integer.toString(fallback)))));
    } catch (RuntimeException ignored) {
      return fallback;
    }
  }

  private static void warning(Exception failure) {
    if (WARNED.compareAndSet(false, true))
      System.err.println(
          "[TESTKIT CALL] Diagnostic logging unavailable: "
              + failure.getClass().getSimpleName()
              + "; original call outcome preserved");
  }
}
