#!/usr/bin/env bash
# Per-boot reconciliation: ensure PostgreSQL is running and the dev
# database/role exist. Safe to run repeatedly.
set -euo pipefail

echo "==> Ensuring PostgreSQL is running"
PG_VERSION="$(ls /etc/postgresql 2>/dev/null | sort -V | tail -1)"
sudo pg_ctlcluster "$PG_VERSION" main start 2>/dev/null || true
for _ in $(seq 1 30); do sudo -u postgres pg_isready -q && break; sleep 1; done

echo "==> Ensuring dev database/role exist"
sudo -u postgres psql -tc "SELECT 1 FROM pg_roles WHERE rolname='radiance'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE ROLE radiance WITH LOGIN PASSWORD 'radiance';"
sudo -u postgres psql -tc "SELECT 1 FROM pg_database WHERE datname='radiance'" | grep -q 1 \
  || sudo -u postgres psql -c "CREATE DATABASE radiance OWNER radiance;"

echo "==> PostgreSQL ready"
