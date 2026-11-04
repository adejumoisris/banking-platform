param(
    [string]$JavaHome = $env:JAVA_HOME,
    [switch]$SkipBuild,
    [switch]$SkipDemos
)
$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$envFile = Join-Path $project '.env.fineract-sandbox'
$stateDirectory = Join-Path $project '.sandbox/fineract'
[IO.Directory]::CreateDirectory($stateDirectory) | Out-Null
function New-SandboxPassword {
    do { $value = 'Aa9!' + [guid]::NewGuid().ToString('N') } while ($value -cmatch '(.)\1')
    return $value
}
$config = [ordered]@{}
if (Test-Path -LiteralPath $envFile) {
    foreach ($line in Get-Content -LiteralPath $envFile) {
        if ($line -match '^([A-Z][A-Z0-9_]*)=(.*)$') { $config[$matches[1]] = $matches[2] }
    }
} else {
    foreach ($name in @('FINERACT_DB_PASSWORD','BANKING_DB_PASSWORD','FINERACT_MASTER_PASSWORD','FINERACT_ADMIN_PASSWORD','FINERACT_PASSWORD','LOCAL_DEMO_PASSWORD')) {
        $config[$name] = New-SandboxPassword
    }
    $config.FINERACT_USERNAME = 'rova-integration'
    $config.FINERACT_URL = 'http://127.0.0.1:8443/fineract-provider/api/v1'
    $config.FINERACT_TENANT = 'default'
    $config.FINERACT_OFFICE_ID = '1'
    $config.FINERACT_NGN_PRODUCT_ID = '0'
    $config.FINERACT_ENABLED = 'true'
    $config.FINERACT_WORKER_ENABLED = 'false'
}
function Save-Configuration {
    $temporary = "$envFile.tmp"
    $lines = @($config.GetEnumerator() | ForEach-Object {
        if ($_.Value -match '[\r\n]') { throw 'Sandbox configuration cannot contain newlines.' }
        "$($_.Key)=$($_.Value)"
    })
    [IO.File]::WriteAllLines($temporary, $lines, [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporary -Destination $envFile -Force
}
Save-Configuration
if (-not $JavaHome -or -not (Test-Path -LiteralPath (Join-Path $JavaHome 'bin/java.exe'))) {
    throw 'Pass -JavaHome pointing to your Java 17+ JDK.'
}
$java = Join-Path $JavaHome 'bin/java.exe'
function Wait-Healthy([string]$url, [int]$seconds) {
    $deadline = [DateTime]::UtcNow.AddSeconds($seconds)
    while ([DateTime]::UtcNow -lt $deadline) {
        try {
            $health = Invoke-RestMethod -Uri $url -TimeoutSec 5
            if ($health.status -eq 'UP') { return }
        } catch { }
        Start-Sleep -Seconds 3
    }
    throw "Service did not become healthy: $url. Inspect sandbox logs before restarting."
}
function Get-AuthHeaders([string]$username, [string]$password) {
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${username}:$password"))
    @{ Authorization = "Basic $encoded"; 'Fineract-Platform-TenantId' = $config.FINERACT_TENANT }
}
function Invoke-Fineract([string]$method, [string]$path, $body, $headers) {
    $parameters = @{ Uri = $config.FINERACT_URL.TrimEnd('/') + $path; Method = $method; Headers = $headers; ContentType = 'application/json'; TimeoutSec = 60 }
    if ($null -ne $body) { $parameters.Body = ConvertTo-Json -InputObject $body -Depth 10 }
    try { Invoke-RestMethod @parameters }
    catch {
        $status = 0
        if ($_.Exception.Response) { $status = [int]$_.Exception.Response.StatusCode }
        $codes = @()
        try {
            $details = $_.ErrorDetails.Message | ConvertFrom-Json
            $codes = @($details.errors | ForEach-Object { ($_.parameterName + ':' + $_.userMessageGlobalisationCode) -replace '[^A-Za-z0-9_.:-]', '' })
        } catch { }
        $failure = [InvalidOperationException]::new("Fineract $method $path failed (HTTP $status; $($codes -join ', ')). Provider response values were omitted.")
        $failure.Data['StatusCode'] = $status
        throw $failure
    }
}
function Stop-OwnedApi {
    $pidFile = Join-Path $stateDirectory 'api-process.json'
    if (-not (Test-Path -LiteralPath $pidFile)) { return }
    $metadata = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $metadata.processId -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$metadata.startTimeTicks) {
        $command = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
        if ($command.CommandLine -notlike "*$($metadata.jarPath)*") { throw 'Refusing to stop an unrecognized API process.' }
        Stop-Process -Id $process.Id -Force
        $process.WaitForExit(10000) | Out-Null
    }
    Remove-Item -LiteralPath $pidFile
}
Push-Location -LiteralPath $project
$environmentBackup = @{}
try {
    Write-Host 'Starting isolated Fineract and PostgreSQL containers...'
    & docker compose --env-file $envFile -f compose.fineract-sandbox.yaml up -d
    if ($LASTEXITCODE -ne 0) { throw 'Sandbox containers failed to start.' }
    Wait-Healthy 'http://127.0.0.1:8443/fineract-provider/actuator/health' 900
    $adminHeaders = Get-AuthHeaders 'mifos' $config.FINERACT_ADMIN_PASSWORD
    $usingSeedPassword = $false
    try { $offices = Invoke-Fineract 'Get' '/offices' $null $adminHeaders }
    catch {
        if ($config.FINERACT_ADMIN_INITIALIZED -eq 'true' -or [int]$_.Exception.Data['StatusCode'] -ne 401) { throw }
        $adminHeaders = Get-AuthHeaders 'mifos' 'password'
        $offices = Invoke-Fineract 'Get' '/offices' $null $adminHeaders
        $usingSeedPassword = $true
    }
    $office = @($offices | Where-Object { $null -eq $_.parentId }) | Select-Object -First 1
    if (-not $office) { $office = @($offices) | Sort-Object id | Select-Object -First 1 }
    if (-not $office -or [long]$office.id -lt 1) { throw 'No sandbox office is available.' }
    $config.FINERACT_OFFICE_ID = "$($office.id)"
    $currencies = Invoke-Fineract 'Get' '/currencies' $null $adminHeaders
    $codes = @($currencies.selectedCurrencyOptions | ForEach-Object code)
    if ('NGN' -notin $codes) {
        $null = Invoke-Fineract 'Put' '/currencies' @{ currencies = @($codes + 'NGN' | Select-Object -Unique) } $adminHeaders
    }
    $products = Invoke-Fineract 'Get' '/savingsproducts' $null $adminHeaders
    $product = @($products | Where-Object name -eq 'Rova NGN Sandbox') | Select-Object -First 1
    if (-not $product) {
        $created = Invoke-Fineract 'Post' '/savingsproducts' @{
            name = 'Rova NGN Sandbox'; shortName = 'RVNG'; description = 'Synthetic-data zero-interest provisioning sandbox'
            currencyCode = 'NGN'; digitsAfterDecimal = 2; inMultiplesOf = 1; locale = 'en'
            nominalAnnualInterestRate = 0; interestCompoundingPeriodType = 1; interestPostingPeriodType = 4
            interestCalculationType = 1; interestCalculationDaysInYearType = 365; accountingRule = 1
            minRequiredOpeningBalance = 0; withdrawalFeeForTransfers = $false; allowOverdraft = $false
        } $adminHeaders
        $product = Invoke-Fineract 'Get' "/savingsproducts/$($created.resourceId)" $null $adminHeaders
    }
    if ($product.currency.code -ne 'NGN') { throw 'Sandbox savings product is not NGN.' }
    $config.FINERACT_NGN_PRODUCT_ID = "$($product.id)"
    Save-Configuration
    $roles = Invoke-Fineract 'Get' '/roles' $null $adminHeaders
    $role = @($roles | Where-Object name -eq 'Rova sandbox provisioning') | Select-Object -First 1
    if (-not $role) {
        $created = Invoke-Fineract 'Post' '/roles' @{ name = 'Rova sandbox provisioning'; description = 'Client and savings application provisioning only' } $adminHeaders
        $role = @{ id = $created.resourceId }
    }
    $requiredPermissions = @('READ_CLIENT','CREATE_CLIENT','ACTIVATE_CLIENT','READ_SAVINGSACCOUNT','CREATE_SAVINGSACCOUNT','READ_SAVINGSPRODUCT')
    $availablePermissions = Invoke-Fineract 'Get' '/permissions' $null $adminHeaders
    $permissions = @{}
    foreach ($permission in $requiredPermissions) {
        if ($permission -notin @($availablePermissions | ForEach-Object code)) { throw "Provider does not expose required permission: $permission" }
        $permissions[$permission] = $true
    }
    $null = Invoke-Fineract 'Put' "/roles/$($role.id)/permissions" @{ permissions = $permissions } $adminHeaders
    $users = Invoke-Fineract 'Get' '/users' $null $adminHeaders
    $integrationUser = @($users | Where-Object username -eq $config.FINERACT_USERNAME) | Select-Object -First 1
    if (-not $integrationUser) {
        $null = Invoke-Fineract 'Post' '/users' @{
            username = $config.FINERACT_USERNAME; firstname = 'Rova'; lastname = 'Integration'
            email = 'rova-sandbox@example.com'; officeId = [long]$config.FINERACT_OFFICE_ID
            password = $config.FINERACT_PASSWORD; repeatPassword = $config.FINERACT_PASSWORD
            roles = @([long]$role.id); sendPasswordToEmail = $false
        } $adminHeaders
    }
    if ($usingSeedPassword) {
        $admin = @($users | Where-Object username -eq 'mifos') | Select-Object -First 1
        if (-not $admin) { throw 'Seed administrator could not be identified.' }
        $null = Invoke-Fineract 'Put' "/users/$($admin.id)" @{ password = $config.FINERACT_ADMIN_PASSWORD; repeatPassword = $config.FINERACT_ADMIN_PASSWORD } $adminHeaders
    }
    $config.FINERACT_ADMIN_INITIALIZED = 'true'
    Save-Configuration
    $integrationHeaders = Get-AuthHeaders $config.FINERACT_USERNAME $config.FINERACT_PASSWORD
    $checkedProduct = Invoke-Fineract 'Get' "/savingsproducts/$($config.FINERACT_NGN_PRODUCT_ID)" $null $integrationHeaders
    if ($checkedProduct.currency.code -ne 'NGN') { throw 'Integration user could not verify NGN product.' }
    Write-Host 'Fineract office, NGN product and restricted integration user are ready.'
    $environment = @{
        JAVA_HOME = $JavaHome
        Path = "$JavaHome\bin;$env:Path"
        DATABASE_URL = 'jdbc:postgresql://127.0.0.1:55432/banking_sandbox'
        DATABASE_USER = 'banking_sandbox'
        DATABASE_PASSWORD = $config.BANKING_DB_PASSWORD
        SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:55432/banking_sandbox'
        SPRING_DATASOURCE_USERNAME = 'banking_sandbox'
        SPRING_DATASOURCE_PASSWORD = $config.BANKING_DB_PASSWORD
        SPRING_PROFILES_ACTIVE = 'local'
        LOCAL_DEMO_USERNAME = 'demo'
        LOCAL_DEMO_PASSWORD = $config.LOCAL_DEMO_PASSWORD
        SERVER_ADDRESS = '127.0.0.1'
        SERVER_PORT = '8080'
        FINERACT_ENABLED = 'true'
        FINERACT_URL = $config.FINERACT_URL
        FINERACT_TENANT = $config.FINERACT_TENANT
        FINERACT_USERNAME = $config.FINERACT_USERNAME
        FINERACT_PASSWORD = $config.FINERACT_PASSWORD
        FINERACT_OFFICE_ID = $config.FINERACT_OFFICE_ID
        FINERACT_NGN_PRODUCT_ID = $config.FINERACT_NGN_PRODUCT_ID
        FINERACT_WORKER_ENABLED = $config.FINERACT_WORKER_ENABLED
        FLYWAY_ENABLED = 'true'
        MAVEN_OPTS = '-Xms32m -Xmx320m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:TieredStopAtLevel=1'
    }
    foreach ($name in $environment.Keys) {
        $environmentBackup[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
        [Environment]::SetEnvironmentVariable($name, $environment[$name], 'Process')
    }
    Stop-OwnedApi
    if (-not $SkipBuild) {
        & mvn -B '-DskipTests' package
        if ($LASTEXITCODE -ne 0) { throw 'Banking API build failed.' }
    }
    $jars = @(Get-ChildItem -LiteralPath (Join-Path $project 'target') -Filter 'banking-platform-*.jar')
    if ($jars.Count -ne 1) { throw 'Expected one packaged banking application JAR.' }
    if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) { throw 'Port 8080 belongs to another process; it was not stopped.' }
    $socketDirectory = Join-Path $stateDirectory 'tcp-selector-only'
    if (Test-Path -LiteralPath $socketDirectory) { throw 'Socket fallback directory must not exist.' }
    $arguments = @('-Xms32m','-Xmx256m','-XX:+UseSerialGC','-XX:ActiveProcessorCount=2','-XX:TieredStopAtLevel=1',
        ('"-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"'), '-jar', ('"' + $jars[0].FullName + '"'))
    $process = Start-Process -FilePath $java -ArgumentList $arguments -WorkingDirectory $project -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $stateDirectory 'banking-api.log') -RedirectStandardError (Join-Path $stateDirectory 'banking-api-error.log')
    @{ processId = $process.Id; startTimeTicks = $process.StartTime.ToUniversalTime().Ticks; jarPath = $jars[0].FullName } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDirectory 'api-process.json') -Encoding UTF8
    Wait-Healthy 'http://127.0.0.1:8080/actuator/health' 180
    if (-not $SkipDemos) {
        $bankingResult = & (Join-Path $PSScriptRoot 'demo.ps1') -BaseUrl 'http://127.0.0.1:8080' -Password $config.LOCAL_DEMO_PASSWORD
        $bankingResult | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDirectory 'banking-result.json') -Encoding UTF8
        $liveResult = & (Join-Path $PSScriptRoot 'demo-fineract.ps1') -BaseUrl 'http://127.0.0.1:8080' -Password $config.LOCAL_DEMO_PASSWORD -UseWorker:($config.FINERACT_WORKER_ENABLED -eq 'true')
        $remoteClient = Invoke-Fineract 'Get' "/clients/external-id/rova-customer-$($liveResult.LocalCustomerId)" $null $integrationHeaders
        $remoteAccount = Invoke-Fineract 'Get' "/savingsaccounts/external-id/rova-account-$($liveResult.LocalAccountId)" $null $integrationHeaders
        if ([long]$remoteClient.id -ne [long]$liveResult.FineractClientId -or [long]$remoteAccount.id -ne [long]$liveResult.FineractSavingsApplicationId -or $remoteAccount.currency.code -ne 'NGN') {
            throw 'Live provider verification did not match the local mappings.'
        }
        $liveResult | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $stateDirectory 'live-result.json') -Encoding UTF8
        $liveResult
    }
    [pscustomobject]@{
        Result = 'READY'; BankingApi = 'http://127.0.0.1:8080'; Swagger = 'http://127.0.0.1:8080/swagger-ui/index.html'
        FineractApi = $config.FINERACT_URL; Tenant = $config.FINERACT_TENANT; OfficeId = $config.FINERACT_OFFICE_ID
        NgnSavingsProductId = $config.FINERACT_NGN_PRODUCT_ID; WorkerEnabled = $config.FINERACT_WORKER_ENABLED
        CredentialsFile = $envFile; ReportsDirectory = $stateDirectory
    }
} finally {
    foreach ($name in $environmentBackup.Keys) { [Environment]::SetEnvironmentVariable($name, $environmentBackup[$name], 'Process') }
    Pop-Location
}
