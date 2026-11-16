FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 10001 --create-home app
COPY --from=build /workspace/target/execution-service-0.0.1-SNAPSHOT.jar app.jar
USER 10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
