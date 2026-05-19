# --- ÉTAPE 1 : COMPILATION MULTI-MODULES ---
FROM maven:3.9-eclipse-temurin-8-alpine AS builder
WORKDIR /build

# Copier l'intégralité du projet
COPY . .

# Compiler tous les modules (corba-server et web-middleware)
RUN mvn clean package -DskipTests -q

# --- ÉTAPE 2 : EXÉCUTION DU TOUT-EN-UN ---
FROM eclipse-temurin:8-jre-alpine
WORKDIR /app

# Installer bash pour le script de démarrage
RUN apk add --no-cache bash

# Récupérer le jar du serveur CORBA et du middleware Web
COPY --from=builder /build/corba-server/target/*.jar /app/corba-server.jar
COPY --from=builder /build/web-middleware/target/*.jar /app/web-middleware.jar

# Exposer le port HTTP indispensable pour Render
EXPOSE 8080

# Script de démarrage en série :
# 1. Le service de nommage (tnameserv) sur le port 2809
# 2. Le serveur CORBA lié à tnameserv
# 3. Le middleware Spring Boot sur le port 8080
CMD tnameserv -ORBInitialPort 2809 & \
    sleep 3 && \
    java -Dorg.omg.CORBA.ORBInitialPort=2809 -Dorg.omg.CORBA.ORBInitialHost=127.0.0.1 -jar /app/corba-server.jar & \
    sleep 3 && \
    java -jar /app/web-middleware.jar --server.port=8080
