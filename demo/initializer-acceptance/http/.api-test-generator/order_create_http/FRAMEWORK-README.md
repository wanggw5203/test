# TestNG framework skeleton

Test class extends your BaseTest and uses the project's installed runtime. DataProvider delegates to DataDriveUtil; CaseContext is bound by BaseTest. YAML selects JSON5 payloads. JsonDiff provides strict assertions.

- OrderCreateHttpGeneratedTestsApi: HTTP/Dubbo transport using framework clients.
- OrderCreateHttpGeneratedTestsDataBuilder: wire verified business data and cleanup.
- Runtime: configure real identities/dataFactory at the composition root; no fake BO/VO or identity provider is invented.
- Run with the existing project's TestNG runner and this interface's testng.xml.
- main: API transport and business data hooks; test: TestNG methods and YAML/JSON5 resources.
- Project initialization, build dependencies, bootstrap and environment configuration belong to the external project initializer.
- Requires framework dependencies on the main compile classpath, not only test scope. No build file is generated or modified.
- Framework suite/environment and identity configuration must be reviewed before live execution.
