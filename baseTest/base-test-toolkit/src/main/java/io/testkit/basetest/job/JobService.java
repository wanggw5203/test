package io.testkit.basetest.job;

import java.util.Optional;

public interface JobService {
    JobExecution trigger(JobRequest request);
    Optional<JobExecution> query(String executionId);

    static JobService unsupported() {
        return new JobService() {
            public JobExecution trigger(JobRequest request) {
                throw new UnsupportedOperationException("No job service adapter is configured");
            }
            public Optional<JobExecution> query(String executionId) { return Optional.empty(); }
        };
    }
}
