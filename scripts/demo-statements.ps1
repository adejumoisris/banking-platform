param(
    [string]$BaseUrl = 'http://127.0.0.1:8080',
    [string]$Username = 'demo',
    [string]$Password = $env:LOCAL_DEMO_PASSWORD,
    [string]$OutputPath = '.sandbox/fineract/account-statement.csv'
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Password)) { throw 'Set LOCAL_DEMO_PASSWORD or pass -Password.' }
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
if (-not [IO.Path]::IsPathRooted($OutputPath)) { $OutputPath = Join-Path $project $OutputPath }
$OutputPath = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory((Split-Path $OutputPath)) | Out-Null
$base = $BaseUrl.TrimEnd('/') + '/api/v1'
$auth = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${Username}:$Password"))
$headers = @{ Authorization = "Basic $auth" }
function Get-Resource($path) { Invoke-RestMethod -Uri "$base$path" -Headers $headers -TimeoutSec 30 }
function Assert-Equal($actual, $expected, [string]$message) {
    if ($actual -ne $expected) { throw "${message}: expected $expected, got $actual." }
}
$from = [DateTime]::UtcNow.AddSeconds(-1).ToString('o')
$demo = & (Join-Path $PSScriptRoot 'demo.ps1') -BaseUrl $BaseUrl -Username $Username -Password $Password
$to = [DateTime]::UtcNow.AddSeconds(1).ToString('o')
$query = 'from=' + [uri]::EscapeDataString($from) + '&to=' + [uri]::EscapeDataString($to)
$statement = Get-Resource "/accounts/$($demo.AccountId)/statement?${query}&page=0&size=2"
$second = Get-Resource "/accounts/$($demo.AccountId)/statement?${query}&page=1&size=2"
$history = Get-Resource "/accounts/$($demo.AccountId)/transactions?${query}&size=2"
Assert-Equal $statement.totalElements 4 'Statement transaction count'
Assert-Equal $statement.totalPages 2 'Statement page count'
Assert-Equal $statement.transactions.Count 2 'First statement page size'
Assert-Equal $second.transactions.Count 2 'Second statement page size'
Assert-Equal ([decimal]$statement.openingBalance) 0 'Opening balance'
Assert-Equal ([decimal]$statement.totalMoneyIn) 1200 'Period money in'
Assert-Equal ([decimal]$statement.totalMoneyOut) 450 'Period money out'
Assert-Equal ([decimal]$statement.closingBalance) 750 'Closing balance'
Assert-Equal ([decimal]$second.closingBalance) 750 'Full-period balance on second page'
Assert-Equal $history.totalElements 4 'History count'
Assert-Equal $history.transactions[0].type 'LOAN_REPAYMENT' 'Newest transaction'
$recipient = Get-Resource "/accounts/$($demo.RecipientAccountId)/statement?$query"
Assert-Equal $recipient.totalElements 1 'Recipient transaction count'
Assert-Equal ([decimal]$recipient.closingBalance) 250 'Recipient closing balance'
$response = Invoke-WebRequest -Uri "$base/accounts/$($demo.AccountId)/statement.csv?$query" -Headers $headers -OutFile $OutputPath -PassThru -TimeoutSec 30
if ([string]$response.Headers['Content-Type'] -notlike 'text/csv*') { throw 'CSV response has the wrong media type.' }
if ([string]$response.Headers['Content-Disposition'] -notlike 'attachment*') { throw 'CSV was not returned as a download.' }
$rows = @(Get-Content -LiteralPath $OutputPath -Raw | ConvertFrom-Csv)
$transactions = @($rows | Where-Object record_type -eq 'TRANSACTION')
Assert-Equal $rows.Count 6 'CSV summary and transaction row count'
Assert-Equal $transactions.Count 4 'CSV transaction count'
Assert-Equal ([decimal]$rows[0].balance) 0 'CSV opening balance'
Assert-Equal ([decimal]$rows[-1].balance) 750 'CSV closing balance'
Assert-Equal ([decimal]$rows[-1].money_in) 1200 'CSV period money in'
Assert-Equal ([decimal]$rows[-1].money_out) 450 'CSV period money out'
[pscustomobject]@{
    Result = 'PASS'; AccountId = $demo.AccountId; Currency = 'NGN'; Transactions = 4
    OpeningBalance = $statement.openingBalance; TotalMoneyIn = $statement.totalMoneyIn
    TotalMoneyOut = $statement.totalMoneyOut; ClosingBalance = $statement.closingBalance
    RecipientClosingBalance = $recipient.closingBalance; FromInclusive = $from; ToExclusive = $to; CsvPath = $OutputPath
}
