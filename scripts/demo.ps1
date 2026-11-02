param([string]$Username = "demo", [string]$Password = $env:LOCAL_DEMO_PASSWORD)
$ErrorActionPreference = 'Stop'
$base = 'http://localhost:8080/api/v1'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Pass -Password or set LOCAL_DEMO_PASSWORD.' }
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:$Password"))
$headers = @{ Authorization = "Basic $auth" }
function Send-Post($path, $body, $key) {
    $requestHeaders = $headers.Clone()
    if ($key) { $requestHeaders['Idempotency-Key'] = $key }
    $params = @{ Uri = "$base$path"; Method = 'Post'; Headers = $requestHeaders; ContentType = 'application/json' }
    if ($null -ne $body) { $params.Body = ConvertTo-Json $body }
    Invoke-RestMethod @params
}
$run = [guid]::NewGuid().ToString()
$customer = Send-Post '/customers' @{ fullName = 'Ada Example'; email = "ada-$run@example.com" }
$null = Send-Post "/customers/$($customer.id)/verify" $null
$account = Send-Post '/accounts' @{ customerId = $customer.id; currency = 'NGN' }
$posting = Send-Post "/accounts/$($account.id)/deposits" @{ amount = 1000 } "deposit-$run"
Invoke-RestMethod -Uri "$base/accounts/$($account.id)" -Headers $headers
Invoke-RestMethod -Uri "$base/postings/$($posting.id)/ledger" -Headers $headers
$loan = Send-Post '/loans' @{ accountId = $account.id; principal = 200 }
$null = Send-Post "/loans/$($loan.id)/disburse" $null
Send-Post "/loans/$($loan.id)/repayments" @{ amount = 200 } "repay-$run"
