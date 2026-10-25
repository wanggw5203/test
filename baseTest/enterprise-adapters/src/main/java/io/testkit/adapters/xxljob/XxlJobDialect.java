package io.testkit.adapters.xxljob;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Version seam: new admin/SSO contracts implement this interface without changing JobService users.
 */
public interface XxlJobDialect {
  String loginPath();

  String triggerPath();

  String logsPath();

  default String cookieName() {
    return "XXL_JOB_LOGIN_IDENTITY";
  }

  default Map<String, String> login(String username, String password) {
    return Map.of("userName", username, "password", password, "ifRemember", "");
  }

  default Map<String, String> trigger(int id, String parameter) {
    return Map.of("id", Integer.toString(id), "executorParam", parameter, "addressList", "");
  }

  default Map<String, String> logs(int group, int job) {
    return Map.of(
        "start",
        "0",
        "length",
        "100",
        "jobGroup",
        Integer.toString(group),
        "jobId",
        Integer.toString(job),
        "logStatus",
        "0",
        "filterTime",
        "");
  }

  default boolean accepted(JsonNode body) {
    JsonNode code = body.path("code");
    return code.isIntegralNumber() && code.canConvertToInt() && code.intValue() == 200;
  }

  /**
   * A version dialect must identify a conclusive rejection; unsupported payloads remain uncertain.
   */
  default boolean rejected(JsonNode body) {
    JsonNode code = body.path("code");
    return code.isIntegralNumber() && code.canConvertToInt() && code.intValue() == 500;
  }

  default JsonNode rows(JsonNode body) {
    JsonNode rows = body.path("data");
    if (!rows.isArray())
      throw new IllegalStateException(
          "Unsupported XXL-JOB log response; select the correct dialect");
    for (JsonNode row : rows)
      if (!row.path("id").isIntegralNumber()
          || !row.path("id").canConvertToLong()
          || row.path("id").longValue() <= 0
          || !validCode(row.path("triggerCode"))
          || !validCode(row.path("handleCode")))
        throw new IllegalStateException("Malformed XXL-JOB execution log");
    return rows;
  }

  private static boolean validCode(JsonNode code) {
    return code.isIntegralNumber() && code.canConvertToInt() && code.intValue() >= 0;
  }

  static XxlJobDialect forVersion(String version) {
    if (!"2.4.1".equals(version) && !"3.1.1".equals(version))
      throw new IllegalArgumentException(
          "Unverified XXL-JOB version requires an explicit dialect: " + version);
    return new XxlJobDialect() {
      public String loginPath() {
        return "login";
      }

      public String triggerPath() {
        return "jobinfo/trigger";
      }

      public String logsPath() {
        return "joblog/pageList";
      }
    };
  }

  /**
   * @deprecated Use {@link #forVersion(String)} to make version selection explicit.
   */
  @Deprecated(since = "1.1.0", forRemoval = false)
  static XxlJobDialect legacy(String version) {
    return forVersion(version);
  }
}
