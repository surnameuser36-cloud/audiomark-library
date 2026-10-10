FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /build/target/audiomark-server-1.0.0.jar /app/server.jar
COPY --from=build /build/target/lib /app/lib
RUN mkdir -p /app/data/audio /app/data/db
EXPOSE 8080
ENTRYPOINT ["java", "-cp", "/app/server.jar:/app/lib/*", "ru.audiomark.server.AudioLibraryServer"]

