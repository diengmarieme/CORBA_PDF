# PDF Studio — Architecture N-Tier CORBA

> Projet académique de manipulation de PDF basé sur une architecture N-Tier  
> **Stack :** Java 11 · JacORB · Apache PDFBox · Spring Boot · Docker · HTML5/JS Vanilla

---

## Table des matières

1. [Architecture](#architecture)
2. [Prérequis](#prérequis)
3. [Structure du projet](#structure-du-projet)
4. [Lancement local (WSL / Linux)](#lancement-local)
5. [Endpoints REST](#endpoints-rest)
6. [Déploiement sur Render](#déploiement-sur-render)
7. [Variables d'environnement](#variables-denvironnement)
8. [Dépannage](#dépannage)

---

## Architecture

```
┌─────────────────┐   HTTP/REST    ┌──────────────────────┐   CORBA/IIOP   ┌──────────────────────┐
│  Tier 3         │ ─────────────► │  Tier 2              │ ──────────────► │  Tier 1              │
│  Frontend       │                │  Spring Boot         │                │  Serveur CORBA       │
│  HTML5/CSS3/JS  │ ◄───────────── │  PdfController       │ ◄────────────── │  PDFServiceImpl      │
│  (Nginx :80)    │   byte[] / JSON│  CORBAClient         │   byte[]        │  Apache PDFBox       │
└─────────────────┘                └──────────────────────┘                └──────────────────────┘
                                              │                                        │
                                              └──────────────┬─────────────────────────┘
                                                             ▼
                                                   ┌──────────────────┐
                                                   │  Naming Service  │
                                                   │  JacORB (:2809)  │
                                                   └──────────────────┘
```

**Flux d'une requête :**
1. L'utilisateur dépose un PDF dans le navigateur
2. Le frontend envoie une requête `POST multipart/form-data` au middleware Spring Boot
3. Le middleware convertit `MultipartFile → byte[]` et appelle le stub CORBA
4. Le serveur CORBA traite le PDF avec PDFBox et renvoie `byte[]`
5. Le middleware retourne le fichier traité en `ResponseEntity<Resource>`
6. Le frontend télécharge automatiquement le fichier résultat

---

## Prérequis

| Outil        | Version minimum | Vérification          |
|--------------|-----------------|-----------------------|
| WSL 2        | Ubuntu 22.04    | `wsl --version`       |
| Docker       | 24.x            | `docker --version`    |
| Docker Compose | 2.x           | `docker compose version` |
| Java (optionnel) | 11+        | `java -version`       |
| Maven (optionnel) | 3.9+      | `mvn --version`       |

> Java et Maven ne sont nécessaires que pour un build hors Docker.

---

## Structure du projet

```
pdf-corba-project/
│
├── service.idl                          # Contrat CORBA (interfaces + types + exceptions)
│
├── corba-server/                        # Tier 1 — Serveur CORBA
│   ├── pom.xml                          # Dépendances Maven (JacORB + PDFBox)
│   ├── jacorb.properties                # Config JacORB (host, port, buffer)
│   └── src/main/java/com/pdfservice/server/
│       ├── ServerMain.java              # Initialise l'ORB, enregistre dans Naming Service
│       └── PDFServiceImpl.java          # Implémentation des 12 opérations PDF
│
├── web-middleware/                      # Tier 2 — Spring Boot
│   ├── pom.xml                          # Dépendances Maven (Spring Boot + JacORB)
│   └── src/main/
│       ├── java/com/pdfservice/web/
│       │   ├── WebApplication.java      # Point d'entrée Spring Boot
│       │   ├── CORBAClient.java         # Init ORB + résolution stub avec retry
│       │   └── PdfController.java       # 13 endpoints REST /api/pdf/*
│       └── resources/
│           └── application.properties   # Config Spring Boot + CORBA
│
├── frontend/
│   └── index.html                       # Interface complète (dark mode, drag & drop, PDF.js)
│
├── docker/
│   ├── Dockerfile.naming                # Conteneur Naming Service JacORB
│   ├── Dockerfile.server                # Conteneur Serveur CORBA (multi-stage)
│   ├── Dockerfile.web                   # Conteneur Spring Boot (multi-stage)
│   └── nginx.conf                       # Reverse proxy Nginx frontend → API
│
├── docker-compose.yml                   # Orchestration des 4 conteneurs
├── .env                                 # Variables d'environnement (ports)
└── README.md                            # Ce fichier
```

---

## Lancement local

### 1. Cloner et se placer dans le projet

```bash
cd ~/projects
git clone <votre-repo> pdf-corba-project
cd pdf-corba-project
```

### 2. Construire et démarrer tous les conteneurs

```bash
# Construction des images (première fois ~5-10 min)
docker compose build

# Démarrage en arrière-plan
docker compose up -d

# Suivi des logs en temps réel
docker compose logs -f
```

### 3. Vérifier que tout est démarré

```bash
# État des conteneurs
docker compose ps

# Healthcheck du serveur CORBA
curl http://localhost:8080/api/pdf/ping
# Réponse attendue : {"status":"OK","message":"Service CORBA opérationnel"}
```

### 4. Ouvrir l'interface

Ouvrez votre navigateur sur : **http://localhost**

---

### Commandes utiles

```bash
# Arrêter tous les conteneurs
docker compose down

# Arrêter et supprimer les volumes
docker compose down -v

# Reconstruire un seul service (ex: après modif du code)
docker compose build server
docker compose up -d --no-deps server

# Logs d'un service spécifique
docker compose logs -f web

# Accéder au shell d'un conteneur
docker compose exec server sh
docker compose exec web sh

# Voir l'utilisation des ressources
docker stats
```

---

### Build manuel sans Docker (pour développement)

```bash
# 1. Compiler le serveur CORBA
cd corba-server
mvn package -DskipTests
java -jar target/corba-server.jar

# 2. Compiler le middleware (dans un autre terminal)
cd web-middleware
mvn package -DskipTests
java -jar target/*.jar

# 3. Ouvrir frontend/index.html dans un navigateur
```

> Assurez-vous qu'un Naming Service JacORB tourne sur le port 2809.  
> Vous pouvez le démarrer avec : `docker compose up naming`

---

## Endpoints REST

Tous les endpoints acceptent `POST multipart/form-data` sauf `/ping`.

| Méthode | Endpoint                  | Paramètres clés                              | Réponse         |
|---------|---------------------------|----------------------------------------------|-----------------|
| GET     | `/api/pdf/ping`           | —                                            | JSON status     |
| POST    | `/api/pdf/merge`          | `files[]` (2+ PDFs)                          | PDF fusionné    |
| POST    | `/api/pdf/split`          | `file`, `pagesPerPart`                       | ZIP ou PDF      |
| POST    | `/api/pdf/extract-pages`  | `file`, `pages` (ex: `1,3,5-8`)             | PDF             |
| POST    | `/api/pdf/delete-pages`   | `file`, `pages`                              | PDF             |
| POST    | `/api/pdf/encrypt`        | `file`, `userPassword`, `ownerPassword`      | PDF chiffré     |
| POST    | `/api/pdf/to-images`      | `file`, `dpi` (72-300), `password`           | PNG ou ZIP      |
| POST    | `/api/pdf/extract-text`   | `file`, `password`                           | JSON texte      |
| POST    | `/api/pdf/create`         | `title`, `content`, `author`                 | PDF             |
| POST    | `/api/pdf/compress`       | `file`, `dpi`, `compressImages`, `removeMetadata` | PDF        |
| POST    | `/api/pdf/watermark`      | `file`, `text`, `fontSize`, `opacity`, `rotation`, `colorR/G/B`, `pages` | PDF |
| POST    | `/api/pdf/rotate`         | `file`, `degrees` (90/180/270), `pages`      | PDF             |
| POST    | `/api/pdf/extract-images` | `file`, `password`                           | PNG ou ZIP      |
| POST    | `/api/pdf/metadata`       | `file`, `password`                           | JSON métadonnées|

### Exemple avec curl

```bash
# Fusionner deux PDFs
curl -X POST http://localhost:8080/api/pdf/merge \
  -F "files=@doc1.pdf" \
  -F "files=@doc2.pdf" \
  -o merged.pdf

# Extraire des pages 1, 3 et 5 à 8
curl -X POST http://localhost:8080/api/pdf/extract-pages \
  -F "file=@document.pdf" \
  -F "pages=1,3,5-8" \
  -o extracted.pdf

# Protéger un PDF
curl -X POST http://localhost:8080/api/pdf/encrypt \
  -F "file=@document.pdf" \
  -F "userPassword=monMotDePasse" \
  -o encrypted.pdf

# Ajouter un filigrane
curl -X POST http://localhost:8080/api/pdf/watermark \
  -F "file=@document.pdf" \
  -F "text=CONFIDENTIEL" \
  -F "opacity=0.3" \
  -F "rotation=45" \
  -o watermarked.pdf

# Extraire le texte
curl -X POST http://localhost:8080/api/pdf/extract-text \
  -F "file=@document.pdf"
```

---

## Déploiement sur Render

### Prérequis Render

- Compte sur [render.com](https://render.com)
- Docker Hub ou GitHub Container Registry pour héberger les images

### Étapes

#### 1. Builder et pousser les images

```bash
# Login Docker Hub
docker login

# Tag et push de chaque image
docker build -f docker/Dockerfile.naming -t <votre-user>/pdf-naming:latest .
docker push <votre-user>/pdf-naming:latest

docker build -f docker/Dockerfile.server -t <votre-user>/pdf-server:latest .
docker push <votre-user>/pdf-server:latest

docker build -f docker/Dockerfile.web -t <votre-user>/pdf-web:latest .
docker push <votre-user>/pdf-web:latest

docker build -f docker/Dockerfile.web \
  --build-arg FRONTEND=true \
  -t <votre-user>/pdf-frontend:latest .
docker push <votre-user>/pdf-frontend:latest
```

#### 2. Créer les services sur Render

Sur Render, créez **4 Web Services** (ou utilisez un Blueprint) :

| Service        | Image                              | Port |
|----------------|------------------------------------|------|
| pdf-naming     | `<user>/pdf-naming:latest`         | 2809 |
| pdf-server     | `<user>/pdf-server:latest`         | 2810 |
| pdf-web        | `<user>/pdf-web:latest`            | 8080 |
| pdf-frontend   | `nginx:alpine` + volume frontend   | 80   |

#### 3. Variables d'environnement sur Render

Pour `pdf-server` et `pdf-web`, définissez :

```
NAMING_HOST = <hostname-interne-render-naming>
NAMING_PORT = 2809
SERVER_HOST = <hostname-interne-render-server>
SERVER_PORT = 2810
```

> Sur Render, les services du même environnement se découvrent via leur nom de service interne.

#### 4. Utilisation du docker-compose.yml sur Render

Render supporte nativement `docker-compose.yml` via son Blueprint feature :

```bash
# Dans votre repo GitHub, le docker-compose.yml à la racine suffit.
# Sur Render : New > Blueprint > pointez votre repo.
```

---

## Variables d'environnement

| Variable              | Défaut    | Description                              |
|-----------------------|-----------|------------------------------------------|
| `NAMING_HOST`         | `naming`  | Hostname du Naming Service               |
| `NAMING_PORT`         | `2809`    | Port du Naming Service                   |
| `SERVER_HOST`         | `server`  | Hostname du serveur CORBA                |
| `SERVER_PORT`         | `2810`    | Port du serveur CORBA                    |
| `WEB_PORT`            | `8080`    | Port HTTP Spring Boot                    |
| `CORBA_RETRY_MAX`     | `20`      | Nombre max de tentatives de connexion    |
| `CORBA_RETRY_DELAY_MS`| `3000`    | Délai entre tentatives (ms)              |
| `NAMING_PORT_EXPOSED` | `2809`    | Port exposé sur l'hôte (compose)         |
| `SERVER_PORT_EXPOSED` | `2810`    | Port exposé sur l'hôte (compose)         |
| `WEB_PORT_EXPOSED`    | `8080`    | Port exposé sur l'hôte (compose)         |
| `FRONTEND_PORT_EXPOSED`| `80`     | Port exposé sur l'hôte (compose)         |

---

## Dépannage

### Le serveur CORBA ne démarre pas

```bash
# Vérifier les logs
docker compose logs server

# Vérifier que le naming est bien en ligne
docker compose ps naming
curl -v telnet://localhost:2809
```

### `CORBA connection refused` dans le middleware

```bash
# Vérifier que le servant est enregistré dans le naming service
docker compose logs server | grep "enregistré"

# Redémarrer le middleware après que le server soit healthy
docker compose restart web
```

### Upload de PDF échoue (413 Request Entity Too Large)

Vérifiez que `client_max_body_size 50M` est bien dans `nginx.conf` et que `spring.servlet.multipart.max-file-size=50MB` est dans `application.properties`.

### Problème de résolution DNS entre conteneurs

Sous WSL, vérifiez que Docker Desktop est bien configuré avec WSL 2 backend. Les noms de service (`naming`, `server`, `web`) sont résolus automatiquement via le réseau `corba-net`.

```bash
# Test de résolution DNS depuis le conteneur web
docker compose exec web ping naming
docker compose exec web ping server
```

---

## Auteur

Projet académique — Architecture N-Tier CORBA avec JacORB et PDFBox.
