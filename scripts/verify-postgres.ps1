param(
    [Parameter(Mandatory = $true)][string]$PostgresBin,
    [switch]$UseTcpSelectorFallback
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$PostgresBin = (Resolve-Path -LiteralPath $PostgresBin).Path
foreach ($tool in @('initdb.exe', 'pg_ctl.exe', 'createdb.exe')) {
    if (-not (Test-Path -LiteralPath (Join-Path $PostgresBin $tool))) { throw "Missing $tool in PostgresBin." }
}
function Get-FreePort {
    $listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, 0)
    $listener.Start()
    try { $listener.LocalEndpoint.Port } finally { $listener.Stop() }
}
$runId = [guid]::NewGuid().ToString('N')
$runDirectory = Join-Path $project "target/postgres-verification/$runId"
[IO.Directory]::CreateDirectory($runDirectory) | Out-Null
$dataDirectory = Join-Path $runDirectory 'data'
$passwordFile = Join-Path $runDirectory 'password.txt'
$databasePassword = [guid]::NewGuid().ToString('N')
$databasePort = Get-FreePort
$apiPort = Get-FreePort
while ($apiPort -eq $databasePort) { $apiPort = Get-FreePort }
$environmentNames = @(
    'TEST_DATABASE_URL', 'TEST_DATABASE_DRIVER', 'TEST_DATABASE_USER', 'TEST_DATABASE_PASSWORD',
    'TEST_FLYWAY_LOCATIONS', 'PGPASSWORD', 'MAVEN_OPTS', 'DATABASE_URL', 'DATABASE_USER',
    'DATABASE_PASSWORD', 'SPRING_PROFILES_ACTIVE', 'LOCAL_DEMO_USERNAME', 'LOCAL_DEMO_PASSWORD',
    'SERVER_ADDRESS', 'SERVER_PORT', 'FINERACT_ENABLED', 'FLYWAY_ENABLED',
    'SPRING_DATASOURCE_URL', 'SPRING_DATASOURCE_USERNAME', 'SPRING_DATASOURCE_PASSWORD'
)
$savedEnvironment = @{}
foreach ($name in $environmentNames) { $savedEnvironment[$name] = [Environment]::GetEnvironmentVariable($name, 'Process') }
$databaseStarted = $false
$apiProcess = $null
Push-Location -LiteralPath $project
try {
    [IO.File]::WriteAllText($passwordFile, $databasePassword, [Text.UTF8Encoding]::new($false))
    & "$PostgresBin/initdb.exe" -D $dataDirectory -U banking_test --encoding=UTF8 --locale=C --auth=scram-sha-256 "--pwfile=$passwordFile"
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL initialization failed.' }
    Remove-Item -LiteralPath $passwordFile
    & "$PostgresBin/pg_ctl.exe" -D $dataDirectory -l (Join-Path $runDirectory 'postgres.log') -o "-h 127.0.0.1 -p $databasePort" -w start
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL startup failed.' }
    $databaseStarted = $true
    $env:PGPASSWORD = $databasePassword
    foreach ($database in @('banking_test', 'banking_demo')) {
        & "$PostgresBin/createdb.exe" -h 127.0.0.1 -p $databasePort -U banking_test $database
        if ($LASTEXITCODE -ne 0) { throw "Could not create $database." }
    }
    $env:TEST_DATABASE_URL = "jdbc:postgresql://127.0.0.1:$databasePort/banking_test"
    $env:TEST_DATABASE_DRIVER = 'org.postgresql.Driver'
    $env:TEST_DATABASE_USER = 'banking_test'
    $env:TEST_DATABASE_PASSWORD = $databasePassword
    $env:TEST_FLYWAY_LOCATIONS = 'classpath:db/migration,classpath:db/postgresql'
    $env:MAVEN_OPTS = '-Xms32m -Xmx320m -Xss512k -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1 -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=32m'
    & mvn -B '-DforkCount=0' verify
    if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL test suite failed.' }
    $reports = @(Get-ChildItem -LiteralPath (Join-Path $project 'target/surefire-reports') -Filter 'TEST-*.xml')
    $tests = 0
    foreach ($report in $reports) {
        [xml]$result = Get-Content -LiteralPath $report.FullName -Raw
        if ([int]$result.testsuite.failures -gt 0 -or [int]$result.testsuite.errors -gt 0 -or [int]$result.testsuite.skipped -gt 0) {
            throw "Incomplete or failing suite: $($report.Name)"
        }
        $tests += [int]$result.testsuite.tests
    }
    if ($tests -eq 0 -or -not (Test-Path -LiteralPath (Join-Path $project 'target/surefire-reports/TEST-com.rova.banking.PostgresIntegrityTest.xml'))) {
        throw 'Expected PostgreSQL integrity test results were not produced.'
    }
    $env:DATABASE_URL = "jdbc:postgresql://127.0.0.1:$databasePort/banking_demo"
    $env:DATABASE_USER = 'banking_test'
    $env:DATABASE_PASSWORD = $databasePassword
    $env:SPRING_DATASOURCE_URL = $env:DATABASE_URL
    $env:SPRING_DATASOURCE_USERNAME = $env:DATABASE_USER
    $env:SPRING_DATASOURCE_PASSWORD = $env:DATABASE_PASSWORD
    $env:SPRING_PROFILES_ACTIVE = 'local'
    $env:LOCAL_DEMO_USERNAME = 'demo'
    $env:LOCAL_DEMO_PASSWORD = [guid]::NewGuid().ToString('N')
    $env:SERVER_ADDRESS = '127.0.0.1'
    $env:SERVER_PORT = "$apiPort"
    $env:FINERACT_ENABLED = 'false'
    $env:FLYWAY_ENABLED = 'true'
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $project 'target') -Filter '*.jar' | Where-Object Name -NotLike '*-plain.jar')
    if ($jars.Count -ne 1) { throw 'Expected one packaged application JAR.' }
    $java = if ($env:JAVA_HOME) { Join-Path $env:JAVA_HOME 'bin/java.exe' } else { (Get-Command java).Source }
    $apiLog = Join-Path $runDirectory 'api.log'
    $apiErrorLog = Join-Path $runDirectory 'api-error.log'
    $javaArguments = @('-Xms32m', '-Xmx256m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-XX:TieredStopAtLevel=1', '-jar', ('"' + $jars[0].FullName + '"'))
    if ($UseTcpSelectorFallback) {
        # A nonexistent Unix socket directory makes this JDK use its TCP loopback fallback.
        # Only this verification process is affected; no machine-wide settings change.
        $socketDirectory = Join-Path $runDirectory 'tcp-selector-only'
        if (Test-Path -LiteralPath $socketDirectory) { throw 'TCP fallback path must not exist.' }
        $javaArguments = @(('"-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"')) + $javaArguments
    }
    $apiProcess = Start-Process -FilePath $java -ArgumentList $javaArguments -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput $apiLog -RedirectStandardError $apiErrorLog
    $baseUrl = "http://127.0.0.1:$apiPort"
    $deadline = [DateTime]::UtcNow.AddSeconds(120)
    $ready = $false
    while ([DateTime]::UtcNow -lt $deadline) {
        if ($apiProcess.HasExited) { throw "API exited. See $apiLog and $apiErrorLog." }
        try {
            $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -TimeoutSec 3
            if ($health.status -eq 'UP') { $ready = $true; break }
        } catch { Start-Sleep -Seconds 2 }
    }
    if (-not $ready) { throw "API did not become healthy. See $apiLog." }
    $summary = & (Join-Path $PSScriptRoot 'demo.ps1') -BaseUrl $baseUrl -Password $env:LOCAL_DEMO_PASSWORD
    $summary | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $runDirectory 'demo-result.json') -Encoding UTF8
    $summary
    Write-Host "PASS: $tests tests, zero failures or skips; HTTP banking demo passed. Reports: $runDirectory"
} finally {
    if ($apiProcess -and -not $apiProcess.HasExited) { Stop-Process -Id $apiProcess.Id -Force }
    if ($databaseStarted) {
        & "$PostgresBin/pg_ctl.exe" -D $dataDirectory -m fast -w stop
        if ($LASTEXITCODE -ne 0) { Write-Warning "PostgreSQL shutdown failed. Data directory: $dataDirectory" }
    }
    if (Test-Path -LiteralPath $passwordFile) { Remove-Item -LiteralPath $passwordFile }
    foreach ($name in $environmentNames) { [Environment]::SetEnvironmentVariable($name, $savedEnvironment[$name], 'Process') }
    Pop-Location
}
