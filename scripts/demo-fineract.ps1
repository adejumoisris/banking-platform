param(
    [string]$BaseUrl = 'http://localhost:8080',
    [string]$Username = 'demo',
    [string]$Password = $env:LOCAL_DEMO_PASSWORD
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Set LOCAL_DEMO_PASSWORD or pass -Password for the local banking API.' }
$base = $BaseUrl.TrimEnd('/') + '/api/v1'
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:$Password"))
$headers = @{ Authorization = "Basic $auth" }
function Send-Post($path, $body) {
    $parameters = @{ Uri = "$base$path"; Headers = $headers; Method = 'Post'; ContentType = 'application/json'; TimeoutSec = 30 }
    if ($null -ne $body) { $parameters.Body = ConvertTo-Json $body }
    Invoke-RestMethod @parameters
}
function Complete-Command($command) {
    $deadline = [DateTime]::UtcNow.AddSeconds(180)
    while ([DateTime]::UtcNow -lt $deadline) {
        $command = Send-Post "/integrations/fineract/commands/$($command.id)/dispatch" $null
        if ($command.status -eq 'SUCCEEDED') {
            if ([long]$command.remoteId -lt 1) { throw 'Successful command has no valid remote mapping.' }
            return $command
        }
        if ($command.status -in @('FAILED','REVIEW')) { throw "Fineract command $($command.id) needs attention: $($command.errorCode)." }
        Start-Sleep -Seconds 2
    }
    throw "Fineract command $($command.id) did not finish within 180 seconds. Inspect its status before retrying."
}
$run = [guid]::NewGuid().ToString('N')
$customer = Send-Post '/customers' @{ fullName = 'Fineract Sandbox Test'; email = "fineract-$run@example.com" }
$null = Send-Post "/customers/$($customer.id)/verify" $null
$account = Send-Post '/accounts' @{ customerId = $customer.id; currency = 'NGN' }
$clientCommand = Send-Post "/integrations/fineract/customers/$($customer.id)" $null
$clientResult = Complete-Command $clientCommand
$clientReplay = Send-Post "/integrations/fineract/customers/$($customer.id)" $null
if ($clientReplay.id -ne $clientResult.id -or $clientReplay.remoteId -ne $clientResult.remoteId) { throw 'Client provisioning replay changed the mapping.' }
$accountCommand = Send-Post "/integrations/fineract/accounts/$($account.id)" $null
$accountResult = Complete-Command $accountCommand
$accountReplay = Send-Post "/integrations/fineract/accounts/$($account.id)" $null
if ($accountReplay.id -ne $accountResult.id -or $accountReplay.remoteId -ne $accountResult.remoteId) { throw 'Account provisioning replay changed the mapping.' }
$finalAccount = Invoke-RestMethod -Uri "$base/accounts/$($account.id)" -Headers $headers -TimeoutSec 30
if ([decimal]$finalAccount.balance -ne 0) { throw 'Provisioning unexpectedly changed the local balance.' }
[pscustomobject]@{
    Result = 'PASS'
    LocalCustomerId = $customer.id
    FineractClientId = $clientResult.remoteId
    LocalAccountId = $account.id
    FineractSavingsApplicationId = $accountResult.remoteId
    LocalBalance = $finalAccount.balance
    MappingReplayChecks = 'customer, account'
}
