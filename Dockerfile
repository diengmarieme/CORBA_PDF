# --- Étape 1 : Compilation globale avec OpenJDK 8 traditionnel (Contient idlj fonctionnel) ---
FROM maven:3.8.6-openjdk-8 AS builder
WORKDIR /build
COPY . .
RUN mvn clean package -DskipTests -q

# --- Étape 2 : Image d'exécution légère ---
FROM eclipse-temurin:8-jre-alpine
WORKDIR /app
RUN apk add --no-cache bash

# Copie des fichiers JAR compilés depuis les dossiers cibles des sous-modules
COPY --from=builder /build/corba-server/target/corba-server.jar /app/corba-server.jar
COPY --from=builder /build/web-middleware/target/web-middleware.jar /app/web-middleware.jar

EXPOSE 8080

# Démarrage coordonné de l'écosystème CORBA et Spring Boot
CMD tnameserv -ORBInitialPort 2809 & \
    sleep 3 && \
    java -Dorg.omg.CORBA.ORBInitialPort=2809 -Dorg.omg.CORBA.ORBInitialHost=127.0.0.1 -jar /app/corba-server.jar & \
    sleep 3 && \
    java -jar /app/web-middleware.jar --server.port=8080
