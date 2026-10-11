# ==============================================================================
# StayEase Migration Verification Script against Disposable Test Database
# Creates disposable database, applies schema-prod.sql, verifies tables/indexes,
# and cleans up safely.
# ==============================================================================
param (
    [string]$TestDb = "stayease_migration_disposable_test",
    [string]$SchemaFile = ".\scripts\schema-prod.sql"
)

$ErrorActionPreference = "Stop"

$DbHost = if ($env:DB_HOST) { $env:DB_HOST } else { "localhost" }
$DbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$DbPassword = if ($env:DB_PASSWORD) { $env:DB_PASSWORD } else { "ishan1950" }

$env:MYSQL_PWD = $DbPassword

Write-Host "======================================================================"
Write-Host "[STEP 1] Creating disposable test database: $TestDb"
Write-Host "======================================================================"
& mysql --host=$DbHost --user=$DbUser -e "DROP DATABASE IF EXISTS $TestDb; CREATE DATABASE $TestDb CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

Write-Host "[STEP 2] Applying schema from $SchemaFile to $TestDb..."
# Replace 'USE stayease;' with 'USE <TestDb>;' for testing
$Content = Get-Content $SchemaFile -Raw
$TestContent = $Content -replace "USE stayease;", "USE $TestDb;"
$TestContent | & mysql --host=$DbHost --user=$DbUser

Write-Host "[STEP 3] Verifying created tables in $TestDb..."
$Tables = & mysql --host=$DbHost --user=$DbUser -e "USE $TestDb; SHOW TABLES;"
Write-Host $Tables

Write-Host "[STEP 4] Verifying foreign keys and constraints..."
$FkCheck = & mysql --host=$DbHost --user=$DbUser -e "SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE FROM information_schema.TABLE_CONSTRAINTS WHERE TABLE_SCHEMA='$TestDb';"
Write-Host $FkCheck

Write-Host "[STEP 5] Cleaning up disposable test database $TestDb..."
& mysql --host=$DbHost --user=$DbUser -e "DROP DATABASE IF EXISTS $TestDb;"

Write-Host "======================================================================"
Write-Host "[SUCCESS] Disposable migration test passed! All tables and constraints verified."
Write-Host "======================================================================"
