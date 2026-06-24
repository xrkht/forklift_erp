# Testing

The default Maven test run is safe on machines without Docker. Tests tagged with
`docker-integration` are excluded by default in `pom.xml`, so these commands run
only the unit and non-Docker tests:

```powershell
.\mvnw.cmd "-Dfrontend.skip=true" test
.\mvnw.cmd "-Dfrontend.skip=true" verify
```

Run the frontend packaging and encoding checks separately when you do not need a
full Maven lifecycle:

```powershell
npm.cmd run check
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

Before using a data restore backup file, validate it with the super-admin
dry-run endpoint `POST /api/admin/data-restore/dry-run`. The actual restore
endpoint still requires the restore switch and `RESTORE-DATA-BACKUP`
confirmation phrase.

Before starting a production instance, run with the `prod` profile in an
environment that provides these required variables:

```powershell
$env:FORKLIFT_ERP_JWT_SECRET = "<32-byte-or-longer-secret>"
$env:FORKLIFT_ERP_JWT_EXPIRATION = "86400000"
$env:FORKLIFT_ERP_DB_PASSWORD = "<database-password>"
$env:FORKLIFT_ERP_ADMIN_PASSWORD = "<bootstrap-admin-password>"
.\mvnw.cmd "-Dspring-boot.run.profiles=prod" spring-boot:run
```
