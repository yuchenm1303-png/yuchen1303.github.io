Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$EnvName = "showui"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $ScriptDir
$Port = 9100

function Find-Conda {
  $cmd = Get-Command conda -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }

  $candidates = @(
    "C:\Users\$env:USERNAME\miniconda3\Scripts\conda.exe",
    "C:\Users\$env:USERNAME\anaconda3\Scripts\conda.exe",
    "C:\ProgramData\miniconda3\Scripts\conda.exe",
    "C:\ProgramData\Anaconda3\Scripts\conda.exe"
  )
  foreach ($path in $candidates) {
    if (Test-Path -LiteralPath $path) { return $path }
  }
  return $null
}

function Invoke-Conda {
  param([string[]]$Arguments)
  & $script:CondaExe @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "conda command failed: $($Arguments -join ' ')"
  }
}

function Test-ShowuiEnv {
  $envList = & $script:CondaExe env list
  if ($LASTEXITCODE -ne 0) { throw "Failed to read conda env list." }
  return [bool]($envList | Select-String -Pattern "^\s*$EnvName\s")
}

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

Write-Host "Current directory: $ScriptDir"

$script:CondaExe = Find-Conda
if (-not $script:CondaExe) {
  Write-Host "Conda was not found. Install Miniconda, then run .\bootstrap-windows.ps1."
  exit 1
}
Write-Host "Conda path: $script:CondaExe"

if (-not (Test-ShowuiEnv)) {
  Write-Host "Conda env showui was not found. Run first:"
  Write-Host "  .\bootstrap-windows.ps1"
  exit 1
}
Write-Host "Conda env showui exists: True"

$portOwners = @(Get-ListeningPortOwners)
if ($portOwners -and $portOwners.Count -gt 0) {
  Write-Host "Port 9100 is already in use. Cleaning old ShowUI provider process..."
  & (Join-Path $ScriptDir "stop-windows.ps1")
  Start-Sleep -Seconds 1
}

Write-Host "Checking Python..."
$pythonVersion = & $script:CondaExe run -n $EnvName python --version
if ($LASTEXITCODE -ne 0) {
  throw "Python check failed in showui env."
}
Write-Host "Python: $pythonVersion"

Write-Host "Checking PyTorch..."
$torchVersion = & $script:CondaExe run -n $EnvName python -c "import torch; print(torch.__version__)" 2>$null
if ($LASTEXITCODE -ne 0) {
  Write-Host "Torch is not installed in the showui env. Run first:"
  Write-Host "  .\bootstrap-windows.ps1"
  exit 1
}
Write-Host "PyTorch: $torchVersion"

Write-Host "Checking CUDA..."
$cudaInfo = & $script:CondaExe run -n $EnvName python -c "import torch; print(torch.cuda.is_available()); print(torch.cuda.get_device_name(0) if torch.cuda.is_available() else 'CPU')"
if ($LASTEXITCODE -ne 0) {
  throw "CUDA check command failed."
}
$cudaInfo | ForEach-Object { Write-Host $_ }
if ($cudaInfo -match "False") {
  Write-Host ""
  Write-Host "CUDA=False. The model will not be started."
  Write-Host "- PyTorch may have been installed as the CPU-only build."
  Write-Host "- The NVIDIA driver may be too old or may not match the selected CUDA wheel."
  Write-Host "- Upgrade the NVIDIA driver, or use the current Windows CUDA wheel from the PyTorch selector."
  exit 1
}

$env:SHOWUI_HOST = if ($env:SHOWUI_HOST) { $env:SHOWUI_HOST } else { "127.0.0.1" }
$env:SHOWUI_PORT = if ($env:SHOWUI_PORT) { $env:SHOWUI_PORT } else { "9100" }
$env:SHOWUI_MAX_PIXELS = if ($env:SHOWUI_MAX_PIXELS) { $env:SHOWUI_MAX_PIXELS } else { "301056" }
$env:SHOWUI_MAX_NEW_TOKENS = if ($env:SHOWUI_MAX_NEW_TOKENS) { $env:SHOWUI_MAX_NEW_TOKENS } else { "32" }

Write-Host ""
Write-Host "SHOWUI_MAX_PIXELS=$($env:SHOWUI_MAX_PIXELS)"
Write-Host "SHOWUI_MAX_NEW_TOKENS=$($env:SHOWUI_MAX_NEW_TOKENS)"
Write-Host "Service URL: http://$($env:SHOWUI_HOST):$($env:SHOWUI_PORT)"
Write-Host "Health URL:  http://$($env:SHOWUI_HOST):$($env:SHOWUI_PORT)/health"
Write-Host "After startup finishes, open another PowerShell and run nvidia-smi."
Write-Host "Normally python.exe should be visible with GPU memory usage after the model is loaded."
Write-Host ""

try {
  Invoke-Conda @("run", "-n", $EnvName, "python", "server.py")
} finally {
  Write-Host "Service exited. Please check the error logs above."
}
