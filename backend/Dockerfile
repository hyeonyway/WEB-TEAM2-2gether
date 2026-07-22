FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies || true

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]