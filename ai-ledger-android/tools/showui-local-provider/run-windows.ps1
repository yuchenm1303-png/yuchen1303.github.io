Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir

Write-Host "run-windows.ps1 is kept as a compatibility entry."
Write-Host "Starting through start-windows.ps1 so no manual conda activate, python, or pip PATH setup is required."
& (Join-Path $ScriptDir "start-windows.ps1")
