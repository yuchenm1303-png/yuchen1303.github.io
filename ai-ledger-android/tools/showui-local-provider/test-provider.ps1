param(
  [Parameter(Mandatory = $true)]
  [string]$ImagePath,

  [string]$Goal = "Click the Contacts tab.",

  [string]$Url = "http://127.0.0.1:9100/",

  [string]$ApiKey = $env:SHOWUI_PROVIDER_API_KEY
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $ImagePath)) {
  throw "ImagePath not found: $ImagePath"
}

$healthUrl = $Url.TrimEnd("/") + "/health"
Write-Host "GET $healthUrl"
Invoke-RestMethod -Uri $healthUrl -Method Get | ConvertTo-Json -Depth 8

$bytes = [System.IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $ImagePath))
$base64 = [Convert]::ToBase64String($bytes)
$extension = [System.IO.Path]::GetExtension($ImagePath).ToLowerInvariant()
$mimeType = if ($extension -eq ".png") { "image/png" } else { "image/jpeg" }

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
Write-Host "POST $Url"
Invoke-RestMethod -Uri $Url -Method Post -Headers $headers -ContentType "application/json" -Body $body | ConvertTo-Json -Depth 12
