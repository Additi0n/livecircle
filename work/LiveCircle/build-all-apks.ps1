$ErrorActionPreference = "Stop"
& (Join-Path $PSScriptRoot "build-uploader-apk.ps1")
& (Join-Path $PSScriptRoot "build-apk.ps1")
