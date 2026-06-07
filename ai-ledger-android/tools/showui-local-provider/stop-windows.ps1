Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$Port = 9100

function Get-ListeningPortOwners {
  $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue |
    Where-Object {
      $_.LocalAddress -eq "127.0.0.1" -or
      $_.LocalAddress -eq "0.0.0.0" -or
      $_.LocalAddress -eq "::" -or
      $_.LocalAddress -eq "::1"
    }
  return $connections | Select-Object -ExpandProperty OwningProcess -Unique
}

Write-Host "[1/2] Check port 9100"
$pids = @(Get-ListeningPortOwners)
if (-not $pids -or $pids.Count -eq 0) {
  Write-Host "Port 9100 is not in use."
  exit 0
}

Write-Host "[2/2] Stop old ShowUI Provider process"
foreach ($pidValue in $pids) {
  $process = Get-Process -Id $pidValue -ErrorAction SilentlyContinue
  $name = if ($process) { $process.ProcessName } else { "unknown" }
  Write-Host "Found process listening on 9100: PID=$pidValue Name=$name"
  taskkill /PID $pidValue /F | Out-Host
}
