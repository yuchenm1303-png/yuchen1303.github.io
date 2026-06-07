param(
  [Parameter(Mandatory = $true)]
  [string]$ImagePath,

  [string]$Goal = "Click the Contacts tab.",

  [string]$Url = "http://127.0.0.1:9100/",

  [int]$TimeoutSec = 240,

  [string]$ApiKey = $env:SHOWUI_PROVIDER_API_KEY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ImagePath)) {
  throw "ImagePath not found: $ImagePath"
}

$healthUrl = $Url.TrimEnd("/") + "/health"
Write-Host "[1/3] Health check"
Write-Host "GET $healthUrl"
Invoke-RestMethod -Uri $healthUrl -Method Get -TimeoutSec 30 | ConvertTo-Json -Depth 8

Write-Host ""
Write-Host "[2/3] Prepare image"
$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $ImagePath))
$base64 = [Convert]::ToBase64String($bytes)
$extension = [System.IO.Path]::GetExtension($ImagePath).ToLowerInvariant()
$mimeType = if ($extension -eq ".png") { "image/png" } else { "image/jpeg" }
Write-Host "Image bytes: $($bytes.Length)"

$body = @{
  goal = $Goal
  imageBase64 = $base64
  mimeType = $mimeType
} | ConvertTo-Json -Depth 8

$headers = @{}
if ($ApiKey) {
  $headers["Authorization"] = "Bearer $ApiKey"
}

Write-Host ""
Write-Host "[3/3] POST provider"
Write-Host "POST $Url"
try {
  Invoke-RestMethod -Uri $Url -Method Post -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec $TimeoutSec | ConvertTo-Json -Depth 12
} catch {
  if ($_.Exception.Message -match "timed out|timeout|operation has timed") {
    Write-Host "Request timeout. The model may be loading for the first time, or VRAM/image size may be too large. Lower SHOWUI_MAX_PIXELS and restart the service."
  }
  throw
}
