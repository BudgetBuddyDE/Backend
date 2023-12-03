# Verwende Gradle als Basisimage für den Builder
FROM gradle:8.3.0-jdk17 AS builder

LABEL org.opencontainers.image.source https://github.com/budgetbuddyde/backend

  # Setze das Arbeitsverzeichnis innerhalb des Builders
WORKDIR /app

  # Kopiere die build.gradle-Datei und die Einstellungsdateien des Gradle-Projekts in das Arbeitsverzeichnis
COPY settings.gradle .
COPY build.gradle .

  # Kopiere das gesamte Gradle-Projekt in das Arbeitsverzeichnis
COPY src src

  # Führe den Build mit Gradle im Builder aus
RUN gradle build --no-daemon

  # Verwende das OpenJDK 17-JDK-Slim-Basisimage für den Anwendungslauf
FROM openjdk:17-jdk-slim

  # Setze das Arbeitsverzeichnis innerhalb des Containers
WORKDIR /app

  # Kopiere die gebaute Anwendung aus dem Builder in den Anwendungscontainer
COPY --from=builder /app/build/libs/backend-0.0.1-SNAPSHOT.jar .

EXPOSE 8080

  # Setze den Befehl, um die Anwendung auszuführen
CMD ["java", "-jar", "backend-0.0.1-SNAPSHOT.jar"]