# DISP-EMS Server

Spring Boot 2.7 / JDK 8 backend module. It owns authenticated business APIs, MySQL metadata, Redis session/event state, and private MinIO objects.

## Configuration

Set these environment variables before starting:

- DISP_DB_URL, DISP_DB_USERNAME, DISP_DB_PASSWORD
- DISP_REDIS_HOST, DISP_REDIS_PORT, DISP_REDIS_PASSWORD
- DISP_MINIO_ENDPOINT, DISP_MINIO_ACCESS_KEY, DISP_MINIO_SECRET_KEY, DISP_MINIO_BUCKET
- DISP_BOOTSTRAP_ADMIN_PASSWORD on the first start only
- DISP_FFPROBE_PATH when video duration extraction is required

Copy src/main/resources/application-example.yml to an ignored local profile only for development. Do not place secrets in Git.

## Start

Run the Flyway migration and application:

    cd server
    mvn spring-boot:run

The server listens on port 8080 by default. The frontend development server proxies /api there. Enable its production transport with:

    VITE_USE_MOCK=false
    VITE_API_BASE_URL=/api/v1

## Implemented API groups

- Opaque Bearer authentication, Redis session cache, role permissions, password changes, user management
- Workstation options and administrator management
- Private MinIO uploads, SHA-256 metadata, short-lived URLs, asynchronous ffprobe video processing
- Video categories, stable video IDs, versioned replacement, playback URLs
- Projection PNG icons and templates
- Redis task-event fan-out to authenticated SSE subscribers

The Flyway migration intentionally uses MySQL 5.7-compatible utf8mb4_bin and service-level validation instead of MySQL 8-only collation and effective CHECK constraints.
