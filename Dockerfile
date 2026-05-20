# ── Stage 1: Build ────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
# Загружаем зависимости отдельным слоем (кеш Docker)
RUN mvn dependency:go-offline -q

COPY src ./src
RUN mvn package -DskipTests -q

# ── Stage 2: Runtime ──────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Quarkus fast-jar layout
COPY --from=build /build/target/quarkus-app/lib/          ./lib/
COPY --from=build /build/target/quarkus-app/*.jar          ./
COPY --from=build /build/target/quarkus-app/app/           ./app/
COPY --from=build /build/target/quarkus-app/quarkus/       ./quarkus/

# Web UI (кошелёк)
COPY src/main/resources/META-INF/resources/web/ ./web/

EXPOSE 47780 47701 47702 47703

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"

ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
CMD ["--port", "47780"]
