FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :core-service:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/core-service/build/libs/core-service.jar app.jar
COPY config ./config
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
