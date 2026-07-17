# --- Etapa de build ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Cache de dependencias
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q package -DskipTests

# --- Etapa de runtime ---
FROM eclipse-temurin:21-jre-jammy AS runtime

RUN groupadd --system spring && useradd --system --gid spring spring
USER spring:spring

WORKDIR /app
COPY --from=build --chown=spring:spring /build/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
