# ==============================================================================
# StayEase Database Restore Script (PowerShell)
# Usage: .\scripts\restore-db.ps1 -BackupFile ".\backups\stayease_backup_20261011.sql"
# ==============================================================================
param (
    [Parameter(Mandatory=$true)]
    [string]$BackupFile
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path $BackupFile)) {
    Write-Error "Backup file not found: $BackupFile"
    exit 1
}

$DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DbPort = if ($env:DB_PORT) { $env:DB_PORT } else { "3306" }
$DbName = if ($env:DB_NAME) { $env:DB_NAME } else { "stayease" }
$DbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$DbPassword = $env:DB_PASSWORD

if (-not $DbPassword) {
    Write-Error "DB_PASSWORD environment variable is not set."
    exit 1
}

Write-Host "======================================================================"
Write-Host "WARNING: Restoring backup will overwrite tables in '$DbName'."
Write-Host "Target: ${DbHost}:${DbPort}/$DbName"
Write-Host "Source: $BackupFile"
Write-Host "======================================================================"

$Confirm = Read-Host "Type 'RESTORE' to proceed"
if ($Confirm -ne "RESTORE") {
    Write-Host "Restore cancelled."
    exit 0
}

Write-Host "[INFO] Restoring database '$DbName'..."
$env:MYSQL_PWD = $DbPassword

Get-Content $BackupFile | & mysql --host=$DbHost --port=$DbPort --user=$DbUser --default-character-set=utf8mb4 $DbName

if ($LASTEXITCODE -ne 0) {
    Write-Error "mysql restore failed with exit code $LASTEXITCODE"
    exit $LASTEXITCODE
}

Write-Host "[SUCCESS] Database restoration complete."
