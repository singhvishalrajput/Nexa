#requires -Version 7.2
<#
.SYNOPSIS
Creates a private local configuration once without replacing existing keys.
#>
[CmdletBinding()]
param()
$ErrorActionPreference = 'Stop'
$configPath = Join-Path $PSScriptRoot '../apps/api/.env'
if (Test-Path -LiteralPath $configPath) {
    Write-Host 'apps/api/.env already exists. Its keys were preserved. Check the example for any missing settings.'
    return
}
function New-PrivateKey {
    $bytes = [byte[]]::new(32)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes)
}
$identityKey = New-PrivateKey
$jwtKey = New-PrivateKey
$content = @"
# Private local configuration. Do not commit or share credentials.
# Preserve the identity key with the database: changing it makes saved identifiers unreadable.
NEXA_ONBOARDING_ENABLED=true
NEXA_ONBOARDING_IDENTITY_KEY=$identityKey
NEXA_ONBOARDING_IDENTITY_KEY_ID=local-v1
NEXA_JWT_SECRET=$jwtKey
# Add NEXA_ADMIN_EMAIL and NEXA_ADMIN_PASSWORD to bootstrap your own administrator.
# Database settings can come from setup-db.ps1 in this terminal or BANKING_DB_* properties here.
"@
# CreateNew also protects a concurrent invocation from overwriting a key.
$stream = [IO.File]::Open($configPath, [IO.FileMode]::CreateNew, [IO.FileAccess]::Write, [IO.FileShare]::None)
try {
    $writer = [IO.StreamWriter]::new($stream, [Text.UTF8Encoding]::new($false))
    try { $writer.Write($content) } finally { $writer.Dispose() }
} finally { $stream.Dispose() }
Write-Host 'Created ignored apps/api/.env with private identity and JWT keys. No secret was printed.'
Write-Host 'Start the API from apps/api. Back up this file privately alongside your database.'
