Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

& (Join-Path $ScriptDir "stop-windows.ps1")
Start-Sleep -Seconds 1
& (Join-Path $ScriptDir "start-windows.ps1")
