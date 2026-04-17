# ── Stage 1 : build Maven ────────────────────────────────
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -q
COPY src ./src
RUN mvn clean package -DskipTests -q

# ── Stage 2 : image runtime légère ───────────────────────
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Jasper a besoin de police DejaVu pour le rendu PDF
RUN apk add --no-cache ttf-dejavu fontconfig

COPY --from=build /app/target/analytics-service-*.jar app.jar

EXPOSE 8086

ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Xmx256m", \
  "-jar", "app.jar"]