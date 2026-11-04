param(
    [string]$Username = 'demo',
    [string]$Password = $env:LOCAL_DEMO_PASSWORD,
    [string]$BaseUrl = 'http://localhost:8080'
)
$ErrorActionPreference = 'Stop'
$base = $BaseUrl.TrimEnd('/') + '/api/v1'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Pass -Password or set LOCAL_DEMO_PASSWORD.' }
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:$Password"))
$headers = @{ Authorization = "Basic $auth" }
function Send-Post($path, $body, $key) {
    $requestHeaders = $headers.Clone()
    if ($key) { $requestHeaders['Idempotency-Key'] = $key }
    $params = @{ Uri = "$base$path"; Method = 'Post'; Headers = $requestHeaders; ContentType = 'application/json'; TimeoutSec = 30 }
    if ($null -ne $body) { $params.Body = ConvertTo-Json $body }
    Invoke-RestMethod @params
}
function Get-Resource($path) {
    Invoke-RestMethod -Uri "$base$path" -Headers $headers -TimeoutSec 30
}
function Assert-Equal($actual, $expected, [string]$description) {
    if ($actual -ne $expected) { throw "${description}: expected '$expected', got '$actual'." }
}
function Assert-Balanced($posting) {
    $entries = Get-Resource "/postings/$($posting.id)/ledger"
    Assert-Equal $entries.Count 2 'Journal entry count'
    $debits = @($entries | Where-Object side -eq 'DEBIT')
    $credits = @($entries | Where-Object side -eq 'CREDIT')
    Assert-Equal $debits.Count 1 'Debit entry count'
    Assert-Equal $credits.Count 1 'Credit entry count'
    foreach ($entry in $entries) {
        Assert-Equal ([decimal]$entry.amount) ([decimal]$posting.amount) 'Journal amount'
        Assert-Equal $entry.currency $posting.currency 'Journal currency'
    }
}
$run = [guid]::NewGuid().ToString('N')
$customer = Send-Post '/customers' @{ fullName = 'Ada Example'; email = "ada-$run@example.com" }
$verified = Send-Post "/customers/$($customer.id)/verify" $null
Assert-Equal $verified.kycStatus 'VERIFIED' 'Customer KYC status'
$account = Send-Post '/accounts' @{ customerId = $customer.id; currency = 'NGN' }
$recipient = Send-Post '/accounts' @{ customerId = $customer.id; currency = 'NGN' }
$deposit = Send-Post "/accounts/$($account.id)/deposits" @{ amount = 1000 } "deposit-$run"
$depositRetry = Send-Post "/accounts/$($account.id)/deposits" @{ amount = 1000 } "deposit-$run"
Assert-Equal $depositRetry.id $deposit.id 'Deposit replay'
Assert-Equal ([decimal](Get-Resource "/accounts/$($account.id)").balance) 1000 'Balance after deposit replay'
Assert-Balanced $deposit
$transferBody = @{ sourceId = $account.id; destinationId = $recipient.id; amount = 250 }
$transfer = Send-Post '/transfers' $transferBody "transfer-$run"
$transferRetry = Send-Post '/transfers' $transferBody "transfer-$run"
Assert-Equal $transferRetry.id $transfer.id 'Transfer replay'
Assert-Balanced $transfer
Assert-Equal ([decimal](Get-Resource "/accounts/$($account.id)").balance) 750 'Sender balance after transfer replay'
Assert-Equal ([decimal](Get-Resource "/accounts/$($recipient.id)").balance) 250 'Recipient balance after transfer replay'
$loan = Send-Post '/loans' @{ accountId = $account.id; principal = 200 }
$disbursed = Send-Post "/loans/$($loan.id)/disburse" $null
$null = Send-Post "/loans/$($loan.id)/disburse" $null
Assert-Equal ([decimal]$disbursed.outstanding) 200 'Disbursed principal'
Assert-Equal ([decimal](Get-Resource "/accounts/$($account.id)").balance) 950 'Balance after disbursement replay'
$repayment = Send-Post "/loans/$($loan.id)/repayments" @{ amount = 200 } "repay-$run"
$repaymentRetry = Send-Post "/loans/$($loan.id)/repayments" @{ amount = 200 } "repay-$run"
Assert-Equal $repaymentRetry.id $repayment.id 'Repayment replay'
Assert-Balanced $repayment
$closedLoan = Get-Resource "/loans/$($loan.id)"
$finalAccount = Get-Resource "/accounts/$($account.id)"
$finalRecipient = Get-Resource "/accounts/$($recipient.id)"
Assert-Equal $closedLoan.status 'CLOSED' 'Repaid loan status'
Assert-Equal ([decimal]$closedLoan.outstanding) 0 'Outstanding loan balance'
Assert-Equal ([decimal]$finalAccount.balance) 750 'Final sender balance'
Assert-Equal ([decimal]$finalRecipient.balance) 250 'Final recipient balance'
[pscustomobject]@{
    Result = 'PASS'
    CustomerId = $customer.id
    AccountId = $account.id
    RecipientAccountId = $recipient.id
    SenderBalance = $finalAccount.balance
    RecipientBalance = $finalRecipient.balance
    Currency = 'NGN'
    LoanStatus = $closedLoan.status
    Outstanding = $closedLoan.outstanding
    BalancedJournalsChecked = 3
    ReplayChecks = 'deposit, transfer, disbursement, repayment'
}
