$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
if (-not $nodeCommand) {
    throw "node.exe was not found. Install Node.js or add node.exe to PATH."
}
$node = $nodeCommand.Source
$serverDir = Join-Path $PSScriptRoot "server"
$server = Join-Path $serverDir "server.js"

if (-not (Test-Path $node)) {
    $node = "node"
}

$existing = Get-NetTCPConnection -LocalPort 8787 -State Listen -ErrorAction SilentlyContinue
if ($existing) {
    Write-Host "LiveCircle relay already listening on port 8787."
} else {
    Start-Process -FilePath $node -ArgumentList @($server) -WorkingDirectory $serverDir -WindowStyle Hidden
    Start-Sleep -Seconds 1
    Write-Host "LiveCircle relay started on port 8787."
}

try {
    Invoke-RestMethod "http://127.0.0.1:8787/api/health" | ConvertTo-Json -Compress
} catch {
    Write-Warning $_.Exception.Message
}

Write-Host ""
Write-Host "For USB testing on an attached Android phone:"
Write-Host "  & 'C:\Program Files\platform-tools\adb.exe' reverse tcp:8787 tcp:8787"
Write-Host ""
Write-Host "For other phones on the same Wi-Fi, use this computer's LAN IP with port 8787."
