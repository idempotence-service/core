FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :sender-simulator:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/sender-simulator/build/libs/sender-simulator.jar app.jar
COPY config ./config
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
