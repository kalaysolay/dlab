#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${DAMULAB_APP_DIR:-/opt/damulab}"
BACKUP_DIR="${DAMULAB_BACKUP_DIR:-/var/backups/damulab}"
RETENTION_DAYS="${DAMULAB_BACKUP_RETENTION_DAYS:-14}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$BACKUP_DIR"
cd "$APP_DIR"

docker compose --env-file .env.prod -f docker-compose.prod.yml exec -T postgres \
  sh -c 'pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom' \
  > "$BACKUP_DIR/database-$STAMP.dump"

docker run --rm \
  -v damulab_lecture-attachments:/source:ro \
  -v "$BACKUP_DIR":/backup \
  alpine:3.20 \
  tar -czf "/backup/lecture-attachments-$STAMP.tar.gz" -C /source .

find "$BACKUP_DIR" -type f -mtime "+$RETENTION_DAYS" -delete

echo "Backup created in $BACKUP_DIR ($STAMP)"
