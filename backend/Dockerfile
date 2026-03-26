FROM gradle:9.2.0-jdk25-corretto AS build
WORKDIR /app

COPY build.gradle settings.gradle gradlew gradlew.bat ./
COPY gradle ./gradle
COPY src ./src

RUN chmod +x gradlew && ./gradlew clean installDist --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/build/install/idem-core-back/ /app/

ENV SERVER_PORT=8080
EXPOSE 8080

ENTRYPOINT ["./bin/idem-core-back"]
