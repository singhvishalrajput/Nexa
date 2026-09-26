#Requires -Version 7.2
# Runs child-process checks only; never prompts for credentials or connects to Oracle.
$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$setupPath = Join-Path $PSScriptRoot '../setup-db.ps1'
$tokens = $null
$parseErrors = $null
$ast = [System.Management.Automation.Language.Parser]::ParseFile($setupPath, [ref]$tokens, [ref]$parseErrors)
if ($parseErrors.Count) { throw 'Setup launcher has a PowerShell syntax error.' }
$functionAst = $ast.Find({
    param($node)
    $node -is [System.Management.Automation.Language.FunctionDefinitionAst] -and $node.Name -eq 'Invoke-SetupProcess'
}, $true)
if (-not $functionAst) { throw 'Setup process runner was not found.' }
# Load just the real process runner, without executing setup/provisioning.
. ([scriptblock]::Create($functionAst.Extent.Text))
$schemaParameter = $ast.ParamBlock.Parameters | Where-Object { $_.Name.VariablePath.UserPath -eq 'Schema' }
if ($schemaParameter.DefaultValue.SafeGetValue() -ne 'NEXA_BANK_APP') {
    throw 'The default would target the other Nexa checkout.'
}

function New-TestProcess([string]$Command) {
    $info = [Diagnostics.ProcessStartInfo]::new()
    $info.FileName = Join-Path $PSHOME $(if ($IsWindows) { 'pwsh.exe' } else { 'pwsh' })
    foreach ($argument in @('-NoProfile', '-NonInteractive', '-Command', $Command)) { $info.ArgumentList.Add($argument) }
    $info.Environment['NEXA_SETUP_PASSWORD'] = 'synthetic-test-secret'
    $info.Environment['NEXA_SETUP_ADMIN_PASSWORD'] = 'synthetic-test-secret-with-admin-suffix'
    return $info
}

$failed = Invoke-SetupProcess (New-TestProcess @'
[Console]::Out.WriteLine('output-before')
[Console]::Out.Write(('o' * 200000))
[Console]::Error.Write(('e' * 200000))
[Console]::Out.WriteLine('output-after')
[Console]::Out.WriteLine($env:NEXA_SETUP_PASSWORD)
[Console]::Error.WriteLine('ORA-01017: diagnostic remains visible')
[Console]::Error.WriteLine($env:NEXA_SETUP_ADMIN_PASSWORD)
exit 23
'@)
if ($failed.ExitCode -ne 23 -or -not $failed.StandardOutput.Contains('output-after') -or
    -not $failed.StandardError.Contains('ORA-01017: diagnostic remains visible')) {
    throw 'Child failure diagnostics or exit code were lost.'
}
if ($failed.StandardOutput.Contains('synthetic-test-secret') -or
    $failed.StandardError.Contains('synthetic-test-secret') -or
    $failed.StandardError.Contains('-with-admin-suffix') -or
    -not $failed.StandardOutput.Contains('[redacted]') -or
    -not $failed.StandardError.Contains('[redacted]')) {
    throw 'Child output leaked a test password or failed to redact it completely.'
}
$succeeded = Invoke-SetupProcess (New-TestProcess "[Console]::Out.WriteLine('Database ready fixture'); exit 0")
if ($succeeded.ExitCode -ne 0 -or $succeeded.StandardOutput.Trim() -ne 'Database ready fixture' -or $succeeded.StandardError) {
    throw 'Successful setup output was not captured correctly.'
}
$silent = Invoke-SetupProcess (New-TestProcess 'exit 7')
if ($silent.ExitCode -ne 7 -or $silent.StandardOutput -or $silent.StandardError) {
    throw 'Silent child failure was not retained.'
}
Write-Output 'Setup launcher checks passed: safe default schema, success/failure output, concurrent large streams, full secret redaction, and silent failure. No database connection made.'
