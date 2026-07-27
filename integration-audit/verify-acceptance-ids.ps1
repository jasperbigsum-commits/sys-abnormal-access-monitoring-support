[CmdletBinding()]
param()

$ErrorActionPreference = 'Stop'
$auditRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$source = Join-Path $auditRoot 'src/verify/java/AcceptanceIdVerifier.java'
$classes = Join-Path $auditRoot 'target/acceptance-id-verifier'

New-Item -ItemType Directory -Force -Path $classes | Out-Null

$javac = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/javac.exe' } else { 'javac' }
$java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { 'java' }

& $javac -encoding UTF-8 -source 8 -target 8 -d $classes $source
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& $java -cp $classes AcceptanceIdVerifier $auditRoot
exit $LASTEXITCODE
