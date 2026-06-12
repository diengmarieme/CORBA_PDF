# ================================================================
#  Dockerfile.render — PDF Studio (Single Container pour Render)
#
#  Problème Render : les services séparés ne peuvent pas communiquer
#  via CORBA/IIOP (ports 2809/2810 bloqués entre services Render).
#
#  Solution : TOUT dans UN seul conteneur (stratégie du projet ref) :
#    - JacORB Naming Service  → localhost:2809 (interne)
#    - Serveur CORBA Java     → localhost:2810 (interne)
#    - Middleware Spring Boot → localhost:8080 (interne)
#    - Nginx frontend         → port 10000     (exposé)
#
#  Stages :
#    build-server : compile corba-server.jar (Maven + JDK 11)
#    build-web    : compile web-middleware.jar (Maven + JDK 11)
#    runtime      : JRE 11 + Nginx + supervisord
# ================================================================

# ── Stage 1 : Build CORBA Server ────────────────────────────────
FROM maven:3.9-eclipse-temurin-11-alpine AS build-server
WORKDIR /build-server

COPY corba-server/pom.xml .
COPY corba-server/libs ./libs
RUN mvn dependency:go-offline -q 2>/dev/null || true

COPY corba-server/src ./src
RUN mvn package -DskipTests -q
RUN ls -la target/*.jar

# ── Stage 2 : Build Web Middleware ──────────────────────────────
FROM maven:3.9-eclipse-temurin-11-alpine AS build-web
WORKDIR /build-web

COPY web-middleware/libs ./libs
RUN mvn install:install-file \
    -Dfile=libs/glassfish-corba-omgapi-4.2.4.jar \
    -DgroupId=org.glassfish.corba \
    -DartifactId=glassfish-corba-omgapi \
    -Dversion=4.2.4 -Dpackaging=jar -q && \
    mvn install:install-file \
    -Dfile=libs/glassfish-corba-orb-4.2.4.jar \
    -DgroupId=org.glassfish.corba \
    -DartifactId=glassfish-corba-orb \
    -Dversion=4.2.4 -Dpackaging=jar -q

COPY web-middleware/pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

# Injecter les stubs CORBA dans le middleware
COPY corba-server/src/main/java/PDFService ./src/main/java/PDFService
COPY web-middleware/src ./src

RUN mvn package -DskipTests -q
RUN ls -la target/*.jar

# ── Stage 3 : Runtime final ──────────────────────────────────────
FROM eclipse-temurin:11-jre-alpine

LABEL description="PDF Studio - Single Container (Render.com)"

# Nginx + supervisord pour orchestrer plusieurs processus
RUN apk add --no-cache nginx supervisor bash

WORKDIR /app

# JARs compilés
COPY --from=build-server /build-server/target/corba-server.jar  /app/corba-server.jar
COPY --from=build-web    /build-web/target/web-middleware.jar   /app/web-middleware.jar

# Frontend statique
COPY frontend/ /usr/share/nginx/html/

# Script de démarrage principal
COPY render-single/start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Config Nginx : écoute sur le port 10000 (requis par Render free plan)
# et proxifie /api/ vers Spring Boot en local
RUN printf 'server {\n    listen 10000;\n    root /usr/share/nginx/html;\n    index index.html;\n    client_max_body_size 200M;\n\n    location /api/ {\n        proxy_pass http://127.0.0.1:8080/api/;\n        proxy_set_header Host $host;\n        proxy_set_header X-Real-IP $remote_addr;\n        proxy_read_timeout 120s;\n        proxy_connect_timeout 30s;\n    }\n\n    location / {\n        try_files $uri $uri/ /index.html;\n    }\n}\n' > /etc/nginx/http.d/default.conf

EXPOSE 10000

CMD ["/app/start.sh"]# ================================================================
#  Dockerfile.render — PDF Studio (Single Container pour Render)
#
#  Problème Render : les services séparés ne peuvent pas communiquer
#  via CORBA/IIOP (ports 2809/2810 bloqués entre services Render).
#
#  Solution : TOUT dans UN seul conteneur (stratégie du projet ref) :
#    - JacORB Naming Service  → localhost:2809 (interne)
#    - Serveur CORBA Java     → localhost:2810 (interne)
#    - Middleware Spring Boot → localhost:8080 (interne)
#    - Nginx frontend         → port 10000     (exposé)
#
#  Stages :
#    build-server : compile corba-server.jar (Maven + JDK 11)
#    build-web    : compile web-middleware.jar (Maven + JDK 11)
#    runtime      : JRE 11 + Nginx + supervisord
# ================================================================

# ── Stage 1 : Build CORBA Server ────────────────────────────────
FROM maven:3.9-eclipse-temurin-11-alpine AS build-server
WORKDIR /build-server

COPY corba-server/pom.xml .
COPY corba-server/libs ./libs
RUN mvn dependency:go-offline -q 2>/dev/null || true

COPY corba-server/src ./src
RUN mvn package -DskipTests -q
RUN ls -la target/*.jar

# ── Stage 2 : Build Web Middleware ──────────────────────────────
FROM maven:3.9-eclipse-temurin-11-alpine AS build-web
WORKDIR /build-web

COPY web-middleware/libs ./libs
RUN mvn install:install-file \
    -Dfile=libs/glassfish-corba-omgapi-4.2.4.jar \
    -DgroupId=org.glassfish.corba \
    -DartifactId=glassfish-corba-omgapi \
    -Dversion=4.2.4 -Dpackaging=jar -q && \
    mvn install:install-file \
    -Dfile=libs/glassfish-corba-orb-4.2.4.jar \
    -DgroupId=org.glassfish.corba \
    -DartifactId=glassfish-corba-orb \
    -Dversion=4.2.4 -Dpackaging=jar -q

COPY web-middleware/pom.xml .
RUN mvn dependency:go-offline -q 2>/dev/null || true

# Injecter les stubs CORBA dans le middleware
COPY corba-server/src/main/java/PDFService ./src/main/java/PDFService
COPY web-middleware/src ./src

RUN mvn package -DskipTests -q
RUN ls -la target/*.jar

# ── Stage 3 : Runtime final ──────────────────────────────────────
FROM eclipse-temurin:11-jre-alpine

LABEL description="PDF Studio - Single Container (Render.com)"

# Nginx + supervisord pour orchestrer plusieurs processus
RUN apk add --no-cache nginx supervisor bash

WORKDIR /app

# JARs compilés
COPY --from=build-server /build-server/target/corba-server.jar  /app/corba-server.jar
COPY --from=build-web    /build-web/target/web-middleware.jar   /app/web-middleware.jar

# Frontend statique
COPY frontend/ /usr/share/nginx/html/

# Script de démarrage principal
COPY render-single/start.sh /app/start.sh
RUN chmod +x /app/start.sh

# Config Nginx : écoute sur le port 10000 (requis par Render free plan)
# et proxifie /api/ vers Spring Boot en local
RUN printf 'server {\n    listen 10000;\n    root /usr/share/nginx/html;\n    index index.html;\n    client_max_body_size 200M;\n\n    location /api/ {\n        proxy_pass http://127.0.0.1:8080/api/;\n        proxy_set_header Host $host;\n        proxy_set_header X-Real-IP $remote_addr;\n        proxy_read_timeout 120s;\n        proxy_connect_timeout 30s;\n    }\n\n    location / {\n        try_files $uri $uri/ /index.html;\n    }\n}\n' > /etc/nginx/http.d/default.conf

EXPOSE 10000

CMD ["/app/start.sh"]
