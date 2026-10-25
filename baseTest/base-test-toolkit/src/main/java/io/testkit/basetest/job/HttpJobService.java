package io.testkit.basetest.job;

import io.testkit.basetest.http.ApiClient;
import java.util.*;

/**
 * Adapter for the explicitly documented test gateway contract, NOT an assumed XXL-JOB native API.
 */
public final class HttpJobService implements JobService {
  private final ApiClient client;
  private final String environment;
  private final Map<String, String> headers;

  public HttpJobService(ApiClient client, String environment, Map<String, String> headers) {
    this.client = Objects.requireNonNull(client);
    this.environment = environment;
    this.headers = Map.copyOf(headers);
  }

  @Override
  public JobExecution trigger(JobRequest request) {
    if (!Objects.equals(environment, request.environment()))
      throw new IllegalArgumentException("Job environment mismatch");
    return client
        .request("POST", "/test-jobs", headers, request)
        .assertStatus(202)
        .as(JobExecution.class);
  }

  @Override
  public Optional<JobExecution> query(String id) {
    String encoded =
        java.net.URLEncoder.encode(id, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    var response = client.request("GET", "/test-jobs/" + encoded, headers, null);
    return response.status() == 404
        ? Optional.empty()
        : Optional.of(response.assertStatus(200).as(JobExecution.class));
  }
}
