FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=UTC"

RUN apt-get update \
    && apt-get install -y --no-install-recommends default-mysql-client gzip \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/build/libs/*.jar app.jar
COPY --from=build /app/src/main/resources /app/db/resources
COPY scripts/start-server.sh /app/scripts/start-server.sh

RUN chmod +x /app/scripts/start-server.sh \
    && mkdir -p /app/db-snapshots

EXPOSE 8080
ENTRYPOINT ["/app/scripts/start-server.sh"]
CMD ["java", "-jar", "app.jar"]
