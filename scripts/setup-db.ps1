#Requires -Version 7.2
<#
.SYNOPSIS
Creates a dedicated local Oracle schema and runs Nexa's versioned migrations.
.EXAMPLE
./scripts/setup-db.ps1
.EXAMPLE
./scripts/setup-db.ps1 -ExistingSchema
#>
[CmdletBinding()]
param(
    [ValidatePattern('^[A-Za-z0-9.-]+$')][string]$DbHost = 'localhost',
    [ValidateRange(1, 65535)][int]$Port = 1521,
    [ValidatePattern('^[A-Za-z][A-Za-z0-9_.-]*$')][string]$Service = 'FREEPDB1',
    [ValidatePattern('^NEXA_[A-Z0-9_]{1,25}$')][string]$Schema = 'NEXA_APP',
    [ValidatePattern('^[A-Z][A-Z0-9_]{0,29}$')][string]$AdminUser = 'SYSTEM',
    [ValidatePattern('^[A-Z][A-Z0-9_]{0,29}$')][string]$Tablespace = 'USERS',
    [switch]$ExistingSchema,
    [string]$MavenRepository
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path $PSScriptRoot -Parent
$apiRoot = Join-Path $repoRoot 'apps/api'
$wrapper = Join-Path $apiRoot $(if ($IsWindows) { 'mvnw.cmd' } else { 'mvnw' })
$java = if ($env:JAVA_HOME) {
    Join-Path $env:JAVA_HOME $(if ($IsWindows) { 'bin/java.exe' } else { 'bin/java' })
} else { (Get-Command java -ErrorAction Stop).Source }
if (-not (Test-Path -LiteralPath $java)) { throw 'Install JDK 17+ and set JAVA_HOME to its installation directory.' }
$javaVersion = (& $java -version 2>&1 | Out-String)
if ($javaVersion -notmatch 'version "(?:1\.)?(\d+)' -or [int]$Matches[1] -lt 17) {
    throw 'JDK 17 or newer is required.'
}
if ($AdminUser -eq 'SYS') { throw 'Use SYSTEM or a PDB administrator; SYS connections are not supported.' }

Write-Host "Setting up $Schema at ${DbHost}:${Port}/$Service (local development, including demo data)."
Write-Host 'Oracle must already be installed/running and the PDB open. No tables or users will be dropped.'
$target = Join-Path $apiRoot 'target'
New-Item -ItemType Directory -Path $target -Force | Out-Null
$classpathFile = Join-Path $target ("db-setup-classpath-{0}.txt" -f [guid]::NewGuid())
$appPassword = $null
$adminPassword = $null
$process = $null
$start = $null

function Reveal-Password([Security.SecureString]$Secret) {
    $buffer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($Secret)
    try { [Runtime.InteropServices.Marshal]::PtrToStringBSTR($buffer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($buffer) }
}

try {
    # Reuse the JDBC/Flyway versions in the application POM. No SQL*Plus installation required.
    $mavenArgs = @('-B', '-ntp', '-f', (Join-Path $apiRoot 'pom.xml'),
        'org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath',
        '-DincludeScope=runtime', "-Dmdep.outputFile=$classpathFile")
    if ($MavenRepository) { $mavenArgs += "-Dmaven.repo.local=$MavenRepository" }
    if ($IsWindows) { & $wrapper @mavenArgs } else { & sh $wrapper @mavenArgs }
    if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $classpathFile)) {
        throw 'Could not resolve dependencies. Check JAVA_HOME, network access and Maven settings, then retry.'
    }

    if (-not $ExistingSchema) { $adminPassword = Read-Host "Password for $AdminUser in $Service" -AsSecureString }
    $appPassword = Read-Host "Password for $Schema (choose 12+ characters for a NEW schema; use its current password if it exists)" -AsSecureString
    if ($appPassword.Length -eq 0) { throw 'The application password cannot be empty.' }

    $jdbcUrl = "jdbc:oracle:thin:@${DbHost}:${Port}/$Service"
    $start = [Diagnostics.ProcessStartInfo]::new()
    $start.FileName = $java
    $start.UseShellExecute = $false
    $start.CreateNoWindow = $true
    $start.ArgumentList.Add('--class-path')
    $start.ArgumentList.Add((Get-Content -LiteralPath $classpathFile -Raw).Trim())
    $start.ArgumentList.Add((Join-Path $PSScriptRoot 'java/SetupDatabase.java'))
    # Passwords are passed only in the child environment, never command arguments or files.
    $start.Environment['NEXA_SETUP_URL'] = $jdbcUrl
    $start.Environment['NEXA_SETUP_SCHEMA'] = $Schema
    $start.Environment['NEXA_SETUP_TABLESPACE'] = $Tablespace
    $start.Environment['NEXA_SETUP_ADMIN'] = $AdminUser
    $start.Environment['NEXA_SETUP_EXISTING'] = $ExistingSchema.IsPresent.ToString()
    $start.Environment['NEXA_SETUP_RESOURCES'] = Join-Path $apiRoot 'src/main/resources'
    $start.Environment['NEXA_SETUP_PASSWORD'] = Reveal-Password $appPassword
    if ($adminPassword) { $start.Environment['NEXA_SETUP_ADMIN_PASSWORD'] = Reveal-Password $adminPassword }
    $process = [Diagnostics.Process]::Start($start)
    $process.WaitForExit()
    if ($process.ExitCode -ne 0) { throw 'Database setup failed. Address the reported issue before retrying; do not use Flyway repair or drop tables to bypass it.' }

    # Available for the next API command in THIS terminal. Nothing is written to a config file.
    $env:BANKING_DB_URL = $jdbcUrl
    $env:BANKING_DB_USERNAME = $Schema
    $env:BANKING_DB_PASSWORD = Reveal-Password $appPassword
    $env:SPRING_PROFILES_ACTIVE = 'local'
    Write-Host "`nDatabase ready. Connection variables are set for this terminal. Start the API:"
    Write-Host '  cd apps/api'
    Write-Host $(if ($IsWindows) { '  .\mvnw.cmd spring-boot:run' } else { '  sh ./mvnw spring-boot:run' })
    Write-Host 'Local demo login: vishal@example.com / NexaDemo@123'
    Write-Host 'Run this script again with -ExistingSchema to reconnect/migrate in a new terminal.'
}
finally {
    if ($start) {
        $start.Environment.Remove('NEXA_SETUP_PASSWORD') | Out-Null
        $start.Environment.Remove('NEXA_SETUP_ADMIN_PASSWORD') | Out-Null
    }
    if ($process) { $process.Dispose() }
    if ($appPassword) { $appPassword.Dispose() }
    if ($adminPassword) { $adminPassword.Dispose() }
    if (Test-Path -LiteralPath $classpathFile) { Remove-Item -LiteralPath $classpathFile }
}
