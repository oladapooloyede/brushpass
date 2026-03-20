FROM registry.access.redhat.com/ubi8/openjdk-21:latest AS build
USER root
WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package -DskipTests

FROM registry.access.redhat.com/ubi8/openjdk-21-runtime:latest
WORKDIR /app
COPY --from=build /build/target/quarkus-app /app
EXPOSE 8080
CMD ["java", "-jar", "/app/quarkus-run.jar"]
