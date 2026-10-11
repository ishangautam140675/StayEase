#!/usr/bin/env bash
# ==============================================================================
# StayEase Automated Database Backup Script
# Performs atomic mysqldump with gzip compression and retention rotation.
# Usage: ./scripts/backup-db.sh [target_directory]
# ==============================================================================
set -euo pipefail

BACKUP_DIR="${1:-./backups}"
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="${BACKUP_DIR}/stayease_backup_${TIMESTAMP}.sql.gz"
RETENTION_DAYS=14

# Database credentials (read from environment variables)
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-stayease}"
DB_USER="${DB_USER:-root}"

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_PASSWORD environment variable is not set." >&2
    exit 1
fi

mkdir -p "${BACKUP_DIR}"

echo "[INFO] Starting backup of database '${DB_NAME}' from ${DB_HOST}:${DB_PORT}..."

# Execute mysqldump with single-transaction (no locking on InnoDB) and gzip
MYSQL_PWD="${DB_PASSWORD}" mysqldump \
    --host="${DB_HOST}" \
    --port="${DB_PORT}" \
    --user="${DB_USER}" \
    --single-transaction \
    --quick \
    --routines \
    --triggers \
    --default-character-set=utf8mb4 \
    "${DB_NAME}" | gzip -9 > "${BACKUP_FILE}"

FILE_SIZE=$(du -h "${BACKUP_FILE}" | cut -f1)
echo "[SUCCESS] Backup completed successfully: ${BACKUP_FILE} (${FILE_SIZE})"

# Enforce backup retention policy
echo "[INFO] Cleaning backups older than ${RETENTION_DAYS} days in ${BACKUP_DIR}..."
find "${BACKUP_DIR}" -name "stayease_backup_*.sql.gz" -type f -mtime +"${RETENTION_DAYS}" -exec rm -f {} +
echo "[SUCCESS] Backup rotation completed."
