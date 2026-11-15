# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system borrowbox \
    && useradd --system --gid borrowbox --home-dir /app --shell /usr/sbin/nologin borrowbox \
    && mkdir -p /app/media \
    && chown -R borrowbox:borrowbox /app
WORKDIR /app
COPY --from=build --chown=borrowbox:borrowbox /workspace/target/*.jar app.jar
USER borrowbox
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
