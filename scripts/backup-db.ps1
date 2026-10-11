# ==============================================================================
# StayEase Automated Database Backup Script (PowerShell)
# Usage: .\scripts\backup-db.ps1 -BackupDir ".\backups"
# ==============================================================================
param (
    [string]$BackupDir = ".\backups",
    [int]$RetentionDays = 14
)

$ErrorActionPreference = "Stop"

$DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "stayease" }
$DbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$DbPassword = $env:DB_PASSWORD

if (-not $DbPassword) {
    Write-Error "DB_PASSWORD environment variable is not set."
    exit 1
}

if (-not (Test-Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$Timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$BackupFile = Join-Path $BackupDir "stayease_backup_$Timestamp.sql"

Write-Host "[INFO] Starting backup of database '$DbName' from ${DbHost}:${DbPort}..."

$env:MYSQL_PWD = $DbPassword
& mysqldump `
    --host=$DbHost `
    --port=$DbPort `
    --user=$DbUser `
    --single-transaction `
    --quick `
    --routines `
    --triggers `
    --default-character-set=utf8mb4 `
    $DbName > $BackupFile

if ($LASTEXITCODE -ne 0) {
    Write-Error "mysqldump failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "[SUCCESS] Backup written to $BackupFile"

# Cleanup old backups
$Cutoff = (Get-Date).AddDays(-$RetentionDays)
Get-ChildItem -Path $BackupDir -Filter "stayease_backup_*.sql" | Where-Object { $_.LastWriteTime -lt $Cutoff } | Remove-Item -Force
Write-Host "[SUCCESS] Old backups cleaned up."
