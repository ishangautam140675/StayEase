#!/usr/bin/env bash
# ==============================================================================
# StayEase Database Restore Script
# Safely restores a gzipped or plain SQL backup into the designated database.
# Usage: ./scripts/restore-db.sh <backup_file.sql[.gz]>
# ==============================================================================
set -euo pipefail

if [ "$#" -lt 1 ]; then
    echo "Usage: $0 <path_to_backup.sql[.gz]>" >&2
    exit 1
fi

BACKUP_FILE="$1"
DB_HOST="${DB_HOST:-localhost}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-stayease}"
DB_USER="${DB_USER:-root}"

if [ ! -f "${BACKUP_FILE}" ]; then
    echo "ERROR: Backup file '${BACKUP_FILE}' not found." >&2
    exit 1
fi

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_PASSWORD environment variable is not set." >&2
    exit 1
fi

echo "======================================================================"
echo "WARNING: Restoring backup will overwrite tables in '${DB_NAME}'."
echo "Target: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "Source: ${BACKUP_FILE}"
echo "======================================================================"
read -p "Type 'RESTORE' to proceed: " CONFIRMATION

if [ "${CONFIRMATION}" != "RESTORE" ]; then
    echo "Aborted."
    exit 0
fi

echo "[INFO] Restoring database '${DB_NAME}'..."

if [[ "${BACKUP_FILE}" == *.gz ]]; then
    MYSQL_PWD="${DB_PASSWORD}" gunzip -c "${BACKUP_FILE}" | mysql \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${DB_USER}" \
        --default-character-set=utf8mb4 \
        "${DB_NAME}"
else
    MYSQL_PWD="${DB_PASSWORD}" mysql \
        --host="${DB_HOST}" \
        --port="${DB_PORT}" \
        --user="${DB_USER}" \
        --default-character-set=utf8mb4 \
        "${DB_NAME}" < "${BACKUP_FILE}"
fi

echo "[SUCCESS] Database restoration completed successfully."
