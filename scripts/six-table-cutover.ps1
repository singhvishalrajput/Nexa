#Requires -Version 7.2
[CmdletBinding()]
param(
 [Parameter(Mandatory)][ValidateSet('archive','migrate','verify','schema','inventory','retire')][string]$Stage,
 [string]$Path,
 [string]$MavenRepository
)
$ErrorActionPreference='Stop'
$repo=Split-Path $PSScriptRoot -Parent
$api=Join-Path $repo 'apps/api'
foreach($key in 'BANKING_DB_URL','BANKING_DB_USERNAME','BANKING_DB_PASSWORD','BANKING_DB_SCHEMA') {
 if(-not [Environment]::GetEnvironmentVariable($key)){throw "Set $key in this terminal before running the cutover."}
}
if($env:BANKING_DB_SCHEMA -notmatch '^NEXA_[A-Z0-9_]+$'){throw 'Use the existing NEXA_ application schema.'}
if($Stage -in 'archive','schema','retire' -and -not $Path){throw 'This stage requires -Path.'}
$cp=Join-Path $api 'target/six-table-classpath.txt'
$argsMaven=@('-q','-f',(Join-Path $api 'pom.xml'),'-DskipTests','compile','org.apache.maven.plugins:maven-dependency-plugin:3.8.1:build-classpath','-DincludeScope=runtime',"-Dmdep.outputFile=$cp")
if($MavenRepository){$argsMaven+="-Dmaven.repo.local=$MavenRepository"}
& (Join-Path $api 'mvnw.cmd') @argsMaven
if($LASTEXITCODE -ne 0){throw 'Compilation or dependency resolution failed.'}
$classPath=(Join-Path $api 'target/classes')+';'+(Get-Content -LiteralPath $cp -Raw).Trim()
$java=if($env:JAVA_HOME){Join-Path $env:JAVA_HOME 'bin/java.exe'}else{'java'}
$argsJava=@('--class-path',$classPath,(Join-Path $PSScriptRoot 'java/SixTableCutover.java'),$Stage)
if($Path){$argsJava+=$Path}
& $java @argsJava
if($LASTEXITCODE -ne 0){throw 'Cutover stage failed. Inspect the error; do not repair or drop data to bypass verification.'}
