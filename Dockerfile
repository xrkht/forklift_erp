FROM eclipse-temurin:21-jre-jammy

ARG APP_UID=10001
ARG APP_GID=10001

RUN groupadd --system --gid "${APP_GID}" forklift \
    && useradd --system --uid "${APP_UID}" --gid "${APP_GID}" --home-dir /app forklift

WORKDIR /app

COPY --chown=forklift:forklift target/docker/app.jar /app/app.jar

USER forklift

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]
