#!/usr/bin/env bash
# Idempotent install script for the Radiance Spring Boot backend.
# Installs system toolchains, provisions a local PostgreSQL dev database,
# generates local dev config, and builds the application.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPO_ROOT/Back-End"
JAVA_HOME_17="/usr/lib/jvm/java-17-openjdk-amd64"
export DEBIAN_FRONTEND=noninteractive

echo "==> Installing system dependencies (JDK 17, Maven, PostgreSQL)"
if ! dpkg -s openjdk-17-jdk >/dev/null 2>&1 \
  || ! command -v mvn >/dev/null 2>&1 \
  || ! command -v psql >/dev/null 2>&1; then
  sudo apt-get update -y
  sudo apt-get install -y --no-install-recommends \
    openjdk-17-jdk maven postgresql postgresql-contrib
fi

if [ -x "$JAVA_HOME_17/bin/java" ]; then
  sudo update-alternatives --set java "$JAVA_HOME_17/bin/java" >/dev/null 2>&1 || true
fi
export JAVA_HOME="$JAVA_HOME_17"

echo "==> Starting PostgreSQL and provisioning the dev database"
PG_VERSION="$(ls /etc/postgresql 2>/dev/null | sort -V | tail -1)"
sudo pg_ctlcluster "$PG_VERSION" main start 2>/dev/null || true
for _ in $(seq 1 30); do sudo -u postgres pg_isready -q && break; sleep 1; done

sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='radiance'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE ROLE radiance WITH LOGIN PASSWORD 'radiance';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='radiance'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE DATABASE radiance OWNER radiance;"

echo "==> Generating local dev config (application.yml is gitignored)"
APP_YML="$BACKEND_DIR/src/main/resources/application.yml"
if [ ! -f "$APP_YML" ]; then
  mkdir -p "$(dirname "$APP_YML")"
  cat > "$APP_YML" <<'YML'
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL:jdbc:postgresql://localhost:5432/radiance}
    username: ${SPRING_DATASOURCE_USERNAME:radiance}
    password: ${SPRING_DATASOURCE_PASSWORD:radiance}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: ${SPRING_JPA_HIBERNATE_DDL_AUTO:update}
    open-in-view: false

server:
  port: ${SERVER_PORT:8080}

app:
  cors:
    allowed-origins: ${APP_CORS_ALLOWED_ORIGINS:http://localhost:3000}
YML
fi

echo "==> Building the application"
cd "$BACKEND_DIR"
mvn -B -DskipTests clean package

echo "==> Install complete"
