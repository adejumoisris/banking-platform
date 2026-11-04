$ErrorActionPreference = 'Stop'
$project = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$pidFile = Join-Path $project '.sandbox/fineract/api-process.json'
if (Test-Path -LiteralPath $pidFile) {
    $metadata = Get-Content -LiteralPath $pidFile -Raw | ConvertFrom-Json
    $process = Get-Process -Id $metadata.processId -ErrorAction SilentlyContinue
    if ($process -and $process.StartTime.ToUniversalTime().Ticks -eq [long]$metadata.startTimeTicks) {
        $command = Get-CimInstance Win32_Process -Filter "ProcessId=$($process.Id)"
        if ($command.CommandLine -notlike "*$($metadata.jarPath)*") { throw 'Refusing to stop an unrecognized process.' }
        Stop-Process -Id $process.Id -Force
        $process.WaitForExit(10000) | Out-Null
    }
    Remove-Item -LiteralPath $pidFile
}
Push-Location -LiteralPath $project
try {
    & docker compose --env-file .env.fineract-sandbox -f compose.fineract-sandbox.yaml stop
    if ($LASTEXITCODE -ne 0) { throw 'Sandbox stop failed.' }
} finally { Pop-Location }
Write-Host 'Sandbox stopped. Database volumes and local credentials were preserved.'
