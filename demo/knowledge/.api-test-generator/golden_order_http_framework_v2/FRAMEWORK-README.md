# TestNG framework skeleton

Test class extends your BaseTest and implements RuntimeProvider. DataProvider delegates to DataDriveUtil; CaseContext is bound by BaseTest. YAML selects JSON5 payloads. JsonDiff provides strict assertions.

- GoldenOrderHttpTestApi: HTTP/Dubbo transport using framework clients.
- GoldenOrderHttpTestDataBuilder: wire verified business data and cleanup.
- Runtime: configure real identities/dataFactory at the composition root; no fake BO/VO or identity provider is invented.
- Run: mvn -f .api-test-generator/golden_order_http_framework_v2/pom.xml test
- Requires io.testkit base-test-toolkit 1.1.0-SNAPSHOT in Maven repository. Compile first using test-compile.
- Framework suite/environment and identity configuration must be reviewed before live execution.
