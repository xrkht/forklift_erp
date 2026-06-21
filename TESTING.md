# Testing

The default Maven test run is safe on machines without Docker. Tests tagged with
`docker-integration` are excluded by default in `pom.xml`, so these commands run
only the unit and non-Docker tests:

```powershell
.\mvnw.cmd test
.\mvnw.cmd verify
```

Use the Docker integration profile when you want to run the Spring Boot API
integration tests backed by Testcontainers MySQL:

```powershell
.\mvnw.cmd -Pdocker-integration-tests test
```

That profile requires a working Docker daemon and pulls `mysql:8.0.43` through
Testcontainers. The integration test datasource in
`src/test/resources/application.yml` is a sentinel value and is overridden by
`TestcontainersDatabaseSupport` when the container starts.
