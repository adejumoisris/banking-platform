$ErrorActionPreference = 'Stop'
$env:MAVEN_OPTS = '-Xms32m -Xmx320m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m'
Push-Location (Join-Path $PSScriptRoot '..')
try {
    & mvn -B '-DforkCount=0' verify
    if ($LASTEXITCODE -ne 0) { throw 'Verification failed.' }
} finally { Pop-Location }
