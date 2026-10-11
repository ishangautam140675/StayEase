#!/usr/bin/env bash
# ==============================================================================
# StayEase Migration Verification Script against Disposable Test Database
# ==============================================================================
set -euo pipefail

TEST_DB="${1:-stayease_migration_disposable_test}"
SCHEMA_FILE="${2:-./scripts/schema-prod.sql}"

DB_HOST="${DB_HOST:-localhost}"
DB_USER="${DB_USER:-root}"

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "ERROR: DB_PASSWORD environment variable is not set." >&2
    exit 1
fi

echo "[STEP 1] Creating disposable test database: ${TEST_DB}..."
MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" -u "${DB_USER}" -e "DROP DATABASE IF EXISTS ${TEST_DB}; CREATE DATABASE ${TEST_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

echo "[STEP 2] Applying schema from ${SCHEMA_FILE}..."
sed "s/USE stayease;/USE ${TEST_DB};/g" "${SCHEMA_FILE}" | MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" -u "${DB_USER}"

echo "[STEP 3] Verifying created tables..."
MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" -u "${DB_USER}" -e "USE ${TEST_DB}; SHOW TABLES;"

echo "[STEP 4] Cleaning up disposable test database..."
MYSQL_PWD="${DB_PASSWORD}" mysql -h "${DB_HOST}" -u "${DB_USER}" -e "DROP DATABASE IF EXISTS ${TEST_DB};"

echo "[SUCCESS] Disposable migration test passed!"
