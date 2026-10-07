# Multi-stage: the JDK and the Maven cache stay out of the final image.
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Copy the wrapper and POM first so dependency resolution is cached and only
# re-runs when the POM actually changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run unprivileged: a container process has no reason to be root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

COPY --from=build /build/target/*.jar app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=20s \
    CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
