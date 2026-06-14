param(
    [Parameter(Mandatory = $true)]
    [string]$HostName,

    [string]$User = "root",

    [string]$KeyPath = "",

    [int]$Port = 8787
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$bundle = Join-Path $root "aliyun\livecircle-aliyun-bundle.zip"
$remote = "/tmp/livecircle-aliyun-bundle.zip"

Remove-Item $bundle -Force -ErrorAction SilentlyContinue
Compress-Archive -Path (Join-Path $root "server"), (Join-Path $root "aliyun\install-livecircle.sh") -DestinationPath $bundle -Force

$target = "$User@$HostName"
$sshArgs = @()
if ($KeyPath) {
    $sshArgs += @("-i", $KeyPath)
}

scp @sshArgs $bundle "${target}:$remote"
ssh @sshArgs $target "rm -rf /tmp/livecircle && mkdir -p /tmp/livecircle && unzip -o $remote -d /tmp/livecircle && cd /tmp/livecircle && chmod +x install-livecircle.sh && PORT=$Port ./install-livecircle.sh"

Write-Host "Done."
Write-Host "Server URL: http://${HostName}:$Port"
