FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :receiver-simulator:bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/receiver-simulator/build/libs/receiver-simulator.jar app.jar
COPY config ./config
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
